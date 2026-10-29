<?php

namespace Gentics\PortalPhp\Features\HttpCache;

use FOS\HttpCache\SymfonyCache\UserContextListener as FOSUserContextListener;
use FOS\HttpCache\SymfonyCache\CacheEvent;

/**
 * {@inheritdoc}
 */
class UserContextListener extends FOSUserContextListener
{
    public function preHandle(CacheEvent $event): void
    {
        parent::preHandle($event);
    }
}
