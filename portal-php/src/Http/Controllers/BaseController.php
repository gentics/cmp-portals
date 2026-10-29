<?php

namespace Gentics\PortalPhp\Http\Controllers;

use Gentics\PortalPhp\PortalPhp;
use Illuminate\Routing\Controller;
use Gentics\PortalPhp\RenderMode;

/**
 * Class BaseController
 * @package Gentics\PortalPhp\Http\Controllers
 */
abstract class BaseController extends Controller
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp    = null;

    /**
     * @var RenderMode|null
     */
    protected $renderMode   = null;

    /**
     * BaseController constructor.
     *
     * @param PortalPhp $portalPhp
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp  = $portalPhp;
        $this->renderMode = $this->portalPhp->renderMode();
    }
}
