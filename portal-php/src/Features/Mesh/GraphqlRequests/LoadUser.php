<?php
namespace Gentics\PortalPhp\Features\Mesh\GraphqlRequests;

use Gentics\PortalPhp\Features\Mesh\GraphqlRequest;
use Illuminate\Support\Facades\Auth;
use Gentics\PortalPhp\Features\Mesh\Traits\WithCache;

/**
 * Class LoadUser
 * {@inheritDoc}
 * @package Gentics\PortalPhp\Features\Mesh\GraphqlRequests
 */
class LoadUser extends GraphqlRequest
{
    use WithCache;

    /** @var string The prefix to be used for the cacheKey */
    protected $cacheKeyPrefix = "mesh:graphql_user:";
    /** @var int Default cache timeout */
    protected $cacheTimeDefault = 15;
    /** @var string Cache timeout configuration key */
    protected $cacheTimeConfigKey = "portal.cache.user.cacheTime";

    /**
     * Generates unique cacheKey identifier from authentication subject
     *
     * @return string
     */
    protected function getCacheIdentifier()
    {
        $isUser = $this->portalPhp->isAuthenticationEnabled() && Auth::user() !== null;
        return sha1($isUser ? Auth::user()->claims['sub'] : 'anonymous');
    }
}
