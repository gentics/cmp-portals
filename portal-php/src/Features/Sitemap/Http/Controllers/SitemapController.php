<?php

namespace Gentics\PortalPhp\Features\Sitemap\Http\Controllers;

use Gentics\PortalPhp\Features\Sitemap\SitemapHandler;
use Illuminate\Routing\Controller as BaseController;

/**
 * Class SitemapController
 * @package Gentics\PortalPhp\Features\Sitemap\Http\Controllers
 */
class SitemapController extends BaseController
{
    /**
     * @var SitemapHandler|null
     */
    protected $sitemap = null;

    /**
     * SitemapController constructor.
     *
     * @param SitemapHandler $sitemap
     */
    public function __construct(SitemapHandler $sitemap)
    {
        $this->sitemap = $sitemap;
    }

    /**
     * Get the sitemap of the portal, in XML format
     *
     * @response 200 <<binary>> XML file
     *
     * @return string
     * @throws \Throwable
     */
    public function index()
    {
        return $this->sitemap->handle();
    }
}
