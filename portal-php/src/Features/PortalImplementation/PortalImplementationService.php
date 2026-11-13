<?php

namespace Gentics\PortalPhp\Features\PortalImplementation;

use Gentics\Mesh\Client\MeshClient;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

/**
 * Loads the files of the configured portal implementation (portal.portalImplementation) from Mesh.
 *
 * The portal implementation is a published node of the schema portal.schemas.implementation, which is a direct child
 * of the root node of the configured Mesh project (default branch), and has the configured keyword in its field
 * "keyword". Its binary field "package" contains a zip file, which is extracted by the
 * {@see PortalImplementationExtractor}. The package is only extracted again, when its checksum changed.
 */
class PortalImplementationService
{
    public const FIELD_KEYWORD = 'keyword';
    public const FIELD_PACKAGE = 'package';

    public const DEFAULT_SCHEMA = 'portalphp_implementation';

    public const STATUS_DISABLED = 'disabled';
    public const STATUS_NOT_FOUND = 'not-found';
    public const STATUS_UNCHANGED = 'unchanged';
    public const STATUS_UPDATED = 'updated';

    /** Name of the state file (in the state folder) holding the checksum of the extracted package */
    public const STATE_FILE = 'state.json';

    /** Name of the cache lock preventing concurrent synchronizations */
    public const LOCK_NAME = 'portal-implementation-sync';

    /** Maximum duration of a synchronization in seconds (for the lock) */
    public const LOCK_SECONDS = 600;

    /** Placeholder for the implementation schema name in {@see IMPLEMENTATION_QUERY} */
    protected const SCHEMA_PLACEHOLDER = '%implementationSchema%';

    /**
     * GraphQL query loading the children of the root node with the implementation schema and the given keyword.
     * The schema filter takes an enum value and the fragment a type name, so the schema name can not be a variable.
     */
    public const IMPLEMENTATION_QUERY = <<<'GRAPHQL'
query ($keyword: String) {
  rootNode {
    children(filter: {
      schema: {is: %implementationSchema%},
      fields: {%implementationSchema%: {keyword: {equals: $keyword}}}
    }) {
      elements {
        uuid
        language
        fields { ... on %implementationSchema% { package { binaryUuid fileName sha512sum } } }
      }
    }
  }
}
GRAPHQL;

    /**
     * @var PortalPhp
     */
    protected $portalPhp;

    /**
     * @var array Map of package folder names to the target paths
     */
    protected $targets;

    /**
     * @var string Folder for the state, the temporary files and the backups of the static folder
     */
    protected $stateDir;

    /**
     * @var PortalImplementationExtractor
     */
    protected $extractor;

    /**
     * @param PortalPhp $portalPhp
     * @param array $targets Map of package folder names to the target paths
     * @param string $stateDir Folder for the state, the temporary files and the backups of the static folder
     * @param PortalImplementationExtractor|null $extractor
     */
    public function __construct(
        PortalPhp $portalPhp,
        array $targets,
        string $stateDir,
        ?PortalImplementationExtractor $extractor = null
    ) {
        $this->portalPhp = $portalPhp;
        $this->targets = $targets;
        $this->stateDir = $stateDir;
        $this->extractor = $extractor ?? new PortalImplementationExtractor();
    }

    /**
     * Get the target paths of the package folders.
     *
     * @return array Map of package folder names to the target paths
     */
    public function getTargets(): array
    {
        return $this->targets;
    }

    /**
     * Get the configured keyword of the portal implementation.
     *
     * @return string|null
     */
    public function getKeyword(): ?string
    {
        $keyword = config('portal.portalImplementation');

        return empty($keyword) ? null : (string) $keyword;
    }

    /**
     * Check whether a portal implementation is configured.
     *
     * @return bool
     */
    public function isEnabled(): bool
    {
        return $this->getKeyword() !== null;
    }

    /**
     * Get the name of the schema of the portal implementation nodes.
     *
     * @return string
     */
    public function getSchema(): string
    {
        return config('portal.schemas.implementation') ?: self::DEFAULT_SCHEMA;
    }

    /**
     * Load the portal implementation from Mesh and extract its package, if it changed since the last
     * synchronization. Concurrent synchronizations wait for each other.
     *
     * @return string One of the STATUS_* constants
     * @throws \Throwable When the portal implementation could not be loaded or extracted, the local files are kept
     */
    public function sync(): string
    {
        if (!$this->isEnabled()) {
            return self::STATUS_DISABLED;
        }

        return Cache::lock(self::LOCK_NAME, self::LOCK_SECONDS)->block(self::LOCK_SECONDS, function () {
            return $this->doSync();
        });
    }

