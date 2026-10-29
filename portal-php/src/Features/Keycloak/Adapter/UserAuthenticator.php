<?php

namespace Gentics\PortalPhp\Features\Keycloak\Adapter;

use Gentics\PortalPhp\Features\Keycloak\User;
use Gentics\PortalPhp\Features\Keycloak\Util;
use Gentics\PortalPhp\Features\Keycloak\Contract\Authenticator;

use Lcobucci\JWT\Token;

class UserAuthenticator implements Authenticator
{
    /**
     * @param Token $token
     * @return mixed|void
     */
    public function authUser(Token $token)
    {
        $claims = Util::getClaims($token);

        $model = new User([
            'username' => $claims->get('preferred_username'),
            'name' =>  $claims->get('name'),
            'email' =>  $claims->get('email'),
            'claims' => $claims->all(),
            'token' => $token
        ]);

        \Auth::setUser($model);
    }
}
