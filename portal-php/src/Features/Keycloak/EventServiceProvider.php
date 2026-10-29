<?php
namespace Gentics\PortalPhp\Features\Keycloak;

use Illuminate\Foundation\Support\Providers\EventServiceProvider as ServiceProvider;

class EventServiceProvider extends ServiceProvider
{
    protected $listen = [
        \SocialiteProviders\Manager\SocialiteWasCalled::class => [
            'Gentics\\PortalPhp\\Features\\Keycloak\\Socialite\\KeycloakExtendSocialite@handle',
        ],
    ];
}
