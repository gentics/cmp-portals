<?php

namespace Gentics\PortalPhp\Features\Helpers;

use Illuminate\Support\Arr;

class BreadcrumbHelper extends BaseHelper
{
    /**
     * The breadcrumbs returned by Mesh contain the project root node and a node for the current page.
     * Those will be skipped by this helper.
     *
     * @param array $elements Breadcrumb elements
     * @return array Breadcrumb elements without root node and current page
     */
    public function eachBreadcrumbHelper(array $elements): array
    {
        if (count($elements) < 2) {
            return [];
        }

        array_pop($elements);
        array_shift($elements);

        return $elements;
    }

    /**
     * Returns true if breadcrumb object has set startpage. Otherwise returns false
     *
     * @param array $breadcrumb Breadcrumb data
     * @return bool
     */
    public function hasDirectStartpageHelper(array $breadcrumb): bool
    {
        return Arr::get($breadcrumb, "node.fields.startpage", null) !== null
                || Arr::get($breadcrumb, "node.fields.startpageurl", null) !== null;
    }

    /**
     * Returns true if current page is start page of parent folder. Otherwise returns false
     *
     * @param array $data
     * @return bool
     */
    public function isCurrentPageStartpageHelper(array $data): bool
    {
        $breadcrumbs = Arr::get($data, 'node.breadcrumb', []);
        $lastFolderIdx = count($breadcrumbs) - 2;

        if ($lastFolderIdx < 1) {
            return false;
        }

        $currentPage = Arr::get($data, "node.uuid", null);
        $startPage = Arr::get($breadcrumbs[$lastFolderIdx], "node.fields.startpage.uuid", null);

        return !($currentPage == null || !$currentPage !== $startPage);
    }
}
