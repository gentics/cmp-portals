<?php
namespace Gentics\PortalPhp\Features\Mesh;

use Gentics\Mesh\Client\MeshClient;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Str;
use GuzzleHttp\Exception\ClientException;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Throwable;

/**
 * Class GraphqlRequest
 * @method $this|GraphqlRequest setAdminClient(bool $value)
 * @method $this|GraphqlRequest setParameters(array $parameters)
 * @method $this|GraphqlRequest setPlaceholders(array $placeholders)
 * @method $this|GraphqlRequest setVariables(array $variables)
 * @method $this|GraphqlRequest setProjectName(string $projectName)
 * @method $this|GraphqlRequest setContentSchema(string $contentSchema)
 * @method $this|GraphqlRequest setFolderSchema(string $folderSchema)
 * @method $this|GraphqlRequest setBinaryContentSchema(string $binaryContentSchema)
 * @method $this|GraphqlRequest setTemplateName(string $templateName)
 * @method $this|GraphqlRequest setAsRouted(bool $routed)
 * @method $this|GraphqlRequest setMeshClient(MeshClient $meshClient)
 * @method mixed getAdminClient()
 * @method mixed getParameters()
 * @method mixed getPlaceholders()
 * @method mixed getVariables()
 * @method mixed getProjectName()
 * @method mixed getContentSchema()
 * @method mixed getFolderSchema()
 * @method mixed getBinaryContentSchema()
 * @method mixed getTemplateName()
 * @method mixed getAsRouted()
 * @method mixed getMeshClient()
 * @package Gentics\PortalPhp\Features\Mesh
 */
class GraphqlRequest
{
    /** List of Accessible Properties */
    protected $properties = [
        'asRouted',
        'adminClient',
        'parameters',
        'placeholders',
        'variables',
        'projectName',
        'contentSchema',
        'folderSchema',
        'binaryContentSchema',
        'templateName',
        'meshClient'
    ];

    /**
     * Accessible Properties
     */

    /** @var bool Marks that the query is routed by Mesh using the request url */
    protected $asRouted = false;
    /** @var bool Defines to use the PortalPhp admin client or user/anonymous client */
    protected $adminClient = false;
    /** @var array Associative array of parameters to pass to Mesh request. It can override predefined parameters */
    protected $parameters = [];
    /**
     * @var array Associative array of placeholders to replace in GraphQL template.
     * It can override predefined placeholders.
     */
    protected $placeholders = [];
    /** @var array Associative array of variables to pass to GraphQL. It can override predefined variables */
    protected $variables = [];

    /** @var string Override the project name used in the portal's configuration */
    protected $projectName;
    /** @var string Override the content schema used in the portal's configuration */
    protected $contentSchema;
    /** @var string Override the folder schema used in the portal's configuration */
    protected $folderSchema;
    /** @var string Override the binary content schema used in the portal's configuration */
    protected $binaryContentSchema;
    /** @var string Override the template name used in the portal's configuration */
    protected $templateName;
    /** @var MeshClient Custom Mesh Client to use */
    protected $meshClient;

    /**
     * Internal Properties
     */

    /** @var array Associative array of parameters to pass to Mesh request. Used to predefine in class level. */
    protected $queryParameters = [];
    /**
     * @var array Associative array of placeholders to replace in GraphQL template. Used to predefine in class level.
     */
    protected $queryPlaceholders = [];
    /** @var array  Associative array of variables to pass to GraphQL. Used to predefine in class level. */
    protected $queryVariables = [];

    /**
     * @var PortalPhp
     */
    protected $portalPhp;

    /**
     * GraphqlRequest constructor.
     * @param PortalPhp $portalPhp
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $class = static::class;

        // Set template name based on class-name if not parent class, and templateName property is null
        $fallbackTemplateName = lcfirst(class_basename($class));

        if (is_null($this->templateName) && $class !== self::class) {
            $this->templateName = $fallbackTemplateName;
        }

        // Merge all properties from child classes up to the parents.
        while ($class = get_parent_class($class)) {
            if (isset(get_class_vars($class)['properties'])) {
                array_push($this->properties, ...get_class_vars($class)['properties']);
            }
        }

        $this->portalPhp = $portalPhp;
    }

    /**
     * Builds and sends the request to Mesh, then return the response.
     *
     * @return GraphqlResponse
     * @throws \Throwable
     */
    public function fetch()
    {
        // Throw exception if templateName is not set (null)
        throw_if(
            $this->templateName === null,
            Exceptions\PortalPhpException::class,
            "The templateName property cannot be null!"
        );

        // Try to serve etag cache first if it's a route based query
        if ($this->asRouted && $cachedEtagResponse = Mesh::tryServeEtagCachedResponse()) {
            AppCache::bypassWhen('response3xx');
            return Mesh::response($cachedEtagResponse);
        }

        $grapqlQuery = $this->prepareQuery();
        return $this->sendRequest($grapqlQuery);
    }

