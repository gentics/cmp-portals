<?php

namespace Gentics\PortalPhp\Features\Keycloak;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;

use Lcobucci\JWT\Token;
use Lcobucci\JWT\UnencryptedToken;

class Util
{
    public static function getClaims(Token $token)
    {
        if (!$token instanceof UnencryptedToken) {
            throw new PortalPhpException('Cannot parse claims');
        } else {
            $claims = $token->claims();
        }

        return $claims;
    }
}
