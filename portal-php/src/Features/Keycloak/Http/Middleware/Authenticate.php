<?php

namespace Gentics\PortalPhp\Features\Keycloak\Http\Middleware;

use Closure;
use Exception;
use Gentics\PortalPhp\Features\Keycloak\Contract\Authenticator;

use Illuminate\Http\Client\Request;
use Lcobucci\Clock\Clock;
use Lcobucci\JWT\Signer;
use Lcobucci\JWT\Token;
use Lcobucci\JWT\Token\Parser;
use Lcobucci\JWT\Validation\Validator;
use Lcobucci\JWT\Validation\Constraint\SignedWith;
use Lcobucci\JWT\Validation\Constraint\ValidAt;

use Gentics\PortalPhp\Features\Keycloak\Util;
use Gentics\PortalPhp\Features\Keycloak\TokenRefresh;
use Gentics\PortalPhp\Features\Keycloak\Storage\Keystore;
use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;

class Authenticate
{
    /**
     * @var TokenStorage
     */
    private $tokenStorage;

    /**
     * @var Parser
     */
    private $jwtParser;

    /**
     * @var Validator
     */
    private $validator;

    /**
     * @var Clock
     */
    private $clock;

    /**
     * @var Signer
     */
    private $signer;

    /**
     * @var Keystore
     */
    private $keystore;

    /**
     * @var TokenRefresh
     */
    private $tokenRefresher;

    /**
     * @var Authenticator
     */
    private $authenticator;

    /**
     * Authenticate constructor.
     *
     * @param TokenStorage  $tokenStorage
     * @param Parser $jwtParser
     * @param Signer $signer
     * @param Authenticator $authenticator
     * @param TokenRefresh $tokenRefresher
     * @param Keystore $keystore
     */
    public function __construct(
        TokenStorage $tokenStorage,
        Parser $jwtParser,
        Validator $validator,
        Clock $clock,
        Signer $signer,
        Authenticator $authenticator,
        TokenRefresh $tokenRefresher,
        Keystore $keystore
    ) {
        $this->tokenStorage = $tokenStorage;
        $this->validator = $validator;
        $this->clock = $clock;
        $this->signer = $signer;
        $this->keystore = $keystore;
        $this->tokenRefresher = $tokenRefresher;
        $this->authenticator = $authenticator;
        $this->jwtParser = $jwtParser;
    }

    /**
     * Refresh old token
     *
     * @param Token $token
     * @return Token|null
     */
    protected function refresh(Token $token, \Illuminate\Http\Request $request)
    {
        $claims = Util::getClaims($token);

        try {
            $refreshedIDToken = $this->tokenRefresher->refreshIDToken($request);
            $this->tokenStorage->store($refreshedIDToken);
            $newToken = $this->jwtParser->parse($refreshedIDToken);

            return $newToken;
        } catch (\Exception $e) {
            return null;
        }
    }

    /**
     * @param $token
     * @param $key
     * @return bool
     */
    protected function isValidSignature($token, $key): bool
    {
        return $this->validator->validate($token, new SignedWith($this->signer, $key));
    }

    /**
     * @param $token
     * @return bool
     */
    protected function isNotExpired($token): bool
    {
        return $this->validator->validate($token, new ValidAt($this->clock));
    }

    /**
     * Validate an ID Token of AUTH cookie.
     *
     * @param  \Illuminate\Http\Request $request
     * @param  \Closure                 $next
     *
     * @return mixed
     */
    public function handle($request, Closure $next)
    {
        $token = $this->tokenStorage->parse($request);

        // TODO: Should check auth route in better place
        if ($request->is('auth/*')) {
            if ($token !== null) {
                $request->attributes->add(['id_token_hint' => $token->toString()]);
            }
            return $next($request);
        }

        if (!$token) {
            return $next($request);
        }

        $kid = $token->headers()->get('kid');
        $key = $this->keystore->get($kid);

        if (!$key) {
            return $next($request);
        }

        if (!$this->isValidSignature($token, $key)) {
            return $next($request);
        }

        if (!$this->isNotExpired($token)) {
            $token = $this->refresh($token, $request);

            if (!$token) {
                return $next($request);
            }
        }

        $this->authenticator->authUser($token);

        return $next($request);
    }

    /**
     * {@inheritdoc}
     */
    public function redirectTo()
    {
        return route('login', ['returnTo' => url()->full()]);
    }
}
