<?php

namespace Gentics\PortalPhp\Features\OpenTelemetry;

use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\Features\Keycloak\Contract\Authenticator;
use Gentics\PortalPhp\Features\Keycloak\Http\Controllers\AuthController;
use Gentics\PortalPhp\Features\Keycloak\Socialite\Provider as KeycloakProvider;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequest;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadByPath;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Illuminate\Foundation\Http\Kernel;
use Illuminate\Http\Request;
use Illuminate\Routing\ControllerDispatcher;
use Illuminate\Routing\Route;
use Illuminate\View\View;
use Laravel\Socialite\Contracts\User as SocialiteUser;
use Lcobucci\JWT\Token\RegisteredClaims;
use Lcobucci\JWT\UnencryptedToken;
use Monolog\Logger;
use Monolog\LogRecord;
use OpenTelemetry\API\Instrumentation\CachedInstrumentation;
use OpenTelemetry\API\Logs\LogRecordBuilderInterface;
use OpenTelemetry\API\Metrics\MeterInterface;
use OpenTelemetry\API\Trace\Span;
use OpenTelemetry\API\Trace\SpanInterface;
use OpenTelemetry\API\Trace\SpanKind;
use OpenTelemetry\API\Trace\StatusCode;
use OpenTelemetry\Context\Context;
use OpenTelemetry\SDK\Sdk;
use Symfony\Component\HttpFoundation\Request as SymfonyRequest;
use Symfony\Component\HttpFoundation\Response;
use Throwable;
use WeakMap;

use function OpenTelemetry\Instrumentation\hook;

/**
 * Adds spans for rendering, Mesh requests, controller dispatch and the HTTP cache,
 * and metrics for incoming requests, Mesh requests, the caches and rendering,
 * using the hooks of the PHP extension "opentelemetry".
 *
 * The server span of a request is created by the Laravel instrumentation, which stores it in the request attribute
 * SpanInterface::class. This class adds to it: the route on requests handled by the HTTP cache, the ID of the
 * logged in user and the redacted query of the Keycloak callback. It also adds the trace ID to the response
 * (header X-Trace-Id) and to log records, both only when the request is sampled.
 */
class Instrumentation
{
    /** @var string Name to use in OTEL_PHP_DISABLED_INSTRUMENTATIONS */
    const NAME = 'portal-php';

    /** @var string Response header containing the trace ID of a sampled request, as in Gentics CMS and Portal | java */
    const TRACE_ID_HEADER = 'X-Trace-Id';

    /** @var string Replacement for the values of redacted query parameters */
    const REDACTED = 'REDACTED';

    /** @var string[] Query parameters of the Keycloak callback, which must not be recorded */
    const OAUTH_CALLBACK_PARAMETERS = ['code', 'session_state'];

    /** @var float[] Bucket boundaries in seconds, as advised for http.server.request.duration */
    const DURATION_BUCKETS = [0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10];

    /** @var string[] Known HTTP methods, others are recorded as "_OTHER" */
    const HTTP_METHODS = ['CONNECT', 'DELETE', 'GET', 'HEAD', 'OPTIONS', 'PATCH', 'POST', 'PUT', 'TRACE'];

    /** @var bool Hooks must only be registered once per process */
    protected static $registered = false;

    /** @var CachedInstrumentation */
    protected static $instrumentation;

    /**
     * Per object: nesting depth and start time per kind and whether a cached request fetched from Mesh.
     * A hook on a parent method also fires for the overriding method, so a call to parent::fetch()
     * must not start a second span for the same object.
     *
     * @var WeakMap<object, array>
     */
    protected static $active;

    /**
     * Instruments per meter, so a new meter provider gets its own instruments
     *
     * @var WeakMap<MeterInterface, array>
     */
    protected static $instruments;

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
        static::$instruments = new WeakMap();

