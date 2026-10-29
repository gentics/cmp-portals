<?php

namespace Gentics\PortalPhp\Features\Likes\Http\Controllers;

use Illuminate\Routing\Controller as BaseController;
use Gentics\PortalPhp\Features\Likes\LikesHandler;

/**
 * Class LikesController
 * @package Gentics\PortalPhp\Features\Likes\Http\Controllers
 */
class LikesController extends BaseController
{
    /**
     * @var LikesHandler|null
     */
    protected $handler = null;

    /**
     * LikesController constructor.
     * @param LikesHandler $handler
     */
    public function __construct(LikesHandler $handler)
    {
        $this->handler = $handler;
    }

    /**
     * A proxy to the Gentics Mesh Like plugin
     * @group Like plugin
     * @urlParam any string Any request
     * @response 200 {}
     *
     * @return mixed
     * @throws \Throwable
     */
    public function index()
    {
        return $this->handler->handle();
    }
}
