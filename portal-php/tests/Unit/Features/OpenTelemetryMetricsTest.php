<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadUser;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\OpenTelemetry\Instrumentation;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Filesystem\Filesystem;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Request as RequestFacade;
use OpenTelemetry\API\Instrumentation\Configurator;
use OpenTelemetry\Context\ScopeInterface;
use OpenTelemetry\SDK\Metrics\Data\HistogramDataPoint;
use OpenTelemetry\SDK\Metrics\Data\Metric;
use OpenTelemetry\SDK\Metrics\Data\NumberDataPoint;
use OpenTelemetry\SDK\Metrics\MeterProvider;
use OpenTelemetry\SDK\Metrics\MetricExporter\InMemoryExporter;
use OpenTelemetry\SDK\Metrics\MetricReader\ExportingReader;
use OpenTelemetry\SDK\Metrics\TemporalityPreference;
use PHPUnit\Framework\Attributes\RequiresPhpExtension;
use Symfony\Component\HttpFoundation\Request as SymfonyRequest;
use Symfony\Component\HttpFoundation\Response as SymfonyResponse;
use Symfony\Component\HttpKernel\HttpCache\Store;
use Symfony\Component\HttpKernel\HttpKernelInterface;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * Tests for the metrics of the OpenTelemetry instrumentation
 */
