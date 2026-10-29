<?php
namespace Gentics\PortalPhp\Features\Likes;

use Gentics\PortalPhp\Features\Proxy\Filter\AddAuthorizationHeader;
use Gentics\PortalPhp\Features\Proxy\Filter\RemoveRoutePath;
use Gentics\PortalPhp\PortalPhp;

/**
 * Class LikesHandler
 * @package Gentics\PortalPhp\Features\Likes
 */
class LikesHandler
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp    = null;

    /**
     * LikesHandler constructor.
     *
     * @param PortalPhp $portalPhp
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
    }

    /**
     * @return mixed
     * @throws \Throwable
     */
    public function handle()
    {
        $projectName = $this->portalPhp->config('projectName');
        
        // Create client without adminClient fallback
        $mesh = $this->portalPhp->getMeshClient(false);

        /**
         * Proxy requests directly to the Likes Plugin
         * with passing the current client token (or null, if not logged in).
         */
        return $mesh->proxy('/' . $projectName . '/plugins/like/', [
            new RemoveRoutePath(),
            new AddAuthorizationHeader($this->portalPhp->getClientToken())
        ]);
    }
}
