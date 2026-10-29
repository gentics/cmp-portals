<?php

namespace Gentics\PortalPhp\Features\Keycloak\Storage;

use Carbon\Carbon;
use Gentics\PortalPhp\Features\Keycloak\Contract\RefreshTokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Util;
use Lcobucci\JWT\Token\Parser;
use Illuminate\Http\Request;
use Lcobucci\JWT\Token;
use Illuminate\Cookie\CookieJar;

/**
 * Parse and store Refresh tokens via Cookie backend
 *
 * Class CookieRefreshTokenStorage
 * @package Gentics\PortalPhp\Features\Keycloak\Storage
 */
class CookieRefreshTokenStorage implements RefreshTokenStorage
{
    /**
     * Cookie name for storing Refresh token
     */
    const REFRESH_COOKIE = "AUTH_REFRESH";

    /**
     * @var Parser
     */
    private $parser;
    private CookieJar $cookieJar;


    /**
     * CookieTokenStorage constructor.
     *
     * @param Parser         $parser
     */
    public function __construct(Parser $parser, CookieJar $cookieJar)
    {
        $this->parser = $parser;
        $this->cookieJar = $cookieJar;
    }

    /**
     * @param Request $request
     * @return Token
     */
    public function fetch(Request $request): ?string
    {
        // Fetching refresh cookie from incoming request
        $refreshToken = $request->cookie(self::REFRESH_COOKIE);

        if (empty($refreshToken)) {
            $refreshToken = "";
        }
        return $refreshToken;
    }

    /**
     * {@inheritdoc}
     */
    public function store(string $refreshToken): void
    {
        // Parse the token to read its claims
        $token = $this->parser->parse($refreshToken);
        $claims = Util::getClaims($token);

        // Extract token expiration timestamp
        $expireTimestamp = $claims->get('exp')->getTimestamp();
        $nowTimestamp = Carbon::now()->timestamp;

        // Calculate remaining lifetime in seconds
        $remainingSeconds = $expireTimestamp - $nowTimestamp;

        // Convert seconds to minutes for the cookie lifetime
        // Round up to ensure cookie lasts slightly longer than token
        $expireMinutes = max(1, (int) ceil($remainingSeconds / 60));

        // Queue the cookie using CookieJar to avoid Symfony vs Facade issues
        $this->cookieJar->queue(
            $this->cookieJar->make(
                self::REFRESH_COOKIE, // cookie name
                $refreshToken,        // cookie value
                $expireMinutes,       // lifetime in minutes
                '/',                  // path
                null,                 // domain (default)
                null,                 // secure
                true                  // httpOnly (not accessible via JS)
            )
        );
    }

    /**
     * @param Request $request
     * @return Token|null
     */
    public function parse(Request $request): ?Token
    {
        $refreshToken = $request->cookie(self::REFRESH_COOKIE);

        if (empty($refreshToken)) {
            return null;
        }

        try {
            return $this->parser->parse($refreshToken);
        } catch (\Exception $e) {
            \Log::warning('Invalid refresh token in cookie', ['error' => $e->getMessage()]);
            return null;
        }
    }
}
