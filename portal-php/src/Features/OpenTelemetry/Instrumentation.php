<?php

namespace Gentics\PortalPhp\Features\OpenTelemetry;

use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequest;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadByPath;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Illuminate\Routing\ControllerDispatcher;
use Illuminate\Routing\Route;
use Illuminate\View\View;
use OpenTelemetry\API\Instrumentation\CachedInstrumentation;
use OpenTelemetry\API\Trace\Span;
use OpenTelemetry\API\Trace\SpanKind;
use OpenTelemetry\API\Trace\StatusCode;
use OpenTelemetry\Context\Context;
use OpenTelemetry\SDK\Sdk;
use Throwable;
use WeakMap;

use function OpenTelemetry\Instrumentation\hook;

/**
 * Adds spans for rendering, Mesh requests, controller dispatch and the HTTP cache,
 * using the hooks of the PHP extension "opentelemetry".
 */
class Instrumentation
{
    /** @var string Name to use in OTEL_PHP_DISABLED_INSTRUMENTATIONS */
    const NAME = 'portal-php';

    /** @var bool Hooks must only be registered once per process */
    protected static $registered = false;

    /** @var CachedInstrumentation */
    protected static $instrumentation;

    /**
     * Per object: nesting depth per span kind and whether a cached request fetched from Mesh.
     * A hook on a parent method also fires for the overriding method, so a call to parent::fetch()
     * must not start a second span for the same object.
     *
     * @var WeakMap<object, array>
     */
    protected static $active;

    /**
     * Register the hooks, if the extension is loaded and the instrumentation is not disabled.
     *
     * @return void
     */
    public static function register()
    {
        if (static::$registered || !extension_loaded('opentelemetry')) {
            return;
        }

        if (class_exists(Sdk::class) && Sdk::isInstrumentationDisabled(static::NAME)) {
            return;
        }

        static::$registered = true;
        static::$instrumentation = new CachedInstrumentation('com.gentics.portal-php');
        static::$active = new WeakMap();

        hook(
            View::class,
            'render',
            pre: static function (View $view) {
                static::start($view, 'render', 'render ' . $view->name(), [
                    'portal.view.name' => $view->name(),
                    'code.file.path' => $view->getPath(),
                ]);
            },
            post: static function (View $view, array $params, $returnValue, ?Throwable $exception) {
                static::end($view, 'render', $exception);
            }
        );

        hook(
            ControllerDispatcher::class,
            'dispatch',
            pre: static function (ControllerDispatcher $dispatcher, array $params) {
                /** @var Route $route */
                [$route, $controller, $method] = $params;
                static::start($dispatcher, 'dispatch', class_basename($controller) . '@' . $method, [
                    'code.function.name' => get_class($controller) . '::' . $method,
                    'http.route' => $route->uri(),
                ]);
            },
            post: static function (ControllerDispatcher $dispatcher, array $params, $returnValue, ?Throwable $exception) {
                static::end($dispatcher, 'dispatch', $exception);
            }
        );

        hook(
            GraphqlRequest::class,
            'fetch',
            pre: static function (GraphqlRequest $request) {
                $attributes = [
                    'portal.mesh.request.class' => get_class($request),
                    'portal.mesh.template' => $request->getTemplateName(),
                ];
                if ($request instanceof LoadByPath) {
                    $attributes['portal.mesh.path'] = $request->getPath();
                }

                // Tells the surrounding fetchCached() span that the cache was missed
                $state = static::$active[$request] ?? [];
                $state['fetched'] = true;
                static::$active[$request] = $state;

                static::start($request, 'fetch', 'mesh ' . $request->getTemplateName(), $attributes);
            },
            post: static function (GraphqlRequest $request, array $params, $returnValue, ?Throwable $exception) {
                static::end($request, 'fetch', $exception);
            }
        );

        // Fires for every request class using the trait WithCache
        hook(
            GraphqlRequest::class,
            'fetchCached',
            pre: static function (GraphqlRequest $request) {
                $state = static::$active[$request] ?? [];
                if (($state['fetchCached'] ?? 0) === 0) {
                    $state['fetched'] = false;
                    static::$active[$request] = $state;
                }

                static::start($request, 'fetchCached', 'mesh cache ' . $request->getTemplateName(), [
                    'portal.mesh.request.class' => get_class($request),
                    'portal.mesh.template' => $request->getTemplateName(),
                ]);
            },
            post: static function (GraphqlRequest $request, array $params, $returnValue, ?Throwable $exception) {
                static::end($request, 'fetchCached', $exception, [
                    'portal.mesh.cache.hit' => !(static::$active[$request]['fetched'] ?? false),
                ]);
            }
        );

        hook(
            Mesh::class,
            'tryServeEtagCachedResponse',
            post: static function ($class, array $params, $returnValue) {
                Span::getCurrent()->setAttribute('portal.mesh.etag_cache.hit', $returnValue !== false);
            }
        );

        hook(
            AppCache::class,
            'handle',
            pre: static function (AppCache $cache) {
                static::start($cache, 'handle', 'http cache', []);
            },
            post: static function (AppCache $cache, array $params, $returnValue, ?Throwable $exception) {
                // The traces of the main request come first, e.g. ['miss', 'store'] or ['fresh']
                $traces = $cache->getTraces();
                $status = implode(', ', reset($traces) ?: []);

                static::end($cache, 'handle', $exception, ['portal.http_cache.status' => $status]);
            }
        );
    }

    /**
     * Start a span and make it the current one, unless one of this kind is already active for the object.
     *
     * @param object $object
     * @param string $kind
     * @param string $name
     * @param array $attributes
     * @return void
     */
    protected static function start(object $object, string $kind, string $name, array $attributes)
    {
        $state = static::$active[$object] ?? [];
        $state[$kind] = ($state[$kind] ?? 0) + 1;
        static::$active[$object] = $state;

        if ($state[$kind] > 1) {
            return;
        }

        $span = static::$instrumentation->tracer()
            ->spanBuilder($name)
            ->setSpanKind(SpanKind::KIND_INTERNAL)
            ->setParent(Context::getCurrent())
            ->setAttributes($attributes)
            ->startSpan();

        Context::storage()->attach($span->storeInContext(Context::getCurrent()));
    }

    /**
     * End the span started by start(), when the outermost call for the object returns.
     *
     * @param object $object
     * @param string $kind
     * @param Throwable|null $exception
     * @param array $attributes Attributes known only at the end
     * @return void
     */
    protected static function end(object $object, string $kind, ?Throwable $exception, array $attributes = [])
    {
        $state = static::$active[$object] ?? [];
        $state[$kind] = max(($state[$kind] ?? 0) - 1, 0);
        static::$active[$object] = $state;

        if ($state[$kind] > 0) {
            return;
        }

        $scope = Context::storage()->scope();
        if ($scope === null) {
            return;
        }
        $scope->detach();

        $span = Span::fromContext($scope->context());
        $span->setAttributes($attributes);

        if ($exception) {
            $span->recordException($exception);
            $span->setStatus(StatusCode::STATUS_ERROR, $exception->getMessage());
        }

        $span->end();
    }
}
