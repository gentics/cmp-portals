<?php

namespace Gentics\PortalPhp\Features\PortalVersion\Http\Controllers;

use Gentics\PortalPhp\Http\Controllers\BaseController;
use Gentics\PortalPhp\PortalPhp;

use Illuminate\Http\Request;

class PortalVersionController extends BaseController
{
    /**
     * Return Gentics Portal | php version
     *
     * @response { 'productName': 'Portal | PHP', 'productVersion': '3.5.0' }
     *
     * @return \Psr\Http\Message\ResponseInterface
     * @throws \Throwable
     */
    public function getVersion()
    {
        return response()->json([
            'productName' => $this->portalPhp->getProductName(),
            'productVersion' => $this->portalPhp->getProductVersion(),
        ]);
    }
    /**
     * Preview Gentics Portal | php version
     *
     * @response { 'productName': 'Portal | PHP', 'productVersion': '3.5.0' }
     *
     * @return \Psr\Http\Message\ResponseInterface
     * @throws \Throwable
     */
    public function getVersionPreview()
    {
        return $this->getVersion();
    }
}
