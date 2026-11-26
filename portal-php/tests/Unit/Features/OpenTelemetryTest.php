<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\Features\HttpCache\ServiceProvider as HttpCacheService;
use Gentics\PortalPhp\Features\Keycloak\Adapter\UserAuthenticator;
use Gentics\PortalPhp\Features\Keycloak\Contract\RefreshTokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;
use Gentics\PortalPhp\Features\Keycloak\ServiceProvider as AuthenticationService;
use Gentics\PortalPhp\Features\Keycloak\Socialite\Provider as KeycloakProvider;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadUser;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Contracts\Http\Kernel as HttpKernel;
use Illuminate\Filesystem\Filesystem;
use Illuminate\Foundation\AliasLoader;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Log;
use Laravel\Socialite\Facades\Socialite;
use Lcobucci\JWT\Configuration;
use Mockery;
use Monolog\Handler\TestHandler;
use Monolog\Logger;
use OpenTelemetry\API\Globals;
use OpenTelemetry\API\Instrumentation\Configurator;
use OpenTelemetry\API\Trace\Span;
use OpenTelemetry\API\Trace\SpanContext;
use OpenTelemetry\API\Trace\SpanContextInterface;
use OpenTelemetry\API\Trace\SpanInterface;
use OpenTelemetry\API\Trace\SpanKind;
use OpenTelemetry\API\Trace\StatusCode;
use OpenTelemetry\API\Trace\TraceFlags;
use OpenTelemetry\Context\Context;
use OpenTelemetry\Context\ScopeInterface;
use OpenTelemetry\SDK\Logs\Exporter\InMemoryExporter as InMemoryLogExporter;
use OpenTelemetry\SDK\Logs\LoggerProvider;
use OpenTelemetry\SDK\Logs\Processor\SimpleLogRecordProcessor;
use OpenTelemetry\SDK\Logs\ReadableLogRecord;
use OpenTelemetry\SDK\Trace\SpanDataInterface;
use OpenTelemetry\SDK\Trace\SpanExporter\InMemoryExporter;
use OpenTelemetry\SDK\Trace\SpanProcessor\SimpleSpanProcessor;
use OpenTelemetry\SDK\Trace\TracerProvider;
use PHPUnit\Framework\Attributes\RequiresPhpExtension;
use Symfony\Component\HttpFoundation\Request as SymfonyRequest;
use Symfony\Component\HttpFoundation\Response as SymfonyResponse;
use Symfony\Component\HttpKernel\HttpCache\Store;
use Symfony\Component\HttpKernel\HttpKernelInterface;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * Tests for the spans of the OpenTelemetry instrumentation
 */
#[RequiresPhpExtension('opentelemetry')]
class OpenTelemetryTest extends TestCase
{
    /**
     * @var \Gentics\PortalPhp\PortalPhp|null Instance
     */
    protected $portalPhp = null;

    /**
     * @var InMemoryExporter
     */
    protected $exporter;

    /**
     * @var TracerProvider
     */
    protected $tracerProvider;

    /**
     * @var InMemoryLogExporter
     */
    protected $logExporter;

    /**
     * @var ScopeInterface
     */
    protected $scope;

    protected function setUp(): void
    {
        parent::setUp();

        $this->exporter = new InMemoryExporter();
        $this->tracerProvider = new TracerProvider(new SimpleSpanProcessor($this->exporter));
        $this->logExporter = new InMemoryLogExporter();
        $loggerProvider = LoggerProvider::builder()
            ->addLogRecordProcessor(new SimpleLogRecordProcessor($this->logExporter))
            ->build();
        $this->scope = Configurator::create()
            ->withTracerProvider($this->tracerProvider)
            ->withLoggerProvider($loggerProvider)
            ->activate();

        config(["portal.projectName" => 'test']);
        $this->portalPhp = $this->createPortalPhpWithMockedMeshClient();
        $this->withoutExceptionHandling();
    }

    protected function tearDown(): void
    {
        $this->scope->detach();

        parent::tearDown();
    }

