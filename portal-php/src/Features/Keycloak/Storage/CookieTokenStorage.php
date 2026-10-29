<?php

namespace Gentics\PortalPhp\Features\Keycloak\Storage;

use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;
use Lcobucci\JWT\Token\Parser;
use Illuminate\Http\Request;
use Lcobucci\JWT\Token;
use Illuminate\Support\Facades\Cookie;

/**
 * Parse, store and forget Authentication tokens via Cookie backend
 *
 * Class CookieTokenStorage
 * @package Gentics\PortalPhp\Features\Keycloak\Storage
 */
class CookieTokenStorage implements TokenStorage
{
    /**
     * Cookie name for storing Authentication token
     */
    const AUTH_COOKIE = "AUTH";

    /**
     * @var Parser
     */
    private $parser;

    /**
     * CookieTokenStorage constructor.
     *
     * @param Parser         $parser
     */
    public function __construct(Parser $parser)
    {
        $this->parser = $parser;
    }

    /**
     * @param Request $request
     * @return Token
     */
    public function parse(Request $request)
    {
        // Fetching auth cookie from incoming request
        $authToken = $request->cookie(self::AUTH_COOKIE);

        // Set authToken as an empty string if no auth token
        if (empty($authToken)) {
            $authToken = "";
        }

        /*
         * Try to parse Auth token. If its invalid, return a null value,
         * which means no user validation can be done.
         */
        try {
            $token = $this->parser->parse($authToken);
        } catch (\Exception $e) {
            $token = null;
        }

        return $token;
    }

    /**
     * {@inheritdoc}
     */
    public function store($token): void
    {
        Cookie::queue(
            Cookie::make(self::AUTH_COOKIE, $token, 0, '/', null, null, true)
        );
    }

    /**
     * {@inheritdoc}
     */
    public function forget(): void
    {
        Cookie::queue(
            Cookie::forget(self::AUTH_COOKIE)
        );
    }
}