    /**
     * Magic call method to set/get accessible properties
     *
     * @param $methodName
     * @param $arguments
     * @return $this
     * @throws \Throwable
     */
    public function __call($methodName, $arguments)
    {
        $staticPrefixes = ['set', 'get'];
        $methodPrefix = null;
        $key = null;

        foreach ($staticPrefixes as $prefix) {
            // Find if a matching method called
            if (Str::is($prefix . '*', $methodName)) {
                $methodPrefix = substr($methodName, 0, strlen($prefix));
                $key = substr($methodName, strlen($prefix));
            }
        }

        if (!is_null($methodPrefix) && !is_null($key)) {
            switch ($methodPrefix) {
                case 'set':
                    $setKey = lcfirst($key);
                    if (in_array($setKey, $this->allAccessibleProperties())) {
                        if (isset($arguments[0]) || is_null($arguments[0])) {
                            $this->{$setKey} = $arguments[0];
                            return $this;
                        } else {
                            throw new Exceptions\PortalPhpException(
                                sprintf('Value must be set for property "%s"', $setKey)
                            );
                        }
                    } else {
                        throw new Exceptions\PortalPhpException(
                            sprintf('Trying to set non-accessible property "%s"', $setKey)
                        );
                    }
                    break;
                case 'get':
                    $getKey = lcfirst($key);
                    if (in_array($getKey, $this->allAccessibleProperties())) {
                        return $this->{$getKey};
                    } else {
                        throw new Exceptions\PortalPhpException(
                            sprintf('Trying to get non-accessible property "%s"', $getKey)
                        );
                    }
                    break;
            }
        }
    }

    /**
     * Return usable Project Name
     *
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    protected function finalProjectName()
    {
        return $this->projectName ?? $this->portalPhp->config('projectName');
    }

    /**
     * Return usable Content Schema
     *
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    protected function finalContentSchema()
    {
        return $this->contentSchema ?? $this->portalPhp->config('schemas.content');
    }

    /**
     * Return usable Folder Schema
     *
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    protected function finalFolderSchema()
    {
        return $this->folderSchema ?? $this->portalPhp->config('schemas.folder');
    }

    /**
     * Return usable Binary Content Schema
     *
     * @return \Illuminate\Config\Repository|mixed
     * @throws \Throwable
     */
    protected function finalBinaryContentSchema()
    {
        return $this->binaryContentSchema ?? $this->portalPhp->config('schemas.binarycontent');
    }

    /**
     * Get all accessible properties
     *
     * @return array
     */
    protected function allAccessibleProperties()
    {
        return $this->properties;
    }

    /**
     * Get a MeshClient instance based on the properties.
     *
     * @return MeshClient|null
     * @throws \Throwable
     */
    protected function meshClient()
    {
        if (!is_null($this->meshClient) && $this->meshClient instanceof MeshClient) {
            return $this->meshClient;
        } else {
            $adminFallback = config('portal.compatibility.noAdminClientByDefault', true) === false;
            return $this->adminClient ?
                $this->portalPhp->getAdminMeshClient() : $this->portalPhp->getMeshClient($adminFallback);
        }
    }

    /**
     * Prepare query and build the GraphQL
     *
     * @return mixed
     * @throws \Throwable
     */
    protected function prepareQuery()
    {
        $replace = [
            "%contentSchema%" => $this->finalContentSchema(),
            "%folderSchema%" => $this->finalFolderSchema(),
            "%binaryContentSchema%" => $this->finalBinaryContentSchema()
        ];

        $graphqlFile = $this->templateName . '.graphql';
        $placeholders = array_merge($replace, $this->queryPlaceholders, $this->placeholders);
        $variables = array_merge($this->queryVariables, $this->variables);

        if (empty($placeholders)) {
            $placeholders = [];
        }

        if (config('portal.meshNoWaitForIdle', true) === true) {
            $this->appendQueryParameters([
                'wait' => 'false'
            ]);
        }

        $query = app()->make(GraphqlLoader::class);
        $graphqlQuery = $query
            ->fromTemplate($graphqlFile)
            ->setPlaceholders($placeholders)
            ->setVariables($variables)
            ->buildAsQuery();
        return $graphqlQuery;
    }

    /**
     * Set version to published
     * @return $this
     */
    public function usePublishedVersion()
    {
        $this->appendQueryParameters([
            'version' => 'published'
        ]);
        return $this;
    }

    /**
     * Set "lang" graphql variable to the current user language, so it can be used in the query
     *
     * @throws Throwable
     */
    public function useMultilingualCurrentLanguageFilter(): void
    {
        if ($this->isMultilingualCurrentLanguageFilterEnabled()) {
            $this->appendQueryVariables([
                'lang' => [
                    $this->portalPhp->getUserLanguage(),
                ],
            ]);
        }
    }

    public function isMultilingualCurrentLanguageFilterEnabled(): bool
    {
        return config('portal.compatibility.useDynamicGraphqlLanguage', true) === true;
    }

    /**
     * Send the request to Mesh.
     *
     * @param $graphqlQuery
     * @return mixed
     * @throws \Throwable
     */
    protected function sendRequest($graphqlQuery)
    {
        $parameters = array_merge($this->queryParameters, $this->parameters);

        if (empty($parameters)) {
            $parameters = [];
        }

        $meshClient = $this->meshClient();
        $request = $meshClient->graphQL(
            $this->finalProjectName(),
            $graphqlQuery,
            $parameters
        );

        try {
            $response = $request->send();
        } catch (ClientException $e) {
            throw new HttpException($e->getCode(), $e->getMessage());
        }

        return Mesh::response($response);
    }

    /**
     * Append Placeholder replaces as array.
     *
     * @param array $placeholders
     */
    protected function appendQueryPlaceholders(array $placeholders)
    {
        $this->queryPlaceholders = array_merge($this->queryPlaceholders, $placeholders);
    }

    /**
     * Append query variables as array.
     *
     * @param array $variables
     */
    protected function appendQueryVariables(array $variables)
    {
        $this->queryVariables = array_merge($this->queryVariables, $variables);
    }

    /**
     * Append query parameters as array.
     *
     * @param array $parameters
     */
    protected function appendQueryParameters(array $parameters)
    {
        $this->queryParameters = array_merge($this->queryParameters, $parameters);
    }
}
