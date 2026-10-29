<?php
namespace Gentics\PortalPhp\Features\Mesh\Traits;

use Gentics\PortalPhp\Features\Mesh\GraphqlResponse;
use Gentics\PortalPhp\PortalPhp;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Str;

trait WithCache
{
    protected $cacheKey;
    protected $staleKeyPrefix = 'update_lock_';
    protected $lockKeyPrefix = 'stale_';

    /**
     * Returns fetched data from cache if available, otherwise it passthrough fetch() and caches it.
     *
     * @return mixed
     */
    public function fetchCached()
    {
        $cacheTimeDefault = 60;

        if (property_exists($this, 'cacheTimeDefault')) {
            $cacheTimeDefault = $this->cacheTimeDefault;
        }

        if (property_exists($this, 'cacheTimeConfigKey')) {
            $cacheTime = (int)config($this->cacheTimeConfigKey, $cacheTimeDefault);
        } else {
            $cacheTime = (int) $cacheTimeDefault;
        }
        $this->cacheKey = $this->makeCacheKey();

        $updateLock = $this->getLockKey($this->lockKeyPrefix . $this->cacheKey);

        // Return stale data when its updating
        if (Cache::has($this->cacheKey) && $this->isKeyLocked($updateLock)) {
            $response = Cache::get($this->cacheKey);
        } else {
            // Set lock timeout to $cacheTimeDefault
            Cache::put($this->lockKeyPrefix . $this->cacheKey, true, now()->addSeconds($cacheTimeDefault));

            // Start fetching fresh data
            $response = $this->fetch();
            if ($response instanceof GraphqlResponse && empty($response->toArray()['errors'])) {
                // Cache returned data forever
                Cache::forever($this->cacheKey, $response);

                $this->releaseKey($updateLock, $cacheTime);
            }
        }

        return $response;
    }

    /**
     * Creates the cache key from prefix and unique identifier if available
     *
     * @return string
     */
    protected function makeCacheKey()
    {
        if (property_exists($this, 'cacheKeyPrefix')) {
            $cacheKey = $this->cacheKeyPrefix;
        } else {
            $cacheKey = 'mesh:' . Str::snake(get_called_class()) . ':';
        }

        if (method_exists($this, 'getCacheIdentifier')) {
            $cacheKey .= $this->getCacheIdentifier();
        }

        // Add project identifier
        $portalPhp = app()->make(PortalPhp::class);
        $cacheKey .= $portalPhp->getProjectName();

        return $cacheKey;
    }

    protected function getLockKey(string $key)
    {
        if (function_exists('sem_get')) {
            return sem_get(crc32($key), 1);
        } else {
            return $key;
        }
    }

    protected function isKeyLocked($identifier)
    {
        if ($identifier instanceof \SysvSemaphore || is_resource($identifier)) {
            // If we do not have stale cache, we need to fetch it
            if (!Cache::has($this->staleKeyPrefix . $this->cacheKey)) {
                // Release semaphore if its running longer than $cacheTimeDefault
                if (!Cache::has($this->lockKeyPrefix . $this->cacheKey)) {
                    try {
                        sem_acquire($identifier, true);
                        sem_release($identifier);
                    } catch (\ErrorException $e) {
                    }

                    return false;
                }

                return sem_acquire($identifier, true) !== false;
            }

            // We have stale cache
            return true;
        } else {
            return Cache::has($identifier) || Cache::has($this->staleKeyPrefix . $this->cacheKey);
        }
    }

    protected function releaseKey($identifier, int $cacheTime)
    {
        // Set expiry time for update
        Cache::put($this->staleKeyPrefix . $this->cacheKey, true, now()->addSeconds($cacheTime));

        if ($identifier instanceof \SysvSemaphore || is_resource($identifier)) {
            if (sem_acquire($identifier, true) === false) {
                try {
                    sem_release($identifier);
                } catch (\ErrorException $e) {
                }
            }
        } else {
            // Remove isUpdating state
            Cache::forget($identifier);
        }
    }
}
