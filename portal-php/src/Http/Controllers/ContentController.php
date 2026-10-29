<?php

namespace Gentics\PortalPhp\Http\Controllers;

use Gentics\PortalPhp\Features\GenticsImageStore\GenticsImageStoreHandler;
use Gentics\PortalPhp\Features\Mesh\BinaryResponse;
use Gentics\PortalPhp\Features\Mesh\GraphqlResponse;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadContent;
use Gentics\PortalPhp\Features\Mesh\Http3xxResponse;
use Gentics\PortalPhp\Features\Mesh\Response;
use Gentics\PortalPhp\RenderMode;
use Gentics\PortalPhp\Traits\NeedsNavigation;

use Illuminate\Http\Request;
use Illuminate\Support\Arr;

/**
 * Class ContentController
 * @package Gentics\PortalPhp\Controllers
 */
class ContentController extends BaseController
{
    use NeedsNavigation;

    protected static $contentTypeRegex = "/^(text|application|\*)\/[\s\S]+$/";

    /**
     * @var string|null
     */
    protected $path         = null;

    /**
     * @var Response|null
     */
    protected $response     = null;

    /**
     * Action that loads the requested node from Mesh
     *
     * @param string $path
     * @return \Illuminate\Contracts\View\Factory|\Illuminate\Http\RedirectResponse|\Illuminate\View\View|\Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     */
    public function index(string $path = '/')
    {
        // Workaround for bug where the startpage property of the root node folder in the CMS is not being published
        if ($path === '/') {
            $path = config('portal.startPage');
        }
        $response = Mesh::request(LoadContent::class)
            ->setPath($path)
            ->setAsRouted(true)
            ->fetch();

        return $this->getResponse($response, $path);
    }

    /**
     * Modify and serve the requested image
     *
     * @urlParam width string required Desired width, either non-zero, non-negative integer, or 'auto'. Example: 200
     * @urlParam height string required Desired height, either non-zero, non-negative integer, or 'auto'. Example: 200
     * @urlParam mode string required Desired resize mode. Enum: prop, force, smart, cropandresize. Example: 200
     * @urlParam path string required Path to an image. Example: /blumes/blume.jpg
     * @response 200 <<binary>> the modified image
     *
     * @param Request $request
     * @return \Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     */
    public function genticsImageStore(Request $request)
    {
        $gisHandler = app()->make(GenticsImageStoreHandler::class);
        return $gisHandler->handle($request)->createResponse();
    }

    /**
     * @param array $responseData
     * @return \Illuminate\Contracts\View\Factory|\Illuminate\View\View
     * @throws \Throwable
     */
    protected function page(array $responseData)
    {
        if (Arr::has($responseData, 'node.fields.contenttype')
                    && preg_match(static::$contentTypeRegex, $responseData['node']['fields']['contenttype'])) {
            return response()
                ->view($this->portalPhp->getTemplateName($responseData), $responseData)
                ->header('Content-Type', $responseData['node']['fields']['contenttype']);
        } else {
            return view($this->portalPhp->getTemplateName($responseData), $responseData);
        }
    }

    /**
     * @return \Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     */
    protected function binary()
    {
        return $this->response->createResponse();
    }

    /**
     * @param array $responseData
     * @return \Illuminate\Http\RedirectResponse
     * @throws \Throwable
     */
    protected function folder(array $responseData)
    {
        if (!Arr::has($responseData, 'node.fields.startpage.languages')
          || empty($responseData['node']['fields']['startpage']['languages'])
          || !Arr::has($responseData, 'node.fields.startpageurl')
          || $responseData['node']['fields']['startpageurl'] === null) {
            abort(404);
        }

        $startPageLanguages = Arr::get($responseData, 'node.fields.startpage.languages', []);
        $startPagePath = $this->portalPhp->getLocalizedStartPagePath($startPageLanguages);

        if ($startPagePath === null) {
            $startPagePath = Arr::get($responseData, 'node.fields.startpageurl', null);
        }

        if ($startPagePath === null) {
            abort(404);
        } else {
            return redirect()->action([static::class, 'index'], ['path' => $startPagePath]);
        }
    }

    /**
     * @param Response $response
     * @param string $path
     * @return \Symfony\Component\HttpFoundation\StreamedResponse|\Illuminate\Contracts\View\Factory|\Illuminate\View\View|\Illuminate\Http\RedirectResponse
     * @throws \Throwable
     */
    protected function getResponse(Response $response, string $path = '/')
    {
        $this->path       = $path;
        $this->response   = $response;

        if ($response instanceof BinaryResponse) {
            return $this->binary();
        } elseif ($response instanceof Http3xxResponse) {
            return $response->createResponse();
        }

        /** @var Response|GraphqlResponse $response */
        $responseData = $response->toArray()['data'];

        if (!empty($responseData['node']['language'])) {
            $this->portalPhp->saveUserLanguage($responseData['node']['language']);
        }

        // RenderMode::Publish is the default portal mode, any other mode only possible from the CMS
        // and with the CMS the navigation should not be cached and rendered with admin privileges
        if ($this->renderMode === RenderMode::PUBLISH) {
            $responseData = $this->addNavigation($responseData, null, null, $path);
        } else {
            $responseData = $this->addNavigation($responseData, false, true, $path);
        }

        if ($responseData['node']['schema']['name'] === config('portal.schemas.folder')) {
            return $this->folder($responseData);
        }

        return $this->page($responseData);
    }
}
