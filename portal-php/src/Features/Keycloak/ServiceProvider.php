<?php

namespace Gentics\PortalPhp\Features\Keycloak;

use Gentics\PortalPhp\Features\Keycloak\Storage\CookieRefreshTokenStorage;
use Illuminate\Contracts\Http\Kernel;
use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Illuminate\Contracts\Cache\Repository;
use Illuminate\Cookie\Middleware\AddQueuedCookiesToResponse;
use Illuminate\Session\Middleware\StartSession;
use Illuminate\Contracts\Debug\ExceptionHandler;
use Gentics\PortalPhp\Features\ErrorHandler\CollisionExceptionHandler;
use Gentics\PortalPhp\Features\ErrorHandler\Handler;

use Gentics\PortalPhp\Features\Keycloak\Adapter\UserAuthenticator;
use Gentics\PortalPhp\Features\Keycloak\Contract\Authenticator;
use Gentics\PortalPhp\Features\Keycloak\Contract\RefreshTokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Contract\TokenStorage;
use Gentics\PortalPhp\Features\Keycloak\Storage\CookieTokenStorage;
use Illuminate\Cookie\CookieJar;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;

use Lcobucci\Clock\Clock;
use Lcobucci\Clock\SystemClock;

use Lcobucci\JWT\Decoder;
use Lcobucci\JWT\Signer;
use Lcobucci\JWT\Signer\Rsa;
use Lcobucci\JWT\Signer\Ecdsa;
use Lcobucci\JWT\Token\Parser;
use Lcobucci\JWT\Encoding\JoseEncoder;
use Lcobucci\JWT\Validation\Validator;

use DateTimeImmutable;
use ReflectionClass;

class ServiceProvider extends ServiceProviderIlluminate
{
    protected $algo = 'RS256';

    protected $signers = [
        // TODO: Implement other signers when required
        // 'HS256' => \Lcobucci\JWT\Signer\Hmac\Sha256::class,
        // 'HS384' => \Lcobucci\JWT\Signer\Hmac\Sha384::class,
        // 'HS512' => \Lcobucci\JWT\Signer\Hmac\Sha512::class,
        'RS256' => \Lcobucci\JWT\Signer\Rsa\Sha256::class,
        'RS384' => \Lcobucci\JWT\Signer\Rsa\Sha384::class,
        'RS512' => \Lcobucci\JWT\Signer\Rsa\Sha512::class,
        // 'ES256' => \Lcobucci\JWT\Signer\Ecdsa\Sha256::class,
        // 'ES384' => \Lcobucci\JWT\Signer\Ecdsa\Sha384::class,
        // 'ES512' => \Lcobucci\JWT\Signer\Ecdsa\Sha512::class,
    ];

    /**
     * Bootstrap any application services.
     *
     * @return void
     */
    public function boot()
    {
        // Set Portal Configuration as SocialiteProviders::Manager configuration values
        \Config::set('services.keycloak', config('portal.authentication.keycloak', null));

        $this->publishes([
            __DIR__ . '/../../../resources/graphql/loadUser.graphql' => resource_path('graphql/loadUser.graphql'),
        ], 'gpphp-graphql');

        $this->loadRoutesFrom(realpath(__DIR__ . '/Http/routes.php'));

        $algoConfig = config('portal.authentication.keycloak.algorithm');

        if ($algoConfig) {
            $this->algo = $algoConfig;
        }

        // Add required middlewares automatically if not existing
        // This behavior can be disabled with the disableMiddlewareAutoload option
        if (! config('portal.authentication.keycloak.disableMiddlewareAutoload', false)) {
            if (! $this->app[Kernel::class]->hasMiddleware('App\Http\Middleware\Authenticate')) {
                $this->app[Kernel::class]
                    ->pushMiddleware(Http\Middleware\Authenticate::class);

                $this->app[Kernel::class]->
                    prependToMiddlewarePriority(Http\Middleware\Authenticate::class);
            }

            if (! $this->app[Kernel::class]->hasMiddleware(AddQueuedCookiesToResponse::class)) {
                $this->app[Kernel::class]
                    ->pushMiddleware(AddQueuedCookiesToResponse::class);
            }

            if (! $this->app[Kernel::class]->hasMiddleware(StartSession::class)) {
                $this->app[Kernel::class]
                    ->pushMiddleware(StartSession::class);

                $this->app[Kernel::class]->
                    prependToMiddlewarePriority(StartSession::class);
            }
        }

        // Register Authentication error handling on the top of the stack
        $exceptionHandler = $this->app[ExceptionHandler::class];

        if ($exceptionHandler instanceof CollisionExceptionHandler) {
            $exceptionHandler = $exceptionHandler->exceptionHandler();
        }
        if ($exceptionHandler instanceof Handler) {
            $exceptionHandler->prependErrorHandler(AuthenticationErrorHandler::class);
        }

        \Auth::extend('keycloak', function () {
            return new KeycloakGuard();
        });
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        $this->app->register(EventServiceProvider::class);

        $this->app->singleton(Decoder::class, function () {
            return new JoseEncoder();
        });

        $this->app->singleton(Parser::class, function () {
            $decoder = app()->make(Decoder::class);
            return new Parser($decoder);
        });

        $this->app->singleton(Signer::class, function () {
            return $this->getSigner();
        });

        $this->app->singleton(Clock::class, function () {
            return new SystemClock((new DateTimeImmutable())->getTimezone());
        });

        $this->app->bind(Validator::class, function () {
            return new Validator();
        });

        /**
         * CookieRefreshTokenStorage fetch Refresh token from Cookie.
         * Uses Cookie for RefreshTokenStorage
         */
        $this->app->singleton(RefreshTokenStorage::class, function ($app) {
            return $app->make(CookieRefreshTokenStorage::class);
        });

        /**
         * CookieTokenStorage fetch Authentication token from Cookie.
         * Uses Cookie backend for RefreshTokenStorage
         */
        $this->app->singleton(TokenStorage::class, function ($app) {
            return new CookieTokenStorage($app[Parser::class]);
        });

        /**
         * You can replace the default UserAuthenticator by registering a new Authenticator based on
         * Gentics\PortalPhp\Features\Keycloak\Contract\Authenticator
         *
         * Then you need to register the new Authenticator to override the default one in the project:
         * App\Providers\AuthServiceProvider.php
         *
         * You can also replace the provided User model or extend.
         */
        $this->app->singleton(Authenticator::class, function () {
            return new UserAuthenticator();
        });
    }

    /**
     * Get the signer instance.
     *
     * @throws PortalPhpException
     *
     * @return \Lcobucci\JWT\Signer
     */
    protected function getSigner()
    {
        if (! array_key_exists($this->algo, $this->signers)) {
            throw new PortalPhpException('The given algorithm could not be found');
        }

        $signer = $this->signers[$this->algo];

        if (is_subclass_of($signer, Ecdsa::class)) {
            return $signer::create();
        }

        return new $signer();
    }

    /**
     * Determine if the algorithm is asymmetric, and thus
     * requires a public/private key combo.
     *
     * @return bool
     */
    protected function isAsymmetric()
    {
        $reflect = new ReflectionClass($this->signer);

        return $reflect->isSubclassOf(Rsa::class) || $reflect->isSubclassOf(Ecdsa::class);
    }
}