    /**
     * A page request creates spans for the controller, the Mesh request and the rendering
     */
    public function testPageRequest()
    {
        $this->mockResponse("graphQL", "/testLoadByPath.json");
        $response = $this->call('GET', '/mysuperawesomepage.html', [], [], [], $this->getBrowserRequestHeaders());
        $response->assertOk();

        $controller = $this->span('ContentController@index');
        $this->assertSame(
            'Gentics\PortalPhp\Http\Controllers\ContentController::index',
            $controller->getAttributes()->get('code.function.name')
        );
        $this->assertSame('{path}', $controller->getAttributes()->get('http.route'));

        // LoadContent::fetch() calls parent::fetch(), which must not create a second span
        $mesh = $this->span('mesh loadContent');
        $this->assertSame($controller->getSpanId(), $mesh->getParentSpanId());
        $this->assertSame(
            'Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadContent',
            $mesh->getAttributes()->get('portal.mesh.request.class')
        );
        $this->assertSame('loadContent', $mesh->getAttributes()->get('portal.mesh.template'));
        $this->assertSame('mysuperawesomepage.html', $mesh->getAttributes()->get('portal.mesh.path'));
        $this->assertFalse($mesh->getAttributes()->get('portal.mesh.etag_cache.hit'));

        // The view is rendered after the controller returned
        $render = $this->span('render contentpage');
        $this->assertNotSame($controller->getSpanId(), $render->getParentSpanId());
        $this->assertSame('contentpage', $render->getAttributes()->get('portal.view.name'));
        $this->assertStringEndsWith(
            'tests/resources/views/contentpage.blade.php',
            $render->getAttributes()->get('code.file.path')
        );
    }

    /**
     * An included view is rendered as child span of the including view
     */
    public function testNestedViewRender()
    {
        $html = view('opentelemetry.outer')->render();
        $this->assertSame("outer\ninner\n", $html);

        $outer = $this->span('render opentelemetry.outer');
        $inner = $this->span('render opentelemetry.inner');
        $this->assertSame($outer->getSpanId(), $inner->getParentSpanId());
    }

    /**
     * fetchCached() marks cache misses and hits, a miss contains the span of the Mesh request
     */
    public function testFetchCached()
    {
        $this->mockResponse("graphQL", "/testLoadUser.json");

        Mesh::request(LoadUser::class)->fetchCached();
        Mesh::request(LoadUser::class)->fetchCached();

        $cacheSpans = $this->spans('mesh cache loadUser');
        $this->assertCount(2, $cacheSpans);
        [$miss, $hit] = $cacheSpans;

        $this->assertFalse($miss->getAttributes()->get('portal.mesh.cache.hit'));
        $this->assertTrue($hit->getAttributes()->get('portal.mesh.cache.hit'));

        $meshSpans = $this->spans('mesh loadUser');
        $this->assertCount(1, $meshSpans);
        $this->assertSame($miss->getSpanId(), $meshSpans[0]->getParentSpanId());
    }

    /**
     * An exception is recorded on the span and the span gets the status error
     */
    public function testMeshErrorRecorded()
    {
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);

        try {
            $this->call('GET', '/nonexisting.html', [], [], [], $this->getBrowserRequestHeaders());
            $this->fail('NotFoundHttpException expected');
        } catch (NotFoundHttpException $exception) {
        }

