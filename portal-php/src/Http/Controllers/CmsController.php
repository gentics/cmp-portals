<?php

namespace Gentics\PortalPhp\Http\Controllers;

use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadContent;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\Response;

use Illuminate\Support\Arr;

/*
 * @group Public API
 */
class CmsController extends ContentController
{
    protected $fieldMappings = [
        'parentNode' => 'parent'
    ];

    /**
     * Action that loads the requested node from Mesh
     *
     * @group Content
     * @urlParam folderPath string required Full webroot path to the resource Example: /content/blume.html
     * @response 200 Stored content, either JSON or binary or plain text
     * @response 404 Webroot path does not exist
     *
     * @param string $path
     * @return \Illuminate\Contracts\View\Factory|\Illuminate\Http\RedirectResponse|\Illuminate\View\View|mixed|\Symfony\Component\HttpFoundation\StreamedResponse
     * @throws \Throwable
     */
    public function index(string $path = '/')
    {
        $branchName = null;
        $channelUuid = request()->input('channelUuid');
        if (!empty($channelUuid)) {
            $branchName = $this->portalPhp->gcmsChannelUuidToBranchName($channelUuid);
        }

        $path = Mesh::normalizePath($path);

        $response = Mesh::request(LoadContent::class)
            ->setPath($path)
            ->setFolderPath($path)
            ->setBranchName($branchName)
            ->setAdminClient(true)
            ->setAbortOnError(false)
            ->fetch();

        // Validate Response type - if not valid, return partial data from the CMS
        if ($response instanceof Response) {
            return $this->getResponse($response, $path);
        } else {
            $responseData = $response->toArray()['data'];
            $responseData = $this->addNavigation($responseData, false, true, $path);

            return $this->folder($responseData);
        }
    }

    /**
     * Preview action for CMS edit mode
     *
     * @param array $responseData
     * @return \Illuminate\Contracts\View\Factory|\Illuminate\View\View
     * @throws \Throwable
     */
    public function folder(array $responseData)
    {
        $data = $this->injectPreviewData($responseData);
        return view($this->portalPhp->getTemplateName($data), $data);
    }

    /**
     * Preview action for CMS edit mode
     *
     * @param array $responseData
     * @return \Illuminate\Contracts\View\Factory|\Illuminate\View\View
     * @throws \Throwable
     */
    public function page(array $responseData)
    {
        return $this->folder($responseData);
    }

    /**
     * Take the preview data from the request and merge them into the Mesh response.
     *
     * @param $data
     * @return mixed The Mesh response object merged with the data from the page
     */
    protected function injectPreviewData(array $data)
    {
        $breadcrumb = $data['node']['breadcrumb'] ?? null;
        $data['node'] = request()->post();
        $this->mapPreviewData($data);
        $data['node']['breadcrumb'] = $breadcrumb ?? [];

        return $data;
    }

    /**
     *  Rename all fields on the top level according to the $fieldMappings
     *  when the compatibility option is enabled.
     *
     *  @param $data
     */
    protected function mapPreviewData(array &$data)
    {
        $isMappingEnabled = config('portal.compatibility.mapNodeResponse', true);

        if ($isMappingEnabled) {
            foreach ($this->fieldMappings as $srcField => $dstField) {
                if (isset($data['node']) && isset($data['node'][$srcField])) {
                    $data['node'][$dstField] = $data['node'][$srcField];
                    unset($data['node'][$srcField]);
                }
            }
        }
    }
}