    /**
     * @return string
     * @throws \Throwable
     */
    protected function doSync(): string
    {
        $keyword = $this->getKeyword();
        $project = $this->portalPhp->config('projectName');
        $client = $this->portalPhp->getAdminMeshClient();
        $node = $this->findImplementation($client, $project, $keyword);

        if ($node === null) {
            Log::warning(sprintf(
                'Portal implementation %s (schema %s) not found in Mesh project %s, using local files',
                $keyword,
                $this->getSchema(),
                $project
            ));
            return self::STATUS_NOT_FOUND;
        }

        $uuid = $node['uuid'];
        $package = Arr::get($node, 'fields.' . self::FIELD_PACKAGE);

        if (empty($package) || empty($package['binaryUuid'])) {
            Log::warning(sprintf('Portal implementation %s (%s) has no package, using local files', $keyword, $uuid));
            return self::STATUS_NOT_FOUND;
        }

        $checksum = $package['sha512sum'] ?? null;

        if ($checksum !== null && $checksum === Arr::get($this->readState(), 'checksum')) {
            return self::STATUS_UNCHANGED;
        }

        Log::info(sprintf(
            'Downloading package %s of portal implementation %s (%s)',
            $package['fileName'] ?? '',
            $keyword,
            $uuid
        ));

        if (!is_dir($this->stateDir)) {
            mkdir($this->stateDir, 0777, true);
        }

        $zipFile = tempnam($this->stateDir, 'package-');

        try {
            $this->downloadPackage($client, $project, $uuid, $node['language'] ?? null, $zipFile);
            $this->extractor->extract($zipFile, $this->targets, $this->stateDir);
        } finally {
            @unlink($zipFile);
        }

        $this->writeState(['keyword' => $keyword, 'uuid' => $uuid, 'checksum' => $checksum]);
        $this->clearCaches();

        Log::info(sprintf('Portal implementation %s was updated', $keyword));

        return self::STATUS_UPDATED;
    }

    /**
     * Find the published implementation node with the given keyword among the children of the project root node,
     * using a GraphQL query filtering by schema name and keyword.
     *
     * @param MeshClient $client
     * @param string $project
     * @param string $keyword
     * @return array|null The node as returned by {@see IMPLEMENTATION_QUERY}
     * @throws PortalPhpException When the query returned errors
     */
    protected function findImplementation(MeshClient $client, string $project, string $keyword): ?array
    {
        $query = str_replace(self::SCHEMA_PLACEHOLDER, $this->getSchema(), self::IMPLEMENTATION_QUERY);
        $response = $client->graphQL(
            $project,
            ['query' => $query, 'variables' => ['keyword' => $keyword]],
            ['version' => 'published']
        )->send()->toArray();

        if (!empty($response['errors'])) {
            throw new PortalPhpException(sprintf(
                'Could not load portal implementation %s: %s',
                $keyword,
                implode(', ', array_map(function ($error) {
                    return $error['message'] ?? json_encode($error);
                }, $response['errors']))
            ));
        }

        $matches = Arr::get($response, 'data.rootNode.children.elements') ?: [];

        if (empty($matches)) {
            return null;
        }

        if (count($matches) > 1) {
            Log::warning(sprintf(
                'Found %d portal implementations with keyword %s, using %s',
                count($matches),
                $keyword,
                $matches[0]['uuid']
            ));
        }

        return $matches[0];
    }

    /**
     * Download the published package of the node into the given file.
     *
     * The Mesh client does not implement downloadBinaryField(), so the request for the node is changed into a
     * request for its binary field (keeping the authentication of the client).
     *
     * @param MeshClient $client
     * @param string $project
     * @param string $uuid
     * @param string|null $language
     * @param string $file
     */
    protected function downloadPackage(
        MeshClient $client,
        string $project,
        string $uuid,
        ?string $language,
        string $file
    ) {
        $nodeRequest = $client->findNodeByUuid($project, $uuid);
        $uri = $nodeRequest->getUri();
        $path = rtrim($uri->getPath(), '/') . '/binary/' . self::FIELD_PACKAGE;
        $request = $nodeRequest->withUri($uri->withPath($path));
        $query = ['version' => 'published'];

        if (!empty($language)) {
            $query['lang'] = $language;
        }

        $client->send($request, ['query' => $query, 'sink' => $file]);
    }

    /**
     * Clear the full response cache (if enabled), so that no pages rendered with the old portal implementation are
     * served. Blade views and GraphQL queries are recompiled automatically, because their files changed.
     */
    protected function clearCaches()
    {
        if (config('portal.cache.http.enabled') && array_key_exists('httpcache:clear', Artisan::all())) {
            Artisan::call('httpcache:clear');
        }
    }

    /**
     * @return array
     */
    protected function readState(): array
    {
        $file = $this->stateDir . '/' . self::STATE_FILE;

        return is_file($file) ? (json_decode(file_get_contents($file), true) ?: []) : [];
    }

    /**
     * @param array $state
     */
    protected function writeState(array $state)
    {
        file_put_contents($this->stateDir . '/' . self::STATE_FILE, json_encode($state, JSON_PRETTY_PRINT));
    }
}
