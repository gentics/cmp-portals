<?php

namespace Gentics\PortalPhp\Features\Keycloak;

use Exception;
use Gentics\PortalPhp\Features\Keycloak\Contract\RefreshTokenStorage;
use Illuminate\Support\Facades\App;

class TokenRefresh
{
    /**
     * @var RefreshTokenStorage
     */
    private $refreshTokenStorage;

    /**
     * TokenRefresh constructor.
     *
     * @param RefreshTokenStorage $refreshTokenStorage
     */
    public function __construct(RefreshTokenStorage $refreshTokenStorage)
    {
        $this->refreshTokenStorage = $refreshTokenStorage;
    }

    /**
     * @param string $sub
     * @param string $iss
     * @return string
     * @throws Exception
     */
    public function refreshIDToken($request): string
    {
        $refreshToken = $this->refreshTokenStorage->fetch($request);

        if (!$refreshToken) {
            throw new Exception("Failed to fetch refresh token");
        }

        $data = \Socialite::with('keycloak')->getRefreshTokenResponse($refreshToken);

        $this->refreshTokenStorage->store($data['refresh_token']);

        return $data['id_token'];
    }
}
