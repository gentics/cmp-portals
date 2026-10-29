<?php

namespace Gentics\PortalPhp\Features\GenticsImageStore;

use Gentics\PortalPhp\Features\Mesh\BinaryResponse;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlResponse;
use Gentics\PortalPhp\PortalPhp;
use Gentics\Mesh\Client\Rest\MeshResponse;
use \Illuminate\Http\Request;
use Illuminate\Support\Arr;

/**
 * Class GenticsImageStoreHandler
 * @package Gentics\PortalPhp\Features\GenticsImageStore
 */
class GenticsImageStoreHandler
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp = null;

    /**
     * GenticsImageStoreHandler constructor.
     *
     * @param PortalPhp $portalPhp
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
    }

    /**
     * Handles GenticsImageStore requests and returns binary data if available
     *
     * @param Request $request
     * @return BinaryResponse
     * @throws \Throwable
     */
    public function handle(Request $request)
    {
        $params = $this->getParamsFromRequest($request->route()->parameters(), $request->input());

        $binaryResponse = Mesh::response(
            $this->imageManipulation(
                $request->route('path'),
                null,
                $params
            )
        );

        // If the response is a GraphqlResponse, this is not a valid image store url
        if ($binaryResponse instanceof GraphqlResponse) {
            abort(404);
        }

        return $binaryResponse;
    }

    /**
     * Parses the request query params and converts GenticsImageStore to Mesh Image Manipulation params
     *
     * @param array $routeParams
     * @param array $inputParams
     * @return array
     */
    protected function getParamsFromRequest(array $routeParams, array $inputParams)
    {
        $params = [];

        // abort if params are missing
        if (empty($routeParams['width']) ||
            empty($routeParams['height']) ||
            empty($routeParams['mode']) ||
            empty($routeParams['path'])) {
            abort(404);
        }

        $params['w'] = $routeParams['width'];
        $params['h'] = $routeParams['height'];

        // Resize Mode (smart, prop, force)
        // Supported from Mesh 0.40.0
        $params['resize'] = $routeParams['mode'];

        if ($params['w'] !== 'auto' &&
            $params['h'] !== 'auto' &&
            intval($params['w']) > 0 &&
            intval($params['h']) > 0) {
            $fpx = Arr::get($inputParams, 'fpx');
            $fpy = Arr::get($inputParams, 'fpy');
            $fpz = Arr::get($inputParams, 'fpz');

            // Focal Point params
            if (!empty($fpx)) {
                $params['fpx'] = floatval($fpx);
            }

            if (!empty($fpy)) {
                $params['fpy'] = floatval($fpy);
            }

            if (!empty($fpz)) {
                $params['fpz'] = floatval($fpz);
            }
        }

        if (isset($params['fpz'])) {
            $params['crop'] = 'fp';
        } elseif ($this->isNumeric($routeParams, 'topleft_x') &&
            $this->isNumeric($routeParams, 'topleft_y') &&
            $this->isNumeric($routeParams, 'cropwidth') &&
            $this->isNumeric($routeParams, 'cropheight')) {
            $params['rect'] = implode(',', [
                $routeParams['topleft_x'],
                $routeParams['topleft_y'],
                $routeParams['cropwidth'],
                $routeParams['cropheight'],
            ]);
            $params['crop'] = 'rect';
        }

        return $params;
    }

    /**
     * Check a param if set and numeric
     *
     * @param array $params
     * @param string $name
     * @return bool
     */
    private function isNumeric(array $params, string $name): bool
    {
        return isset($params[$name]) && is_numeric($params[$name]);
    }

    /**
     * Requests Mesh Image Manipulation with the provided path and params
     *
     * @param string $path
     * @param null|string $overrideProjectName
     * @param array|null $params
     * @return MeshResponse
     * @throws \Throwable
     */
    public function imageManipulation(
        string $path,
        ?string $overrideProjectName = null,
        ?array $params = []
    ): MeshResponse {
        return $this->portalPhp->loadBinaryByPath($path, $overrideProjectName, $params, true);
    }
}
