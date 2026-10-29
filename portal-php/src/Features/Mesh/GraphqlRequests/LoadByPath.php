<?php
namespace Gentics\PortalPhp\Features\Mesh\GraphqlRequests;

use Gentics\PortalPhp\Features\Mesh\BinaryResponse;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequest;
use Gentics\PortalPhp\Features\Mesh\GraphqlResponse;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\Http3xxResponse;

use Illuminate\Support\Str;

/**
 * Class LoadByPath
 * {@inheritDoc}
 * @method $this|LoadByPath setAbortOnError(bool $value)
 * @method $this|LoadByPath setPath(string $path)
 * @method $this|LoadByPath setFolderPath(string $folderPath)
 * @method $this|LoadByPath setBranchName(string $branchName)
 * @method mixed getAbortOnError()
 * @method mixed getPath()
 * @method mixed getFolderPath()
 * @method mixed getBranchName()
 * @package Gentics\PortalPhp\Features\Mesh\Requests
 */
class LoadByPath extends GraphqlRequest
{
    /** List of Accessible Properties */
    protected $properties = [
        'abortOnError',
        'path',
        'folderPath',
        'branchName',
    ];

    /**
     * Accessible Properties
     */

    /** @var bool Defines to throw exception on error detected with the response. */
    protected $abortOnError = true;
    /** @var string Override the folderpath variable that automatically generated from path. */
    protected $folderPath;
    /** @var string Override the branchName variable to skip auto-detect. */
    protected $branchName;
    /** @var string Set request path (Required) */
    protected $path;

    /**
     * @return \Gentics\Mesh\Client\Rest\MeshResponse|GraphqlResponse
     * @throws \Throwable
     */
    public function fetch()
    {
        throw_if(empty($this->path), Exceptions\PortalPhpException::class, "The path property must be set!");
        $path = Mesh::normalizePath($this->path);
        $branch = $this->portalPhp->findBranchFromRequest($path);

        // Prepend pathPrefix when not present and matchPageDirectory is enabled
        if ($this->portalPhp->config("branches.matchPageDirectory", false, true)
            && !empty($branch['pathPrefix'])) {
            $pathPrefix = Mesh::normalizePath($branch['pathPrefix']);

            if (!Str::startsWith($path, $pathPrefix) && $path != $pathPrefix) {
                $path = preg_replace('#/+#', '/', $pathPrefix . $path);
            }
        }

        $response = $this->earlyBinaryGuess($path);
        if ($response instanceof Http3xxResponse || $response instanceof BinaryResponse) {
            return $response;
        }

        $folderPath = $this->folderPath($path);

        // Prepare Query Variables
        $variables = [
            'path' => $path,
            'folderpath' => $folderPath
        ];

        $this->appendQueryVariables($variables);

        // Prepare Query Parameters
        $parameters = [];
        $parameters['version'] = 'published';
        $branchName = $this->branchName;

        if (empty($branchName) && !empty($branch) && array_key_exists('name', $branch)) {
            $branchName = $branch['name'];
        }

        if (!empty($branchName)) {
            $parameters['branch'] = $branchName;
        }

        $this->appendQueryParameters($parameters);
        $this->useMultilingualCurrentLanguageFilter();

        // Check if it's a cached etag response or GraphQL response need to be parsed
        $response = parent::fetch();
        if ($response instanceof Http3xxResponse || $response instanceof BinaryResponse) {
            return $response;
        } else {
            $response = $response->getMeshResponse();
        }

        $responseArray = $response->toArray();
        $responseData = $responseArray['data'] ?? [];

        if ($this->abortOnError) {
            abort_if(empty($responseData['node']), 404);
        }

        if (empty($responseData['node']['schema']['name'])) {
            return $response;
        }

        $schemaName = $responseData['node']['schema']['name'];

        switch ($schemaName) {
            case $this->finalBinaryContentSchema():
                return Mesh::response($this->portalPhp->loadBinaryByPath($path, $this->finalProjectName()));
        }

        return Mesh::response($response);
    }

    /**
     * @param $path
     * @return string
     */
    protected function folderPath($path)
    {
        $folderPath = dirname($path);
        if ($folderPath === '.') {
            // dirname returns a '.' for the root folder, but GraphQL needs ''
            $folderPath = '';
        }

        if ($this->folderPath !== null) {
            $folderPath = $this->folderPath;
        }

        return $folderPath;
    }

    /**
     * @param $path
     * @return mixed
     * @throws \Throwable
     */
    protected function earlyBinaryGuess($path)
    {
        // Try to guess if the path is a binary file, and try to load it from Mesh webroot
        $guessIsBinaryContent = $this->portalPhp->guessIsBinaryContent($path);
        if ($guessIsBinaryContent) {
            $binaryResponse = Mesh::response($this->portalPhp->loadBinaryByPath(
                $path,
                $this->finalProjectName(),
                [],
                $this->adminClient
            ));

            return $binaryResponse;
        }

        return false;
    }
}
