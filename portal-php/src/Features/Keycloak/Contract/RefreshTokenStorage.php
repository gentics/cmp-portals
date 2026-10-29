<?php

namespace Gentics\PortalPhp\Features\Keycloak\Contract;

use Illuminate\Http\Request;

interface RefreshTokenStorage
{
    /**
     * Stores Refresh token as a Cookie
     *
     * @param string $refreshToken
     */
    public function store(string $refreshToken): void;

    /**
     * Fetches refresh token from Cookie
     *
     * @param Request $request
     * @return null|string
     */
    public function fetch(Request $request): ?string;
}
