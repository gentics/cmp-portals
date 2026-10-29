<?php

namespace Gentics\PortalPhp\Features\Keycloak\Http\Controllers;

use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Contract\RefreshTokenStorage;

use Illuminate\Routing\Controller as BaseController;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cookie;
use Symfony\Component\HttpFoundation\RedirectResponse;

class AuthController extends BaseController
{
    /**
     * This cookie holds the URL where user should be redirected after
     * return from Keycloak flow.
     * @var string
     */
    private $returnToCookieKey = 'AUTH_REDIRECT';

    /**
     * @param string $path
     */
    private function createRedirectCookie(string $path = null)
    {
        /**
         * If no valid path are set, then use the start page defined
         * in the Portal configuration.
         */
        if ($path == null) {
            $path = config('portal.startPage');
        }

        $returnToCookie = Cookie::make(
            $this->returnToCookieKey,
            $path,
            0,
            '/',
            null,
            null,
            true
        );

        Cookie::queue($returnToCookie);
    }

    /**
     * Redirect to logout
     *
     * @queryParam returnTo string success redirection URL
     * @response 303 Redirection
     *
     * @param TokenStorage $tokenStorage
     * @return RedirectResponse
     */
    public function logout(Request $request, TokenStorage $tokenStorage)
    {
        $this->createRedirectCookie($request->input('returnTo', null));

        // Try getting token
        $token = null;
        if ($request->attributes->has('id_token_hint')) {
            $token = $request->attributes->get('id_token_hint');
        }

        // Forget Authentication Token
        $tokenStorage->forget();

        /** @var \Symfony\Component\HttpFoundation\RedirectResponse $redirectResponse */
        $redirectResponse = \Socialite::with('keycloak')->stateless()->logoutRedirect($token);

        return $redirectResponse;
    }

    /**
     * Redirect to the registration
     *
     * @queryParam returnTo string success redirection URL
     * @response 303 Redirection
     *
     * @param Request $request
     * @return RedirectResponse
     */
    public function register(Request $request)
    {
        $this->createRedirectCookie($request->input('returnTo', null));

        /** @var \Symfony\Component\HttpFoundation\RedirectResponse $redirectResponse */
        $redirectResponse = \Socialite::with('keycloak')->stateless()->registerRedirect();

        return $redirectResponse;
    }

    /**
     * Redirect to URL
     *
     * @queryParam returnTo string redirection URL
     * @response 303 Redirection
     *
     * @param Request $request
     * @return RedirectResponse
     */
    public function redirect(Request $request)
    {
        $this->createRedirectCookie($request->input('returnTo', null));

        /** @var \Symfony\Component\HttpFoundation\RedirectResponse $redirectResponse */
        $redirectResponse = \Socialite::with('keycloak')->stateless()->redirect();

        return $redirectResponse;
    }

    /**
     * Authentication callback, called by Keycloak
     *
     * @response 303 Redirection to the Portal's start page
     *
     * @param Request $request
     * @param TokenStorage $tokenStorage
     * @param RefreshTokenStorage $refreshTokenStorage
     * @return \Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector
     */
    public function callback(Request $request, TokenStorage $tokenStorage, RefreshTokenStorage $refreshTokenStorage)
    {
        if ($request->has('code')) {
            /** @var \Laravel\Socialite\Two\User $user */
            $user = \Socialite::with('keycloak')->stateless()->user();

            // Store Refresh Token
            $refreshTokenStorage->store($user->refreshToken);

            // Store Authentication Token
            $tokenStorage->store($user->token);
        }
        /**
         * If no redirect cookie was set, then use the start page from
         * Portal configuration.
         */
        $returnUrl = config('portal.startPage');

        if (Cookie::has($this->returnToCookieKey)) {
            $returnUrl = Cookie::get($this->returnToCookieKey);
            Cookie::queue(Cookie::forget($this->returnToCookieKey));
        }

        return redirect($returnUrl);
    }
}
