<?php
namespace Gentics\PortalPhp\Features\Comments;

use Gentics\PortalPhp\Features\Proxy\Filter\AddAuthorizationHeader;
use Gentics\PortalPhp\Features\Proxy\Filter\RemoveRoutePath;
use Gentics\PortalPhp\PortalPhp;

/**
 * Class CommentsHandler
 * @package Gentics\PortalPhp\Features\Comments
 */
class CommentsHandler
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp    = null;

    /**
     * CommentsHandler constructor.
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
         * Proxy requests directly to the Comments Plugin
         * with passing the current client token (or null, if not logged in).
         */
        return $mesh->proxy('/' . $projectName . '/plugins/comment/', [
            new RemoveRoutePath(),
            new AddAuthorizationHeader($this->portalPhp->getClientToken())
        ]);
    }
}
