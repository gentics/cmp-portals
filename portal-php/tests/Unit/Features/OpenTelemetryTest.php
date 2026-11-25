<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadUser;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Filesystem\Filesystem;
use Illuminate\Http\Response;
use OpenTelemetry\API\Instrumentation\Configurator;
use OpenTelemetry\API\Trace\StatusCode;
use OpenTelemetry\Context\ScopeInterface;
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
     * @var ScopeInterface
     */
    protected $scope;

    protected function setUp(): void
    {
        parent::setUp();

        $this->exporter = new InMemoryExporter();
        $tracerProvider = new TracerProvider(new SimpleSpanProcessor($this->exporter));
        $this->scope = Configurator::create()->withTracerProvider($tracerProvider)->activate();

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
