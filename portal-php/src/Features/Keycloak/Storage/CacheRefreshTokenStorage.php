<?php

namespace Gentics\PortalPhp\Features\Keycloak\Storage;

use Gentics\PortalPhp\Features\Keycloak\Util;
use Gentics\PortalPhp\Features\Keycloak\Contract\RefreshTokenStorage;

use Carbon\Carbon;
use Lcobucci\JWT\Token\Parser;
use Illuminate\Contracts\Cache\Repository as CacheRepository;

/**
 * Stores and Fetches Refresh tokens via Cache backend
 *
 * Class CacheRefreshTokenStorage
 * @package Gentics\PortalPhp\Features\Keycloak\Storage
 */
class CacheRefreshTokenStorage implements RefreshTokenStorage
{
    /**
     * @var Parser
     */
    private $parser;

    /**
     * @var CacheRepository
     */
    private $cache;

    /**
     * CacheRefreshTokenStorage constructor.
     * @param Parser $parser
     * @param CacheRepository $cache
     */
    public function __construct(Parser $parser, CacheRepository $cache)
    {
        $this->parser = $parser;
        $this->cache = $cache;
    }

    /**
     * Save refresh token in Cache until expiration date
     *
     * @param string $sub
     * @param string $iss
     * @param string $refreshToken
     */
    public function store(string $sub, string $iss, string $refreshToken): void
    {
        $token = $this->parser->parse($refreshToken);
        $claims = Util::getClaims($token);
        $expire = Carbon::createFromTimestampUTC($claims->get('exp')->format("U"));

        if ($expire->toDateString() > Carbon::now()->toDateString()) {
            $expire = $expire->subSeconds(1);
        } else {
            $expire = Carbon::now()->addMinutes(30);
        }

        $this->cache->put("keycloak-refresh-token.{$sub}.{$iss}", $refreshToken, $expire);
    }


    /**
     * Fetch and return Refresh token by iss and sub params
     *
     * @param string $sub
     * @param string $iss
     * @return null|string
     */
    public function fetch(string $sub, string $iss): ?string
    {
        return $this->cache->get("keycloak-refresh-token.{$sub}.{$iss}");
    }
}
