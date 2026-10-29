<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\Sitemap\SitemapGenerator;
use Gentics\PortalPhp\Tests\TestCase;

class SitemapTest extends TestCase
{
    /**
     * Test Sitemap Generator by predefined Mesh response
     */
    public function testSitemapGenerator()
    {
        $generator = new SitemapGenerator();

        $sitemapTestJson = $this->loadResponseFile("/loadAllForSitemap.json");
        $sitemapTestData = json_decode($sitemapTestJson, true)['data'];

        $sitemap = $generator->generateSitemap($sitemapTestData);

        // Compare sitemap
        $this->assertXmlStringEqualsXmlFile(
            resource_path("responses/sitemap.xml"),
            $sitemap
        );
    }

    /**
     * Test Sitemap Generator by predefined Mesh response
     */
    public function testSitemapGeneratorMultilang()
    {
        config([
            "portal.sitemap.nodeLanguages" => ['en', 'de'],
            "portal.sitemap.multiLanguage" => true
        ]);
        $generator = new SitemapGenerator();

        $sitemapTestJson = $this->loadResponseFile("/loadAllForSitemapMultilang.json");
        $sitemapTestData = json_decode($sitemapTestJson, true)['data'];

        $sitemap = $generator->generateSitemap($sitemapTestData);

        // Compare sitemap
        $this->assertXmlStringEqualsXmlFile(
            resource_path("responses/sitemapMultilang.xml"),
            $sitemap
        );
    }
}
