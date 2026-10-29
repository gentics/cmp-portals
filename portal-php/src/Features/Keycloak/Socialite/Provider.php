<?php

namespace Gentics\PortalPhp\Features\Keycloak\Socialite;

use Gentics\PortalPhp\Features\Keycloak\Util;

use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Arr;

use Laravel\Socialite\Two\InvalidStateException;
use Laravel\Socialite\Two\ProviderInterface;

use SocialiteProviders\Manager\OAuth2\AbstractProvider;
use SocialiteProviders\Manager\OAuth2\User;

use Lcobucci\JWT\Token\Parser;
use Exception;

class Provider extends AbstractProvider implements ProviderInterface
{
    /**
     * Unique Provider Identifier.
     */
    const IDENTIFIER = 'KEYCLOAK';

    /**
     * Redirect headers for cache busting
     */
    const REDIRECT_HEADERS = [
        'Cache-Control' => 'no-cache, no-store, must-revalidate, max-age=0'
    ];

    /**
     * Redirect HTTP Code
     */
    const REDIRECT_CODE = Response::HTTP_FOUND;

    /**
     * The scopes being requested.
     *
     * @var array
     */
    protected $scopes = [
        'openid',
        'email',
        'profile',
    ];

    /**
     * The separating character for the requested scopes.
     *
     * @var string
     */
    protected $scopeSeparator = ' ';

    /**
     * JWT Token parser instance.
     *
     * @var \Lcobucci\JWT\Parser
     */
    protected $parser;

    /**
     * @var string
     */
    private $baseUrl;

    /**
     * Create a new provider instance.
     *
     * @param  \Illuminate\Http\Request $request
     * @param  string                   $clientId
     * @param  string                   $clientSecret
     * @param  string                   $redirectUrl
     */
    public function __construct(
        Request $request,
        string $clientId,
        string $clientSecret,
        string $redirectUrl
    ) {
        parent::__construct($request, $clientId, $clientSecret, $redirectUrl);

        $this->baseUrl = $this->getFromConfig('authUrl') .
            '/realms/' .
            $this->getFromConfig('realm') .
            '/protocol/openid-connect';

        $this->parser = app()->make(Parser::class);
    }

    /**
     * {@inheritdoc}
     * @throws Exception
     */
    public function user()
    {
        if ($this->hasInvalidState()) {
            throw new InvalidStateException;
        }

        $response = $this->getAccessTokenResponse($this->getCode());

        if (!empty($response['error'])) {
            throw new Exception($response['error']);
        }

        $token = $response['id_token'];

        $user = $this->mapUserToObject($this->getUserByToken($token));

        return $user->setToken($token)
            ->setRefreshToken(Arr::get($response, 'refresh_token'))
            ->setExpiresIn(Arr::get($response, 'expires_in'));
    }

    /**
     * @return \Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector
     */
    public function logoutRedirect($token = null)
    {
        // for Keycloak <18.0.0
        if (false) {
            return redirect(
                $this->getLogoutUrl() . '?redirect_uri=' . $this->redirectUrl,
                static::REDIRECT_CODE,
                static::REDIRECT_HEADERS
            );
        } else {
            if ($token == null) {
                $redirectUri = $this->getLogoutUrl();
            } else {
                $redirectUri = $this->getLogoutUrl() .'?post_logout_redirect_uri='
                    . $this->redirectUrl . '&id_token_hint=' . $token;
            }

            return redirect(
                $redirectUri,
                static::REDIRECT_CODE,
                static::REDIRECT_HEADERS
            );
        }
    }

    /**
     * @return \Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector
     */
    public function registerRedirect()
    {
        $query = http_build_query(
            $this->getRegistrationFields(),
            '',
            '&',
            $this->encodingType
        );

        return redirect(
            $this->getRegisterUrl() . '?' . $query,
            static::REDIRECT_CODE,
            static::REDIRECT_HEADERS
        );
    }

    /**
     * @return \Illuminate\Http\RedirectResponse|\Illuminate\Routing\Redirector
     */
    public function redirect()
    {
        $redirect = parent::redirect();
        $redirect->setStatusCode(static::REDIRECT_CODE);
        $redirect->withHeaders(static::REDIRECT_HEADERS);
        return $redirect;
    }

    /**
     * {@inheritdoc}
     */
    protected function mapUserToObject(array $user)
    {
        return (new User)->setRaw($user)->map([
            'id' => $user['sub'],
            'sub' => $user['sub'],
            'iss' => $user['iss'],
            'nickname' => $user['name'],
            'name' => $user['name'],
            'email' => $user['email'],
        ]);
    }

    /**
     * {@inheritdoc}
     */
    protected function getUserByToken($token)
    {
        $plainToken = $this->parser->parse($token);
        $claims = Util::getClaims($plainToken);

        return [
            'sub' => $claims->get('sub'),
            'iss' => $claims->get('iss'),
            'name' => $claims->get('name'),
            'email' => $claims->get('email'),
        ];
    }

    /**
     * {@inheritdoc}
     */
    protected function getTokenFields($code)
    {
        return [
            'client_id' => $this->clientId,
            'client_secret' => $this->clientSecret,
            'code' => $code,
            'redirect_uri' => $this->redirectUrl,
            'grant_type' => 'authorization_code',
        ];
    }

    /**
     * {@inheritdoc}
     */
    public function getRefreshTokenFields($refreshToken)
    {
        return [
            'client_id'     => $this->clientId,
            'client_secret' => $this->clientSecret,
            'grant_type'    => 'refresh_token',
            'refresh_token' => $refreshToken,
        ];
    }

    /**
     * {@inheritdoc}
     */
    public function getRegistrationFields()
    {
        return [
            'client_id'     => $this->clientId,
            'response_type' => 'code',
            'scope'         => implode(' ', $this->scopes),
            'redirect_uri'  => $this->redirectUrl,
        ];
    }

    /**
     * {@inheritdoc}
     */
    public function getRefreshTokenResponse($refreshToken)
    {
        $response = $this->getHttpClient()->post($this->getTokenUrl(), [
            'headers'     => ['Accept' => 'application/json'],
            'form_params' => $this->getRefreshTokenFields($refreshToken),
        ]);
        return json_decode($response->getBody(), true);
    }

    /**
     * Fetches JSON Web Key Sets
     * @return mixed
     */
    public function getJwks()
    {
        $response = $this->getHttpClient()->get($this->getJwksUrl(), [
            'headers'     => ['Accept' => 'application/json'],
        ]);
        return json_decode($response->getBody(), true);
    }

    /**
     * @param string $arrayKey
     * @return mixed
     */
    protected function getFromConfig($arrayKey)
    {
        return app()['config']['services.keycloak'][$arrayKey] ?? null;
    }

    /**
     * {@inheritdoc}
     */
    protected function getAuthUrl($state)
    {
        return $this->buildAuthUrlFromBase($this->baseUrl . '/auth', $state);
    }

    /**
     * {@inheritdoc}
     */
    protected function getTokenUrl()
    {
        return $this->baseUrl . '/token';
    }

    /**
     *
     * @return string
     */
    protected function getJwksUrl()
    {
        return $this->baseUrl . '/certs';
    }

    /**
     *
     * @return string
     */
    protected function getLogoutUrl()
    {
        return $this->baseUrl . '/logout';
    }

    /**
     *
     * @return string
     */
    protected function getRegisterUrl()
    {
        return $this->baseUrl . '/registrations';
    }
}
