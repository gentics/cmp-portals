<?php
namespace Gentics\PortalPhp\Traits;

use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadNavigation;

trait NeedsNavigation
{
    /**
     * Adds project data to the responseData which contains the navigation
     *
     * @param array $responseData
     * @param bool $cached
     * @param bool $adminClient
     * @param string $path Override Auto-Detected path
     * @return array
     * @throws \Throwable
     */
    protected function addNavigation(
        array $responseData,
        ?bool $cached = true,
        ?bool $adminClient = false,
        string $path = null
    ) {
        // If 'project' is not set means there is no navigation loaded
        // with the response data, so we loading it now.
        if (!isset($responseData['rootNode'])) {
            // Set Path for branch detection
            if (empty($path)) {
                $path = app()->request->path();
            }

            $navigationResponse = Mesh::request(LoadNavigation::class)
                ->setPath($path)
                ->setAdminClient($adminClient ?? false);

            if ($cached ?? true) {
                $navigationResponse = $navigationResponse->fetchCached();
            } else {
                $navigationResponse = $navigationResponse->fetch();
            }

            $navigationResponse = $navigationResponse->toArray()['data'];

            // Merge response data into navigation response
            $responseData = array_merge($navigationResponse, $responseData);
        }

        return $responseData;
    }
}
