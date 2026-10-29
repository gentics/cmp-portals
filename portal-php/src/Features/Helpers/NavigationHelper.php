<?php

namespace Gentics\PortalPhp\Features\Helpers;

use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Arr;
use Illuminate\Support\Collection;

class NavigationHelper extends BaseHelper
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp = null;

    /**
     * NavigationHelper constructor.
     * @param PortalPhp $portalPhp
     * @throws \Throwable
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
        parent::__construct();
    }

    /**
     * @param array $element
     * @return bool
     */
    public function isNotHiddenHelper(array $element): bool
    {
        return !$this->isHidden($element);
    }

    /**
     * @param array $element
     * @return bool
     * @throws \Throwable
     * @see NavigationHelper::isFolder() Helper function of this method
     */
    public function isFolderHelper(array $element): bool
    {
        return $this->isFolder($element);
    }

    /**
     * @param array $element
     * @return bool
     * @throws \Throwable
     * @see NavigationHelper::hasUnhiddenFolderChildren() Helper function of this method
     */
    public function hasUnhiddenFolderChildrenHelper(array $element): bool
    {
        return $this->hasUnhiddenFolderChildren($element);
    }

    /**
     * If element has object.startpage configured, returns url of that start page. Otherwise returns #!
     *
     * @param array $element
     * @return string
     */
    public function getUrlHelper(array $element): string
    {
        $startpage = null;
        $startpageurl = null;

        if (Arr::has($element, 'fields')) {
            $startpage = Arr::get($element, 'fields.startpage', null);
            $startpageurl = Arr::get($element, 'fields.startpageurl', null);
        } else {
            $startpage = Arr::get($element, 'node.fields.startpage', null);
            $startpageurl = Arr::get($element, 'node.fields.startpageurl', null);
        }

        if ($startpage != null) {
            return Arr::get($startpage, 'path');
        }
        if ($startpageurl != null) {
            return $startpageurl;
        }

        return '#!';
    }

    /**
     * @see NavigationHelper::isInActivePath() Helper function of this method
     * @param array $element
     * @param array $node
     * @return bool
     */
    public function isInActivePathHelper(array $element, array $node): bool
    {
        return $this->isInActivePath($element, $node);
    }

    /**
     * @see NavigationHelper::folderHasDirectStartpage() Helper function of this method
     * @param array $element
     * @return bool
     */
    public function folderHasDirectStartpageHelper(array $element): bool
    {
        return $this->folderHasDirectStartpage($element);
    }

    /**
     * Sorts navigation by navsortorder field
     *
     * @param array $elements
     * @return mixed
     */
    public function sortByNavSortOrderHelper(array $elements): array
    {
        usort($elements, function ($a, $b) {
            return (intval($a['fields']['navsortorder'] ?? 0)) <=> (intval($b['fields']['navsortorder'] ?? 0));
        });

        return $elements;
    }

    /**
     * Return each page which has start page and sub-elements
     *
     * @param array $elements
     * @return array
     */
    public function eachWithStartpageHelper(array $elements): array
    {
        return (new Collection($elements))
            ->filter(function ($element) {
                return $this->unhiddenFilter($element);
            })
            ->filter(function ($element) {
                return $this->startpageFilter($element);
            })
            ->toArray();
    }

    /**
     * Return each page with either start page or sub-elements
     *
     * @param array $elements
     * @return array
     */
    public function eachWithStartpageOrSubElementsHelper(array $elements): array
    {
        return (new Collection($elements))
            ->filter(function ($element) {
                return $this->startpageOrSubelementsFilter($element);
            })
            ->toArray();
    }

    /**
     * @param array $element
     * @return bool
     */
    protected function unhiddenFilter(array $element): bool
    {
        $fields = Arr::get($element, 'fields');
        if (empty($fields)) {
            return false;
        }

        if (Arr::get($fields, 'navhidden', false) !== true &&
            !empty(Arr::get($element, 'children.elements'))) {
            return true;
        }

        return false;
    }

    /**
     * @param array $element
     * @return bool
     */
    protected function startpageFilter(array $element): bool
    {
        return $this->hasStartpage($element);
    }

    /**
     * @param array $element
     * @return bool
     */
    protected function startpageOrSubelementsFilter(array $element): bool
    {
        return ($this->startpageFilter($element) || $this->unhiddenFilter($element));
    }

    /**
     * Returns true if element contains startpage somewhere in subtree
     *
     * @param array $element
     * @return bool
     */
    protected function hasStartpage(array $element): bool
    {
        if ($this->folderHasDirectStartpage($element)) {
            return true;
        }

        $subElements = Arr::get($element, 'children.elements');
        if (!empty($subElements)) {
            foreach ($subElements as $child) {
                if ($this->hasStartpage($child)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @param array $element
     * @return bool
     * @throws \Throwable
     */
    protected function hasUnhiddenFolderChildren(array $element): bool
    {
        $elements = Arr::get($element, 'children.elements');
        if (!empty($elements)) {
            foreach ($elements as $child) {
                if ($this->isFolder($child) && !$this->isHidden($child) && $this->folderHasDirectStartpage($child)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if current object is in breadcrumbs. Returns true if element is in breadcrumbs
     * of the folder or is in folder. Otherwise returns false.
     *
     * @param array $element
     * @param array $node
     * @return bool
     */
    protected function isInActivePath(array $element, array $node): bool
    {
        if ($this->elementInFolder($element, $node)) {
            return true;
        }

        $elements = Arr::get($element, 'children.elements', []);

        foreach ($elements as $child) {
            if ($this->elementInBreadcrumbs($child, $node) || $this->elementInFolder($child, $node)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param array $element
     * @param array $node
     * @return bool
     */
    protected function elementInBreadcrumbs(array $element, array $node): bool
    {
        $breadcrumbs = Arr::get($node, 'breadcrumb', []);
        $elementUuid = Arr::get($element, 'uuid');

        foreach ($breadcrumbs as $breadcrumb) {
            if ($elementUuid === Arr::get($breadcrumb, 'uuid')) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param array $element
     * @param array $node
     * @return bool
     */
    protected function elementInFolder(array $element, array $node): bool
    {
        return Arr::get($element, 'uuid') ===  Arr::get($node, 'uuid');
    }

    /**
     * Returns true if folder has a start page
     *
     * @param array $element
     * @return bool
     */
    protected function folderHasDirectStartpage(array $element): bool
    {
        return Arr::get($element, 'fields.startpage', null) !== null
                || Arr::get($element, 'fields.startpageurl', null) !== null;
    }

    /**
     * @param array $element
     * @return bool
     * @throws \Throwable
     */
    protected function isFolder(array $element): bool
    {
        return Arr::get($element, 'schema.name') === $this->portalPhp->config('schemas.folder');
    }

    /**
     * @param array $element
     * @return bool
     */
    protected function isHidden(array $element): bool
    {
        return (bool) Arr::get($element, 'fields.navhidden', false);
    }
}
