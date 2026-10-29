<?php
namespace Gentics\PortalPhp\Features\Mesh\GraphqlRequests;

use Gentics\PortalPhp\Features\Mesh\GraphqlRequest;
use Gentics\PortalPhp\Features\Mesh\Mesh;

/**
 * Class LoadSitemap
 * {@inheritDoc}
 * @method $this|Search setPath(string $path)
 * @method $this|Search setBranchName(string $branchName)
 * @method mixed getPath()
 * @method mixed getBranchName()
 */
class LoadSitemap extends GraphqlRequest
{

    /** List of Accessible Properties */
    protected $properties = [
        'branchName',
        'path',
    ];

    /** @var string Override the branchName variable to skip auto-detect. */
    protected $branchName;
    /** @var string Set request path */
    protected $path;

    public function fetch()
    {
        $parameters = [];
        $branchName = $this->finalBranchName();

        if (!empty($branchName)) {
            $parameters['branch'] = $branchName;
        }
        $parameters['version'] = 'published';

        $this->appendQueryParameters($parameters);

        return parent::fetch();
    }

    /**
     * Get branch name
     * @throws \Throwable
     */
    protected function finalBranchName()
    {
        $branchName = $this->branchName;

        if (empty($branchName)) {
            if (empty($this->path)) {
                $this->path = "/";
            }
            $path = Mesh::normalizePath($this->path);
            $branchName = $this->portalPhp->findBranchNameFromRequest($path);
        }

        return $branchName;
    }
}
