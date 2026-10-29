<?php

namespace Gentics\PortalPhp\Features\Comments\Http\Controllers;

use Illuminate\Routing\Controller as BaseController;
use Gentics\PortalPhp\Features\Comments\CommentsHandler;

/**
 * Class CommentsController
 * @package Gentics\PortalPhp\Features\Comments\Http\Controllers
 */
class CommentsController extends BaseController
{
    /**
     * @var CommentsHandler|null
     */
    protected $handler = null;

    /**
     * CommentsController constructor.
     * @param CommentsHandler $handler
     */
    public function __construct(CommentsHandler $handler)
    {
        $this->handler = $handler;
    }

    /**
     * A proxy to the Gentics Mesh Comment plugin
     * @group Comment plugin
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
