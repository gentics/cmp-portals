<?php
namespace Gentics\PortalPhp\Features\Sitemap;

use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadSitemap;

/**
 * Class SitemapHandler
 * @package Gentics\PortalPhp\Features\Sitemap
 */
class SitemapHandler
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp        = null;

    /**
     * @var SitemapGenerator|null
     */
    protected $generator        = null;

    /**
     * @var array
     */
    protected $nodeLanguages    = ['en'];

    /**
     * SitemapHandler constructor.
     *
     * @param PortalPhp $portalPhp
     * @param SitemapGenerator $generator
     */
    public function __construct(PortalPhp $portalPhp, SitemapGenerator $generator)
    {
        $this->portalPhp = $portalPhp;
        $this->generator = $generator;
        $this->nodeLanguages = config('portal.sitemap.nodeLanguages', ['en']);
    }

    /**
     * @return string
     * @throws \Throwable
     */
    public function handle()
    {
        $urlTree = $this->loadAllForSitemap();
        return $this->generator->generateSitemap($urlTree);
    }

    /**
     * @return mixed
     * @throws \Throwable
     */
    private function loadAllForSitemap()
    {
        $response = Mesh::request(LoadSitemap::class)
            ->setTemplateName('loadAllForSitemap')
            ->setVariables([
                'lang' => $this->nodeLanguages
            ])
            ->fetch();
        $responseArray = $response->toArray();
        $responseData = $responseArray['data'];

        return $responseData;
    }
}