#[RequiresPhpExtension('opentelemetry')]
class OpenTelemetryMetricsTest extends TestCase
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
     * @var ExportingReader
     */
    protected $reader;

    /**
     * @var ScopeInterface
     */
    protected $scope;

    /**
     * @var Metric[]|null Collected metrics by name
     */
    protected $metrics = null;

    protected function setUp(): void
    {
        parent::setUp();

        // Delta, as recommended for PHP
        $this->exporter = new InMemoryExporter(temporality: TemporalityPreference::delta());
        $this->reader = new ExportingReader($this->exporter);
        $meterProvider = MeterProvider::builder()->addReader($this->reader)->build();
        $this->scope = Configurator::create()->withMeterProvider($meterProvider)->activate();

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
     * A page request records the request, the Mesh request, the ETag cache lookup and the rendering
     */
    public function testPageRequest()
    {
        $this->mockResponse("graphQL", "/testLoadByPath.json");
        $response = $this->call('GET', '/mysuperawesomepage.html', [], [], [], $this->getBrowserRequestHeaders());
        $response->assertOk();

        $request = $this->histogramPoint('http.server.request.duration', [
            'http.request.method' => 'GET',
            'url.scheme' => 'http',
            'http.route' => '{path}',
            'http.response.status_code' => 200,
        ]);
        $this->assertSame(1, $request->count);
        $this->assertGreaterThan(0, $request->sum);
        $this->assertSame(Instrumentation::DURATION_BUCKETS, $request->explicitBounds);
        $this->assertSame('s', $this->metric('http.server.request.duration')->unit);

        // LoadContent::fetch() calls parent::fetch(), which must be recorded once
        $mesh = $this->histogramPoint('portal.mesh.request.duration', ['portal.mesh.template' => 'loadContent']);
        $this->assertSame(1, $mesh->count);
        $this->assertGreaterThan(0, $mesh->sum);
        $this->assertLessThanOrEqual($request->sum, $mesh->sum);
        $this->assertSame('s', $this->metric('portal.mesh.request.duration')->unit);

        $etag = $this->numberPoint('portal.mesh.etag_cache.lookups', ['portal.mesh.etag_cache.hit' => false]);
        $this->assertSame(1, $etag->value);
        $this->assertSame('{lookup}', $this->metric('portal.mesh.etag_cache.lookups')->unit);

        $render = $this->histogramPoint('portal.view.render.duration', ['portal.view.name' => 'contentpage']);
        $this->assertSame(1, $render->count);
        $this->assertSame('s', $this->metric('portal.view.render.duration')->unit);
    }

    /**
     * A response with a 5xx status code is recorded with the status code as error type
     */
    public function testServerError()
    {
        // Mesh is not ready
        $this->mockResponse("healthReady", null, Response::HTTP_SERVICE_UNAVAILABLE);
        $this->call('GET', '/api/health/ready')->assertStatus(503);

        $request = $this->histogramPoint('http.server.request.duration', [
            'http.request.method' => 'GET',
            'url.scheme' => 'http',
            'http.route' => 'api/health/ready',
            'http.response.status_code' => 503,
            'error.type' => '503',
        ]);
        $this->assertSame(1, $request->count);
    }

    /**
     * An exception leaving the kernel is recorded as error type, without status code,
     * and as error type of the Mesh request that threw it
     */
    public function testRequestException()
    {
        $this->mockResponse("graphQL", null, Response::HTTP_NOT_FOUND);

        try {
            $this->call('GET', '/nonexisting.html', [], [], [], $this->getBrowserRequestHeaders());
            $this->fail('NotFoundHttpException expected');
        } catch (NotFoundHttpException $exception) {
        }

        $request = $this->histogramPoint('http.server.request.duration', [
            'http.request.method' => 'GET',
            'url.scheme' => 'http',
            'http.route' => '{path}',
            'error.type' => NotFoundHttpException::class,
        ]);
        $this->assertSame(1, $request->count);

        $mesh = $this->histogramPoint('portal.mesh.request.duration', [
            'portal.mesh.template' => 'loadContent',
            'error.type' => NotFoundHttpException::class,
        ]);
        $this->assertSame(1, $mesh->count);
    }

    /**
     * An unknown HTTP method is recorded as "_OTHER"
     */
    public function testUnknownMethod()
    {
        $this->withExceptionHandling();

        $this->call('FOO', '/api/health/live');

        $points = $this->points('http.server.request.duration');
        $this->assertCount(1, $points);
        $this->assertSame('_OTHER', $points[0]->attributes->get('http.request.method'));
    }

    /**
     * fetchCached() counts a miss and a hit
     */
    public function testFetchCached()
    {
        $this->mockResponse("graphQL", "/testLoadUser.json");

        Mesh::request(LoadUser::class)->fetchCached();
        Mesh::request(LoadUser::class)->fetchCached();

        $miss = $this->numberPoint('portal.mesh.cache.lookups', [
            'portal.mesh.template' => 'loadUser',
            'portal.mesh.cache.hit' => false,
        ]);
        $hit = $this->numberPoint('portal.mesh.cache.lookups', [
            'portal.mesh.template' => 'loadUser',
            'portal.mesh.cache.hit' => true,
        ]);
        $this->assertSame(1, $miss->value);
        $this->assertSame(1, $hit->value);
        $this->assertSame('{lookup}', $this->metric('portal.mesh.cache.lookups')->unit);

        // Only the miss fetched from Mesh
        $mesh = $this->histogramPoint('portal.mesh.request.duration', ['portal.mesh.template' => 'loadUser']);
        $this->assertSame(1, $mesh->count);
    }

    /**
     * ETag cache lookups are counted as hit or miss
     */
    public function testEtagCacheLookups()
    {
        RequestFacade::swap(Request::create('/', 'GET', [], [], [], ['HTTP_IF_NONE_MATCH' => '"abc-gzip"']));

        $this->assertFalse(Mesh::tryServeEtagCachedResponse());
        Cache::put(Mesh::ETAG_CACHE_KEY_PREFIX . '"abc"', ['ETag' => ['"abc"']], 60);
        $this->assertNotFalse(Mesh::tryServeEtagCachedResponse());
        $this->assertNotFalse(Mesh::tryServeEtagCachedResponse());

        $miss = $this->numberPoint('portal.mesh.etag_cache.lookups', ['portal.mesh.etag_cache.hit' => false]);
        $hit = $this->numberPoint('portal.mesh.etag_cache.lookups', ['portal.mesh.etag_cache.hit' => true]);
        $this->assertSame(1, $miss->value);
        $this->assertSame(2, $hit->value);
    }

    /**
     * Requests of the HTTP cache are counted per cache status
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
            $appCache->handle(SymfonyRequest::create('http://localhost/cached'));
        } finally {
            (new Filesystem())->deleteDirectory($cacheDir);
        }

        $miss = $this->numberPoint('portal.http_cache.requests', ['portal.http_cache.status' => 'miss, store']);
        $fresh = $this->numberPoint('portal.http_cache.requests', ['portal.http_cache.status' => 'fresh']);
        $this->assertSame(1, $miss->value);
        $this->assertSame(2, $fresh->value);
        $this->assertSame('{request}', $this->metric('portal.http_cache.requests')->unit);
    }

    /**
     * Every rendered view is recorded with its name, an included view separately
     */
    public function testNestedViewRender()
    {
        view('opentelemetry.outer')->render();

        $outer = $this->histogramPoint('portal.view.render.duration', ['portal.view.name' => 'opentelemetry.outer']);
        $inner = $this->histogramPoint('portal.view.render.duration', ['portal.view.name' => 'opentelemetry.inner']);
        $this->assertSame(1, $outer->count);
        $this->assertSame(1, $inner->count);
        $this->assertGreaterThanOrEqual($inner->sum, $outer->sum);
    }

    /**
     * Get a collected metric by name. Collects once per test.
     * @param string $name
     * @return Metric
     */
    protected function metric(string $name): Metric
    {
        if ($this->metrics === null) {
            $this->reader->collect();
            $this->metrics = [];
            foreach ($this->exporter->collect(true) as $metric) {
                $this->metrics[$metric->name] = $metric;
            }
        }

        $this->assertArrayHasKey($name, $this->metrics, "Metric '$name' not collected");

        return $this->metrics[$name];
    }

    /**
     * Get the data points of a metric
     * @param string $name
     * @return HistogramDataPoint[]|NumberDataPoint[]
     */
    protected function points(string $name): array
    {
        return array_values(is_array($points = $this->metric($name)->data->dataPoints)
            ? $points
            : iterator_to_array($points));
    }

    /**
     * Get the only data point of a metric with exactly the given attributes
     * @param string $name
     * @param array $attributes
     * @return HistogramDataPoint|NumberDataPoint
     */
    protected function point(string $name, array $attributes)
    {
        ksort($attributes);
        $matching = array_values(array_filter($this->points($name), function ($point) use ($attributes) {
            $pointAttributes = $point->attributes->toArray();
            ksort($pointAttributes);
            return $pointAttributes === $attributes;
        }));
        $this->assertCount(1, $matching, "Expected one data point of '$name' with " . json_encode($attributes)
            . ', got ' . json_encode(array_map(fn ($point) => $point->attributes->toArray(), $this->points($name))));

        return $matching[0];
    }

    /**
     * @param string $name
     * @param array $attributes
     * @return HistogramDataPoint
     */
    protected function histogramPoint(string $name, array $attributes): HistogramDataPoint
    {
        return $this->point($name, $attributes);
    }

    /**
     * @param string $name
     * @param array $attributes
     * @return NumberDataPoint
     */
    protected function numberPoint(string $name, array $attributes): NumberDataPoint
    {
        return $this->point($name, $attributes);
    }
}