        // No span: the server span belongs to the Laravel instrumentation
        hook(
            Kernel::class,
            'handle',
            pre: static function (Kernel $kernel) {
                static::enter($kernel, 'request');
            },
            post: static function (Kernel $kernel, array $params, $returnValue, ?Throwable $exception) {
                $duration = static::leave($kernel, 'request');
                if ($duration === null) {
                    return;
                }

                /** @var Request $request */
                [$request] = $params;

                // Set outside the HTTP cache, so a cached response never contains the trace ID of another request
                $serverSpan = static::serverSpan($request);
                if ($serverSpan && $returnValue instanceof Response) {
                    $spanContext = $serverSpan->getContext();
                    if ($spanContext->isValid() && $spanContext->isSampled()) {
                        $returnValue->headers->set(static::TRACE_ID_HEADER, $spanContext->getTraceId());
                    }
                }

                $method = strtoupper($request->getMethod());
                $attributes = [
                    'http.request.method' => in_array($method, static::HTTP_METHODS, true) ? $method : '_OTHER',
                    'url.scheme' => $request->getScheme(),
                ];

                $route = $request->route();
                if ($route instanceof Route) {
                    $attributes['http.route'] = $route->uri();
                }

                if ($returnValue instanceof Response) {
                    $status = $returnValue->getStatusCode();
                    $attributes['http.response.status_code'] = $status;
                    if ($status >= 500) {
                        $attributes['error.type'] = (string) $status;
                    }
                }
                if ($exception) {
                    $attributes['error.type'] = get_class($exception);
                }

                static::instruments()['request']->record($duration, $attributes);
            }
        );

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
                $duration = static::end($view, 'render', $exception);
                if ($duration !== null) {
                    static::instruments()['render']->record($duration, ['portal.view.name' => $view->name()]);
                }
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
            post: static function (ControllerDispatcher $dispatcher,
                    array $params, $returnValue, ?Throwable $exception) {
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
                $duration = static::end($request, 'fetch', $exception);
                if ($duration === null) {
                    return;
                }

                $attributes = ['portal.mesh.template' => $request->getTemplateName()];
                if ($exception) {
                    $attributes['error.type'] = get_class($exception);
                }
                static::instruments()['mesh']->record($duration, $attributes);
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
                $hit = !(static::$active[$request]['fetched'] ?? false);
                $duration = static::end($request, 'fetchCached', $exception, ['portal.mesh.cache.hit' => $hit]);
                if ($duration !== null) {
                    static::instruments()['meshCache']->add(1, [
                        'portal.mesh.template' => $request->getTemplateName(),
                        'portal.mesh.cache.hit' => $hit,
                    ]);
                }
            }
        );

        hook(
            Mesh::class,
            'tryServeEtagCachedResponse',
            post: static function ($class, array $params, $returnValue) {
                $hit = $returnValue !== false;
                Span::getCurrent()->setAttribute('portal.mesh.etag_cache.hit', $hit);
                static::instruments()['etagCache']->add(1, ['portal.mesh.etag_cache.hit' => $hit]);
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

                $duration = static::end($cache, 'handle', $exception, ['portal.http_cache.status' => $status]);
                if ($duration !== null) {
                    static::instruments()['httpCache']->add(1, ['portal.http_cache.status' => $status]);

                    // Routing runs only for a copy of the request on a miss and not at all on a hit,
                    // so the Laravel instrumentation does not find a route for the server span
                    [$request] = $params;
                    $serverSpan = static::serverSpan($request);
                    if ($serverSpan && $serverSpan->isRecording() && $request instanceof Request) {
                        static::setRoute($serverSpan, $request);
                    }
                }
            }
        );

        hook(
            Authenticator::class,
            'authUser',
            post: static function (Authenticator $authenticator, array $params, $returnValue, ?Throwable $exception) {
                [$token] = $params;
                if (!$exception && $token instanceof UnencryptedToken) {
                    static::setUserId($token->claims()->get(RegisteredClaims::SUBJECT));
                }
            }
        );

        // The login in the Keycloak callback
        hook(
            KeycloakProvider::class,
            'user',
            post: static function (KeycloakProvider $provider, array $params, $returnValue) {
                if ($returnValue instanceof SocialiteUser) {
                    static::setUserId($returnValue->getId());
                }
            }
        );

        hook(
            AuthController::class,
            'callback',
            pre: static function (AuthController $controller, array $params) {
                [$request] = $params;
                $serverSpan = static::serverSpan($request);
                $query = $request instanceof Request ? $request->getQueryString() : null;
                if (!$serverSpan || !$serverSpan->isRecording() || $query === null) {
                    return;
                }

                $redacted = static::redactQuery($query, static::OAUTH_CALLBACK_PARAMETERS);
                $serverSpan->setAttribute('url.query', $redacted);
                $serverSpan->setAttribute('url.full', str_replace($query, $redacted, $request->fullUrl()));
            }
        );

        hook(
            Logger::class,
            '__construct',
            post: static function (Logger $logger) {
                $logger->pushProcessor(static function (LogRecord $record) {
                    $spanContext = Span::getCurrent()->getContext();
                    if ($spanContext->isValid() && $spanContext->isSampled()) {
                        $record->extra['trace_id'] = $spanContext->getTraceId();
                        $record->extra['span_id'] = $spanContext->getSpanId();
                    }

                    return $record;
                });
            }
        );

        // Log records exported with OpenTelemetry, e.g. by the Laravel instrumentation, would otherwise
        // carry the ID of a trace that is not exported
        hook(
            LogRecordBuilderInterface::class,
            'emit',
            pre: static function (LogRecordBuilderInterface $builder) {
                $spanContext = Span::getCurrent()->getContext();
                if ($spanContext->isValid() && !$spanContext->isSampled()) {
                    $builder->setContext(false);
                }
            }
        );
    }

    /**
     * Get the server span of the request, created by the Laravel instrumentation
     *
     * @param mixed $request
     * @return SpanInterface|null
     */
    protected static function serverSpan($request): ?SpanInterface
    {
        $span = $request instanceof SymfonyRequest ? $request->attributes->get(SpanInterface::class) : null;

        return $span instanceof SpanInterface ? $span : null;
    }

    /**
     * Set the route matching the request on the server span, in the same format as the Laravel instrumentation
     *
     * @param SpanInterface $serverSpan
     * @param Request $request
     * @return void
     */
    protected static function setRoute(SpanInterface $serverSpan, Request $request)
    {
        try {
            $route = app('router')->getRoutes()->match($request);
        } catch (Throwable $exception) {
            return;
        }

        $serverSpan->updateName($request->method() . ' /' . ltrim($route->uri(), '/'));
        $serverSpan->setAttribute('http.route', $route->uri());
    }

    /**
     * Set the ID of the logged in user on the server span of the current request, if the span is recorded
     *
     * @param mixed $userId
     * @return void
     */
    protected static function setUserId($userId)
    {
        $serverSpan = static::serverSpan(app('request'));
        if ($serverSpan && $serverSpan->isRecording() && $userId !== null && $userId !== '') {
            $serverSpan->setAttribute('user.id', (string) $userId);
        }
    }

    /**
     * Replace the values of the given parameters in the query string with REDACTED
     *
     * @param string $query Query string without the leading "?"
     * @param string[] $names Names of the parameters to redact
     * @return string
     */
    protected static function redactQuery(string $query, array $names): string
    {
        return implode('&', array_map(static function (string $parameter) use ($names) {
            $index = strpos($parameter, '=');

            return $index !== false && in_array(substr($parameter, 0, $index), $names, true)
                ? substr($parameter, 0, $index + 1) . static::REDACTED
                : $parameter;
        }, explode('&', $query)));
    }

    /**
     * Get the instruments of the current meter, create them on first use
     *
     * @return array
     */
    protected static function instruments(): array
    {
        $meter = static::$instrumentation->meter();
        $buckets = ['ExplicitBucketBoundaries' => static::DURATION_BUCKETS];

        return static::$instruments[$meter] ??= [
            'request' => $meter->createHistogram(
                'http.server.request.duration',
                's',
                'Duration of HTTP server requests.',
                $buckets
            ),
            'mesh' => $meter->createHistogram(
                'portal.mesh.request.duration',
                's',
                'Duration of GraphQL requests to Gentics Mesh, including building the query and handling the response.',
                $buckets
            ),
            'meshCache' => $meter->createCounter(
                'portal.mesh.cache.lookups',
                '{lookup}',
                'Lookups of Gentics Mesh requests that are cached in the Laravel cache.'
            ),
            'etagCache' => $meter->createCounter(
                'portal.mesh.etag_cache.lookups',
                '{lookup}',
                'Lookups of the ETag cache for requests to Gentics Mesh.'
            ),
            'httpCache' => $meter->createCounter(
                'portal.http_cache.requests',
                '{request}',
                'Requests handled by the HTTP cache.'
            ),
            'render' => $meter->createHistogram(
                'portal.view.render.duration',
                's',
                'Duration of rendering a Blade view, including the views it includes.',
                $buckets
            ),
        ];
    }

    /**
     * Count a call for the object, remember the time of the outermost call
     *
     * @param object $object
     * @param string $kind
     * @return bool True for the outermost call
     */
    protected static function enter(object $object, string $kind): bool
    {
        $state = static::$active[$object] ?? [];
        $state[$kind] = ($state[$kind] ?? 0) + 1;
        if ($state[$kind] === 1) {
            $state[$kind . 'Start'] = hrtime(true);
        }
        static::$active[$object] = $state;

        return $state[$kind] === 1;
    }

    /**
     * Count the return of a call for the object
     *
     * @param object $object
     * @param string $kind
     * @return float|null Duration of the outermost call in seconds, when it returns, otherwise null
     */
    protected static function leave(object $object, string $kind): ?float
    {
        $state = static::$active[$object] ?? [];
        $state[$kind] = max(($state[$kind] ?? 0) - 1, 0);
        $start = $state[$kind] === 0 ? ($state[$kind . 'Start'] ?? null) : null;
        if ($start !== null) {
            unset($state[$kind . 'Start']);
        }
        static::$active[$object] = $state;

        return $start === null ? null : (hrtime(true) - $start) / 1e9;
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
        if (!static::enter($object, $kind)) {
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
     * @return float|null Duration of the outermost call in seconds, when it returns, otherwise null
     */
    protected static function end(object $object, string $kind, ?Throwable $exception, array $attributes = [])
    {
        $duration = static::leave($object, $kind);
        if ($duration === null) {
            return null;
        }

        $scope = Context::storage()->scope();
        if ($scope === null) {
            return $duration;
        }
        $scope->detach();

        $span = Span::fromContext($scope->context());
        $span->setAttributes($attributes);

        if ($exception) {
            $span->recordException($exception);
            $span->setStatus(StatusCode::STATUS_ERROR, $exception->getMessage());
        }

        $span->end();

        return $duration;
    }
}
