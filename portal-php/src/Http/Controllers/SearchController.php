<?php

namespace Gentics\PortalPhp\Http\Controllers;

use Gentics\PortalPhp\Features\Search\Http\Controllers\SearchController as BaseSearchController;
use Gentics\PortalPhp\Features\Search\SearchHandler;

/**
 * Class SearchController
 * @package Gentics\PortalPhp\Http\Controllers
 * @deprecated 1.1.0 This controller is replaced by the feature's controller.
 */
class SearchController extends BaseSearchController
{
    // Backward compatibility
    // For new implementations please use Gentics\PortalPhp\Features\Search\Http\Controllers\SearchController!

    /**
     * @var SearchHandler|null
     */
    protected $searchHandler = null;

    /**
     * SearchController constructor.
     */
    public function __construct(SearchHandler $handler)
    {
        parent::__construct($handler);
        $this->searchHandler = $handler;
    }
}
