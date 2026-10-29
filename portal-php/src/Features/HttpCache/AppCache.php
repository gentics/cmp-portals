<?php

namespace Gentics\PortalPhp\Features\HttpCache;

use FOS\HttpCache\SymfonyCache\CacheInvalidation;
use FOS\HttpCache\SymfonyCache\EventDispatchingHttpCache;
use FOS\HttpCache\SymfonyCache\DebugListener;

use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\HttpCache\HttpCache;
use Symfony\Component\HttpKernel\HttpCache\StoreInterface;
use Symfony\Component\HttpKernel\HttpCache\SurrogateInterface;
use Symfony\Component\HttpKernel\HttpKernelInterface;

use Illuminate\Support\Facades\Config;

use Gentics\PortalPhp\PortalPhp;

class AppCache extends HttpCache implements CacheInvalidation
{
    use EventDispatchingHttpCache;

    /**
     * Overwrite constructor to register event listeners for FOSHttpCache.
     */
    public function __construct(
        HttpKernelInterface $kernel,
        StoreInterface $store,
        SurrogateInterface $surrogate = null,
        array $options = []
    ) {
        parent::__construct($kernel, $store, $surrogate, $options);
        $app = app();

        $portalPhp = $app->make(PortalPhp::class);

        if ($portalPhp->isAuthenticationEnabled()) {
            $this->addSubscriber(new UserContextListener([
                'user_hash_accept_header' => 'application/vnd.gtx.user-context-hash',
                'user_hash_header' => $app['portal.cache.http.userContextHeader'],
                'user_hash_uri' => routerPrefix() . '/_gtx_user_context_hash',
                'user_hash_method' => 'GET',
                'user_identifier_headers' => $app['config']->get(
                    'portal.cache.http.userIdentifierHeaders',
                    ['Authorization', 'HTTP_AUTHORIZATION', 'PHP_AUTH_USER']
                ),
                'session_name_prefix' => 'AUTH',
            ]));
        }

        if (isset($options['debug']) && $options['debug']) {
            $this->addSubscriber(new DebugListener());
        }
    }

    /**
     * Made public to allow event listeners to do refresh operations.
     *
     * {@inheritDoc}
     */
    public function fetch(Request $request, bool $catch = false): Response
    {
        $subRequest = clone $request;

        // send no head requests because we want content
        if ('HEAD' === $request->getMethod()) {
            $subRequest->setMethod('GET');
        }

        // avoid that the backend sends no content
        $subRequest->headers->remove('if_modified_since');
        $subRequest->headers->remove('if_none_match');

        $response = $this->forward($subRequest, $catch);

        // Store Only when X-GTX-No-Store is not set and is cacheable
        if ($response->isCacheable() && !$response->headers->has('X-GTX-No-Store')) {
            $this->store($request, $response);
        }

        if ($response->headers->has('X-GTX-No-Store')) {
            $response->headers->remove('X-GTX-No-Store');
        }

        return $response;
    }

    /**
     * Set bypassInternal configuration to true, to disable caching from the code.
     */
    public static function bypass()
    {
        if (config('portal.cache.http.enabled', false)) {
            Config::set('portal.cache.http.__bypassInternal', true);
        }
    }

    /**
     * Set bypass when rule is enabled
     * @param string $bypassRule The name specified in the configuration to activate this bypass
     */
    public static function bypassWhen(string $bypassRule)
    {
        if (collect(config('portal.cache.http.exceptions', []))->contains($bypassRule)) {
            static::bypass();
        }
    }
}
