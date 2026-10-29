<?php
namespace Gentics\PortalPhp\Features\Sitemap;

use Carbon\Carbon;
use Spatie\Sitemap\Sitemap;
use Spatie\Sitemap\Tags\Url;
use Illuminate\Support\Arr;

/**
 * Class SitemapGenerator
 * @package Gentics\PortalPhp\Features\Sitemap
 */
class SitemapGenerator
{
    /**
     * @param array $urlTree
     * @return string
     * @throws \Exception
     */
    public function generateSitemap(array $urlTree)
    {
        $sitemap = Sitemap::create();
        $rootElements = Arr::get($urlTree, 'project.rootNode.children');
        $multiLanguage = config('portal.sitemap.multiLanguage', false) === true;
        $this->addChildren($rootElements, $sitemap, $multiLanguage);
        return $sitemap->render();
    }

    /**
     * @param array|null $children
     * @param Sitemap $sitemap
     * @param bool $multiLanguage
     * @throws \Exception
     */
    private function addChildren(?array $children, Sitemap $sitemap, bool $multiLanguage)
    {
        if ($children == null || Arr::get($children, 'elements') == null) {
            return;
        }

        $elements = Arr::get($children, 'elements');
        foreach ($elements as $element) {
            $this->addToSitemap($element, $sitemap, $multiLanguage);
            $this->addChildren(Arr::get($element, 'children'), $sitemap, $multiLanguage);
        }
    }

    /**
     * @param array $element
     * @param Sitemap $sitemap
     * @param bool $multiLanguage
     * @throws \Exception
     */
    private function addToSitemap(array $element, Sitemap $sitemap, bool $multiLanguage)
    {
        $allowedSchemas = [
            config('portal.schemas.content'),
            config('portal.schemas.binarycontent')
        ];

        if (!in_array(Arr::get($element, 'schema.name'), $allowedSchemas)
            || Arr::get($element, 'path') == null) {
            return;
        }

        /**
         * The sitemap tries to use the noindex and editdate fields to populate entries if possible
         */
        $path = Arr::get($element, 'path');
        $noindex = Arr::get($element, 'fields.noindex', false);
        $editdate = new \DateTime(Arr::get($element, 'fields.editdate', Carbon::now()));

        if ($noindex === true) {
            return;
        }

        /**
         * Add multiple language alternatives
         */
        if ($multiLanguage && Arr::get($element, 'languages') != null &&
            count(Arr::get($element, 'languages')) > 1
        ) {
            $languages = Arr::get($element, 'languages');

            foreach ($languages as $language) {
                $langPath = Arr::get($language, 'path');
                $langLanguage = Arr::get($language, 'language');
                $langNoindex = Arr::get($language, 'fields.noindex', false);
                $langEditdate = new \DateTime(Arr::get($language, 'fields.editdate', Carbon::now()));

                if ($langNoindex === true) {
                    continue;
                }

                $sitemapUrl = Url::create($langPath)->setLastModificationDate($langEditdate);

                foreach ($languages as $alternate) {
                    $altPath = Arr::get($alternate, 'path');
                    $altLanguage = Arr::get($alternate, 'language');
                    $altNoindex = Arr::get($alternate, 'fields.noindex', false);

                    if ($altNoindex === true) {
                        continue;
                    }

                    $sitemapUrl->addAlternate($altPath, $altLanguage);
                }

                $sitemap->add($sitemapUrl);
            }
            /**
             * Single language case
             */
        } else {
            $sitemapUrl = Url::create($path)->setLastModificationDate($editdate);
            $sitemap->add($sitemapUrl);
        }
    }
}
