<?php

namespace Gentics\PortalPhp\Features\Keycloak\Contract;

use Lcobucci\JWT\Token;

interface Authenticator
{
    /**
     * @param Token $token
     * @return mixed
     */
    public function authUser(Token $token);
}
