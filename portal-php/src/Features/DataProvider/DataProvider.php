<?php

namespace Gentics\PortalPhp\Features\DataProvider;

use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadContent;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Facades\Cache;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;

/**
 * Class DataProvider
 * @package Gentics\PortalPhp\Features\DataProvider
 */
class DataProvider
{
    /**
     * @var PortalPhp|null
     */
    protected $portalPhp = null;

    /**
     * DataProvider constructor.
     *
     * @param PortalPhp $portalPhp
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
    }

    /**
     * Provides a getter for different MeshData DataProviders
     * It using the array key from the configuration as method name.
     *
     * E.g: 'config' -> DataProvider::getConfig()
     *
     * It works case-insensitive (DataProvider::getConFig() will be the same).
     *
     * The returned json data can be expanded as array, if keys are defined
     * in the configuration file.
     *
     * @param $methodName
     * @param $arguments
     * @return mixed
     * @throws \Throwable
     */
    public static function __callStatic($methodName, $arguments)
    {
        $methodPrefix = substr($methodName, 0, 3);
        $key = strtolower(substr($methodName, 3));

        $meshData = array_change_key_case(config('portal.dataProvider.meshData', []), CASE_LOWER);

        if ($methodPrefix == "get") {
            throw_if(
                !array_key_exists($key, $meshData),
                Exceptions\PortalPhpException::class,
                "DataProvider key " . $key . " not found in configuration"
            );

            $dataProvider = resolve(static::class);
            $data = $dataProvider->retrieveMeshData($key, $arguments);

            if (is_array($data)) {
                $dataProvider->expandJsonKeys($data);
            }

            return $data;
        }
    }

    /**
     * Prefetches a datasource or all with updating the cache.
     *
     * @param string $datasource
     * @throws \Throwable
     */
    public static function prefetch(string $datasource)
    {
        $dataProvider = resolve(static::class);
        $datasource = strtolower($datasource);
        $datasources = [$datasource];

        if ($datasource == 'all') {
            $datasources = array_keys(config('portal.dataProvider.meshData', []));
        }

        foreach ($datasources as $datasource) {
            $dataProvider->retrieveMeshData($datasource, null, true);
        }
    }

    /**
     * Prefetches all Mesh branches of the default Mesh project
     * @return array
     */
    public static function prefetchBranches()
    {
        $dataProvider = resolve(static::class);
        $dataProvider->retrieveBranches(true);
    }

    /**
     * Expands specific array keys with json_decode on specified in configuration array.
     *
     * @param array $data
     */
    public function expandJsonKeys(array &$data)
    {
        $expandJsonKeys = config('portal.dataProvider.expandJsonKeys', []);
        array_walk_recursive($data, function (&$item, $key) use ($expandJsonKeys) {
            if (in_array($key, $expandJsonKeys)) {
                $item = json_decode($item, true);
            }
        });
    }

    /**
     * Returns cached data if available. When no data available, it tries to fetch.
     * Cache can be bypassed by providing the bypassCache parameter, it also updates
     * the cache.
     *
     * @param string $key
     * @param $arguments
     * @param bool $bypassCache
     * @return mixed
     */
    public function retrieveMeshData(string $key, $arguments, bool $bypassCache = false)
    {
        $cacheTime = (int) config('portal.dataProvider.cacheTime', 60);
        $branch = $this->portalPhp->findBranchNameFromRequest('/' . request()->path());
        $cacheKey = 'dataprovider_meshdata_' . $branch . '_' . $key;

        if ($bypassCache) {
            Cache::forget($cacheKey);
        }

        $value = Cache::remember($cacheKey, now()->addSeconds($cacheTime), function () use ($key, $branch) {
            return $this->fetchByKey($key, $branch);
        });

        return $value;
    }


    /**
     * Returns an array with the branches of the default Mesh project specified in the configuration.
     * If the cache has expired or hasn't been set, an API request is made.
     * @param bool $bypassCache Whether to use the cache or update it
     * @return array An array with the branch data from Mesh
     * @throws \Throwable
     */
    public function retrieveBranches(bool $bypassCache = false)
    {
        $projectName = $this->portalPhp->config('projectName');
        $cacheTime = (int) config('portal.dataProvider.cacheTime', 60);
        $cacheKey = 'dataprovider_meshbranches_' . $projectName;

        if ($bypassCache) {
            Cache::forget($cacheKey);
        }

        $value = Cache::remember($cacheKey, now()->addSeconds($cacheTime), function () use ($projectName) {
            return $this->fetchMeshProjectBranches($projectName);
        });

        return $value;
    }

    /**
     * Makes a Mesh request to load all the branches of a Mesh project
     * @param string $projectName Project name
     * @return array All branches of the project
     * @throws \Throwable
     */
    protected function fetchMeshProjectBranches(string $projectName)
    {
        return $this->portalPhp->getAdminMeshClient()->findBranches($projectName)->send()->toArray()['data'];
    }

    /**
     * Fetching DataProvider URL and return the response data as array.
     *
     * @param $key
     * @param $branch
     * @return mixed
     * @throws \Throwable
     */
    private function fetchByKey($key, $branch)
    {
        $meshData = array_change_key_case(config('portal.dataProvider.meshData', []), CASE_LOWER);

        throw_if(
            !array_key_exists($key, $meshData),
            Exceptions\PortalPhpException::class,
            "DataProvider key " . $key . " not found in configuration"
        );

        // check if the config contains array or string at the key we are trying to fetch
        throw_if(
            !(is_array($meshData[$key]) || is_string($meshData[$key])),
            Exceptions\PortalPhpException::class,
            "DataProvider config key  " . $key . " has to be array or string"
        );

        if (is_array($meshData[$key])) {
            // try each entry and see if it can be fetched in the current branch / channel
            foreach ($meshData[$key] as $dataProviderUrl) {
                $result = Mesh::request(LoadContent::class)
                    ->setPath($dataProviderUrl)
                    ->setAbortOnError(false)
                    ->setAdminClient(true)
                    ->setBranchName($branch)
                    ->fetch()
                    ->toArray()['data'];
                // return as soon as we get results -> skip all other urls from config
                if (!empty($result['node'])) {
                    return $result;
                }
            }
        } elseif (is_string($meshData[$key])) {
            return Mesh::request(LoadContent::class)
                ->setPath($meshData[$key])
                ->setAbortOnError(false)
                ->setAdminClient(true)
                ->setBranchName($branch)
                ->fetch()
                ->toArray()['data'];
        }
    }
}