        $mesh = $this->span('mesh loadContent');
        $this->assertSame(StatusCode::STATUS_ERROR, $mesh->getStatus()->getCode());
        $this->assertSame('exception', $mesh->getEvents()[0]->getName());
        $this->assertSame(
            NotFoundHttpException::class,
            $mesh->getEvents()[0]->getAttributes()->get('exception.type')
        );
    }

    /**
     * The HTTP cache span contains the cache status of the request
     */
    public function testHttpCacheStatus()
    {
        $cacheDir = sys_get_temp_dir() . '/portal-php-otel-' . uniqid();
        $kernel = new class implements HttpKernelInterface {
            public function handle(
                SymfonyRequest $request,
                int $type = self::MAIN_REQUEST,
                bool $catch = true
            ): SymfonyResponse {
                return new SymfonyResponse('content', 200, ['Cache-Control' => 'public, max-age=60']);
            }
        };

        try {
            $appCache = new AppCache($kernel, new Store($cacheDir));
            $appCache->handle(SymfonyRequest::create('http://localhost/cached'));
            $appCache->handle(SymfonyRequest::create('http://localhost/cached'));
        } finally {
            (new Filesystem())->deleteDirectory($cacheDir);
        }

        $spans = $this->spans('http cache');
        $this->assertCount(2, $spans);
        $this->assertSame('miss, store', $spans[0]->getAttributes()->get('portal.http_cache.status'));
        $this->assertSame('fresh', $spans[1]->getAttributes()->get('portal.http_cache.status'));
    }

    /**
     * A sampled request gets the ID of its trace as response header, an anonymous request gets no user.id
     */
    public function testTraceIdHeader()
    {
        $this->mockResponse("graphQL", "/testLoadByPath.json");

        $response = $this->handle($this->pageRequest('/mysuperawesomepage.html'), $this->startServerSpan());

        $this->assertSame(200, $response->getStatusCode());
        $serverSpan = $this->span('GET');
        $this->assertMatchesRegularExpression('/^[0-9a-f]{32}$/', $response->headers->get('X-Trace-Id'));
        $this->assertSame($serverSpan->getTraceId(), $response->headers->get('X-Trace-Id'));
        $this->assertFalse($serverSpan->getAttributes()->has('user.id'));
    }

    /**
     * A request that is not sampled or has no server span gets no trace ID header
     */
    public function testNoTraceIdHeaderWhenNotSampled()
    {
        $this->mockResponse("graphQL", "/testLoadByPath.json");

        $notSampled = $this->startServerSpan($this->notSampledParent());
        $this->assertTrue($notSampled->getContext()->isValid());
        $response = $this->handle($this->pageRequest('/mysuperawesomepage.html'), $notSampled);
        $this->assertSame(200, $response->getStatusCode());
        $this->assertFalse($response->headers->has('X-Trace-Id'));

        $response = $this->handle($this->pageRequest('/mysuperawesomepage.html'), null);
        $this->assertSame(200, $response->getStatusCode());
        $this->assertFalse($response->headers->has('X-Trace-Id'));
    }

    /**
     * With the HTTP cache, the server span gets its route on a miss and on a hit. The response of a hit carries
     * the trace ID of its own request, not the one of the request that filled the cache.
     */
    public function testHttpCacheRouteAndTraceId()
    {
        $this->mockResponse("graphQL", "/testLoadByPath.json");
        config(['portal.cache.http.enabled' => true]);
        $this->app->register(HttpCacheService::class);
        $cacheDir = sys_get_temp_dir() . '/portal-php-otel-' . uniqid();
        $this->app['http_cache.cache_dir'] = $cacheDir;

        // A browser reload ("Cache-Control: no-cache") would bypass the cache
        $headers = $this->getBrowserRequestHeaders();
        unset($headers['HTTP_CACHE_CONTROL']);

        try {
            $miss = $this->handle($this->pageRequest('/mysuperawesomepage.html', $headers), $this->startServerSpan());
            $hit = $this->handle($this->pageRequest('/mysuperawesomepage.html', $headers), $this->startServerSpan());
        } finally {
            (new Filesystem())->deleteDirectory($cacheDir);
        }

        $this->assertSame(
            ['miss, store', 'fresh'],
            array_map(
                fn (SpanDataInterface $span) => $span->getAttributes()->get('portal.http_cache.status'),
                $this->spans('http cache')
            )
        );

        $serverSpans = $this->spans('GET /{path}');
        $this->assertCount(2, $serverSpans);
        foreach ($serverSpans as $serverSpan) {
            $this->assertSame(SpanKind::KIND_SERVER, $serverSpan->getKind());
            $this->assertSame('{path}', $serverSpan->getAttributes()->get('http.route'));
        }

        $this->assertSame($serverSpans[0]->getTraceId(), $miss->headers->get('X-Trace-Id'));
        $this->assertSame($serverSpans[1]->getTraceId(), $hit->headers->get('X-Trace-Id'));
        $this->assertNotSame($serverSpans[0]->getTraceId(), $serverSpans[1]->getTraceId());
    }

    /**
     * The Keycloak callback span keeps the callback route, gets the ID of the user who logged in,
     * and does not record the OAuth code and session state
     */
    public function testKeycloakCallback()
    {
        config(['portal.startPage' => '/']);
        config(['portal.authentication.keycloak' => [
            'authUrl' => 'http://keycloakserver/auth',
            'realm' => '',
            'client_id' => '',
            'client_secret' => '',
            'redirect' => 'http://portal/auth/callback',
        ]]);
        $this->app->register(AuthenticationService::class);
        // The callback is not cached, but runs through the HTTP cache, which sets the route
        config(['portal.cache.http.enabled' => true]);
        $this->app->register(HttpCacheService::class);
        $cacheDir = sys_get_temp_dir() . '/portal-php-otel-' . uniqid();
        $this->app['http_cache.cache_dir'] = $cacheDir;

        $this->app->instance(TokenStorage::class, $this->createMock(TokenStorage::class));
        $this->app->instance(RefreshTokenStorage::class, $this->createMock(RefreshTokenStorage::class));

        // Only the requests to Keycloak are replaced, Provider::user() runs
        $provider = $this->getMockBuilder(KeycloakProvider::class)
            ->disableOriginalConstructor()
            ->onlyMethods(['getCode', 'getAccessTokenResponse', 'getUserByToken'])
            ->getMock();
        $provider->method('getCode')->willReturn('secret-code');
        $provider->method('getAccessTokenResponse')->willReturn([
            'id_token' => 'id-token',
            'refresh_token' => 'refresh-token',
            'expires_in' => 300,
        ]);
        $provider->method('getUserByToken')->willReturn([
            'sub' => 'user-sub',
            'iss' => 'http://keycloakserver/auth',
            'name' => 'Dummy User',
            'email' => 'dummy@example.com',
        ]);
        AliasLoader::getInstance()->alias('Socialite', Socialite::class);
        $socialite = Mockery::mock();
        $socialite->shouldReceive('with')->with('keycloak')->andReturn($provider);
        Socialite::swap($socialite);

        $request = Request::create(
            'http://localhost/auth/callback?session_state=secret-state&iss=http%3A%2F%2Fkeycloakserver&code=secret-code'
        );
        // Recorded by the Laravel instrumentation when it starts the server span
        $serverSpan = $this->startServerSpan();
        $serverSpan->setAttribute('url.query', $request->getQueryString());
        $serverSpan->setAttribute('url.full', $request->fullUrl());

        try {
            $response = $this->handle($request, $serverSpan);
        } finally {
            (new Filesystem())->deleteDirectory($cacheDir);
        }

        $this->assertSame(302, $response->getStatusCode());
        $span = $this->span('GET /auth/callback');
        $this->assertSame('auth/callback', $span->getAttributes()->get('http.route'));
        $this->assertSame('user-sub', $span->getAttributes()->get('user.id'));
        $this->assertFalse($span->getAttributes()->has('user.name'));
        $this->assertSame(
            'code=REDACTED&iss=http%3A%2F%2Fkeycloakserver&session_state=REDACTED',
            $span->getAttributes()->get('url.query')
        );
        $this->assertSame(
            'http://localhost/auth/callback?code=REDACTED&iss=http%3A%2F%2Fkeycloakserver&session_state=REDACTED',
            $span->getAttributes()->get('url.full')
        );

        foreach ($this->exporter->getSpans() as $exported) {
            foreach ($exported->getAttributes() as $value) {
                $this->assertStringNotContainsString('secret-', json_encode($value));
            }
        }
    }

    /**
     * An authenticated request gets the Keycloak subject of the user as user.id
     */
    public function testUserIdOfAuthenticatedUser()
    {
        $config = Configuration::forUnsecuredSigner();
        $token = $config->builder()
            ->relatedTo('user-sub')
            ->withClaim('preferred_username', 'dummyuser')
            ->getToken($config->signer(), $config->signingKey());

        $serverSpan = $this->startServerSpan();
        $request = Request::create('http://localhost/page.html');
        $request->attributes->set(SpanInterface::class, $serverSpan);
        $this->app->instance('request', $request);

        (new UserAuthenticator())->authUser($token);
        $serverSpan->end();

        $span = $this->span('GET');
        $this->assertSame('user-sub', $span->getAttributes()->get('user.id'));
        $this->assertFalse($span->getAttributes()->has('user.name'));
    }

    /**
     * Log records written by Laravel get the trace and span ID only while a sampled span is current
     */
    public function testTraceIdInLogRecords()
    {
        $logger = Log::build(['driver' => 'monolog', 'handler' => TestHandler::class]);
        /** @var TestHandler $handler */
        $handler = $logger->getLogger()->getHandlers()[0];

        $logger->info('without span');
        $sampled = $this->startServerSpan();
        $this->inSpan($sampled, fn () => $logger->info('sampled'));
        $this->inSpan($this->startServerSpan($this->notSampledParent()), fn () => $logger->info('not sampled'));

        [$withoutSpan, $inSampled, $inNotSampled] = $handler->getRecords();
        $this->assertArrayNotHasKey('trace_id', $withoutSpan->extra);
        $this->assertSame($sampled->getContext()->getTraceId(), $inSampled->extra['trace_id']);
        $this->assertSame($sampled->getContext()->getSpanId(), $inSampled->extra['span_id']);
        $this->assertArrayNotHasKey('trace_id', $inNotSampled->extra);
        $this->assertArrayNotHasKey('span_id', $inNotSampled->extra);
    }

    /**
     * Log records exported with OpenTelemetry carry the trace context only while a sampled span is current
     */
    public function testTraceIdInExportedLogRecords()
    {
        $logger = Globals::loggerProvider()->getLogger('test');

        $sampled = $this->startServerSpan();
        $this->inSpan($sampled, fn () => $logger->logRecordBuilder()->setBody('sampled')->emit());
        $this->inSpan(
            $this->startServerSpan($this->notSampledParent()),
            fn () => $logger->logRecordBuilder()->setBody('not sampled')->emit()
        );

        /** @var ReadableLogRecord[] $records */
        $records = $this->logExporter->getStorage()->getArrayCopy();
        $this->assertCount(2, $records);
        $this->assertSame('sampled', $records[0]->getBody());
        $this->assertSame($sampled->getContext()->getTraceId(), $records[0]->getSpanContext()->getTraceId());
        $this->assertSame('not sampled', $records[1]->getBody());
        $this->assertFalse($records[1]->getSpanContext()->isValid());
    }

    /**
     * Start a span like the server span of the Laravel instrumentation
     * @param SpanContextInterface|null $parent Remote parent, e.g. from an incoming traceparent header
     * @return SpanInterface
     */
    protected function startServerSpan(?SpanContextInterface $parent = null): SpanInterface
    {
        return $this->tracerProvider->getTracer('test')
            ->spanBuilder('GET')
            ->setSpanKind(SpanKind::KIND_SERVER)
            ->setParent($parent ? Span::wrap($parent)->storeInContext(Context::getRoot()) : Context::getRoot())
            ->startSpan();
    }

    /**
     * Get the context of a remote parent, which was not sampled
     * @return SpanContextInterface
     */
    protected function notSampledParent(): SpanContextInterface
    {
        return SpanContext::createFromRemoteParent(
            '0af7651916cd43dd8448eb211c80319c',
            'b7ad6b7169203331',
            TraceFlags::DEFAULT
        );
    }

    /**
     * Call the function while the span is current, then end the span
     * @param SpanInterface $span
     * @param callable $function
     * @return void
     */
    protected function inSpan(SpanInterface $span, callable $function)
    {
        $scope = $span->activate();
        try {
            $function();
        } finally {
            $scope->detach();
            $span->end();
        }
    }

    /**
     * Create a request for a page as sent by a browser
     * @param string $path
     * @param array|null $headers Request headers in $_SERVER format, default: the headers of a browser
     * @return Request
     */
    protected function pageRequest(string $path, ?array $headers = null): Request
    {
        return Request::create(
            'http://localhost:8080' . $path,
            'GET',
            [],
            [],
            [],
            $headers ?? $this->getBrowserRequestHeaders()
        );
    }

    /**
     * Handle the request with the HTTP kernel, with the server span stored in the request and current,
     * as the Laravel instrumentation does
     * @param Request $request
     * @param SpanInterface|null $serverSpan
     * @return SymfonyResponse
     */
    protected function handle(Request $request, ?SpanInterface $serverSpan): SymfonyResponse
    {
        $kernel = $this->app->make(HttpKernel::class);
        if (!$serverSpan) {
            return $kernel->handle($request);
        }

        $request->attributes->set(SpanInterface::class, $serverSpan);
        $response = null;
        $this->inSpan($serverSpan, function () use ($kernel, $request, &$response) {
            $response = $kernel->handle($request);
        });

        return $response;
    }

    /**
     * Get all exported spans with the given name
     * @param string $name
     * @return SpanDataInterface[]
     */
    protected function spans(string $name): array
    {
        return array_values(array_filter(
            $this->exporter->getSpans(),
            fn (SpanDataInterface $span) => $span->getName() === $name
        ));
    }

    /**
     * Get the only exported span with the given name
     * @param string $name
     * @return SpanDataInterface
     */
    protected function span(string $name): SpanDataInterface
    {
        $spans = $this->spans($name);
        $this->assertCount(1, $spans, "Expected exactly one span '$name'");

        return $spans[0];
    }
}
