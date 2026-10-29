<?php

namespace Gentics\PortalPhp\Features\Keycloak;

use Illuminate\Auth\GuardHelpers;
use Illuminate\Contracts\Auth\Authenticatable;
use Illuminate\Contracts\Auth\Guard;
use Illuminate\Support\Traits\Macroable;

class KeycloakGuard implements Guard
{
    use GuardHelpers, Macroable;

    /**
     * @return \Illuminate\Contracts\Auth\Authenticatable
     */
    public function user()
    {
        return $this->user;
    }

    /**
     * @param array $credentials
     * @return bool
     */
    public function validate(array $credentials = [])
    {
        return $this->user instanceof Authenticatable;
    }
}
