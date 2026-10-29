<?php
namespace Gentics\PortalPhp\Features\Mesh;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Str;
use Illuminate\Support\Arr;

/**
 * Class GraphqlLoader
 * @package Gentics\PortalPhp\Features\Mesh
 */
class GraphqlLoader
{
    protected static $fragmentIncludeRegex = "/(?!\.{3}[\s]+on[\s]+)(\.{3}[\s]*[_A-Za-z][_0-9A-Za-z]*)[\s]?$/m";
    protected static $fragmentDeclareRegex = "/fragment[\s]+([_A-Za-z][_0-9A-Za-z]*)[\s]+on[\s]+.+[\s]*{/m";

    /**
     * @var bool Defines built queries should cached or not
     */
    protected $cache = true;

    /**
     * @var string Cache key based on the base query
     */
    protected $cacheKey;

    /**
     * @var string GraphQL template
     */
    protected $template;

    /**
     * @var string GraphQL template with all dependencies
     */
    protected $templateResolved;

    /**
     * @var array GraphQL placeholders
     */
    protected $placeholders = [];

    /**
     * @var array GraphQL variables
     */
    protected $variables;

    /**
     * @var array All included files for the current query
     */
    protected $includedFiles = [];

    /**
     * GraphqlLoader constructor.
     * @param null|string $filename
     * @param array|null $replace
     * @param array|null $variables
     * @throws \Throwable
     */
    public function __construct(?string $filename = null, ?array $replace = null, ?array $variables = null)
    {
        if (!is_null($filename)) {
            $this->fromTemplate($filename);
        }

        if (!is_null($replace)) {
            $this->setPlaceholders($replace);
        }

        if (!is_null($variables)) {
            $this->setVariables($variables);
        }
    }

    /**
     * Load GraphQL template from file.
     *
     * @param string $filename
     * @return $this
     * @throws \Throwable
     */
    public function fromTemplate(string $filename): GraphqlLoader
    {
        $this->cacheKey = 'gql-file-' . sha1($this->templatePath($filename));
        $this->template = $this->loadQueryTemplate($filename);
        return $this;
    }

    /**
     * Load GraphQL template from string.
     *
     * @param string $graphql
     * @return GraphqlLoader
     */
    public function fromString(string $graphql): GraphqlLoader
    {
        $this->cacheKey = 'gql-string-' . sha1($graphql);
        $this->template = $graphql;
        return $this;
    }

    /**
     * Set an array of placeholders
     *
     * @param array $replace
     * @return GraphqlLoader
     */
    public function setPlaceholders(array $replace = []): GraphqlLoader
    {
        foreach ($replace as $key => $value) {
            $this->placeholders[$key] = $value;
        }
        return $this;
    }

    /**
     * Set a placeholder
     *
     * @param string $key
     * @param string $value
     * @return GraphqlLoader
     */
    public function setPlaceholder(string $key, string $value): GraphqlLoader
    {
        $this->setPlaceholders([
            $key => $value
        ]);

        return $this;
    }

    /**
     * Set GraphQL variables
     *
     * @param array $variables
     * @return GraphqlLoader
     */
    public function setVariables(array $variables): GraphqlLoader
    {
        $this->variables = $variables;
        return $this;
    }

    /**
     * Set caching
     *
     * @param bool $value
     * @return $this
     */
    public function useCache(bool $value)
    {
        $this->cache = $value;
        return $this;
    }

    /**
     * Check if query files are changed, that means cache expired.
     *
     * @param array $cachedFiles
     * @return bool
     */
    protected function isExpired(array $cachedFiles)
    {
        foreach ($cachedFiles as $file => $modified) {
            $isModified = @filemtime($file);
            if (!$isModified || $isModified > $modified) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build GraphQL query from template including all fragments required and use
     * cached version if its possible.
     *
     * @return string Returns the GraphQL query string
     * @throws \Throwable
     */
    public function build()
    {
        throw_if(
            empty($this->template),
            Exceptions\PortalPhpException::class,
            "The template must be set for the GraphQL query!"
        );

        // If cache enabled, try to load template from here first
        if ($this->cache) {
            $builtTemplate = Cache::rememberForever($this->cacheKey, function () {
                $templateResolved = $this->buildDependencies($this->template);
                return [
                    'includedFiles' => $this->includedFiles,
                    'templateResolved' => $templateResolved
                ];
            });
        }

        // Check if cached template exists (if enabled) and if its not expired use that data
        if (isset($builtTemplate) && !$this->isExpired(Arr::get($builtTemplate, 'includedFiles'))) {
            $this->templateResolved = Arr::get($builtTemplate, 'templateResolved');
        } else {
            $this->templateResolved = $this->buildDependencies($this->template);

            // If cache enabled, write data
            if ($this->cache) {
                Cache::forever($this->cacheKey, [
                    'includedFiles' => $this->includedFiles,
                    'templateResolved' => $this->templateResolved
                ]);
            }
        }

        $this->templateResolved = $this->replacePlaceholders($this->templateResolved);

        return $this->templateResolved;
    }

    /**
     * Build GraphQL dependencies by iterating until every dependency found.
     *
     * @param string $template
     * @return string
     * @throws \Throwable
     */
    protected function buildDependencies(string $template)
    {
        $missingFragments = [];
        $templateResolved = $template;

        do {
            $templateBefore = $templateResolved;
            $dependenciesResolved = $this->resolveDependencies($templateResolved, $missingFragments);
        } while ($templateBefore !== $templateResolved);

        throw_if(
            !$dependenciesResolved,
            Exceptions\PortalPhpException::class,
            sprintf(
                "Cannot solve all dependencies for the GraphQL template, missing fragment(s): %s",
                implode(', ', $missingFragments)
            )
        );

        return $templateResolved;
    }

    /**
     * Build GraphQL query and return as query array to pass to Mesh Client
     *
     * @return array Return the GraphQL query string with params
     * @throws \Throwable
     */
    public function buildAsQuery()
    {
        $variables = $this->variables;
        if (empty($variables)) {
            $variables = null;
        }

        return [
            "query" => $this->build(),
            "variables" => $variables
        ];
    }

    /**
     * Return built GraphQL query as string
     *
     * @return string Builds and Returns the GraphQL query string
     * @throws \Throwable
     */
    public function __toString()
    {
        return $this->build();
    }

    /**
     * Returns template path
     *
     * @param string $path
     * @return string
     */
    protected function templatePath(string $path)
    {
        return resource_path('graphql/' . $path);
    }

    /**
     * Loads a query template file from the resources.
     *
     * @param string $path
     * @return bool|string
     * @throws \Throwable
     */
    protected function loadQueryTemplate(string $path)
    {
        if (!Str::endsWith($path, ".graphql")) {
            $path .= ".graphql";
        }

        $templatePath = $this->templatePath($path);

        throw_if(
            !file_exists($templatePath),
            Exceptions\PortalPhp\GraphqlTemplateFileNotFoundException::class,
            $path
        );

        $template = file_get_contents($templatePath);

        if (!Arr::has(array_keys($this->includedFiles), $templatePath)) {
            $this->includedFiles[$templatePath] = filemtime($templatePath);
        }

        return $template;
    }

    /**
     * Append a fragment at the end of the template
     *
     * @param string $template
     * @param string $fragmentName
     * @throws \Throwable
     * @return string
     */
    protected function appendFragment(string $template, string $fragmentName)
    {
        $fragment = $this->loadQueryTemplate('fragments/' . $fragmentName);
        $declarations = $this->matchDeclarations($fragment);

        throw_if(
            !in_array($fragmentName, $declarations),
            Exceptions\PortalPhpException::class,
            sprintf('Declaration "%s" not found in fragment query!', $fragmentName)
        );

        $template .= $fragment;
        return $template;
    }

    /**
     * Replace the applied placeholders in the template
     *
     * @param string $template
     * @return mixed
     */
    protected function replacePlaceholders(string $template)
    {
        return str_replace(
            array_keys($this->placeholders),
            array_values($this->placeholders),
            $template
        );
    }

    /**
     * Returns all fragments used in a template
     *
     * @param string $template GraphQL template
     * @return array Unique list of matched fragments
     */
    protected function matchFragments(string $template)
    {
        $fragmentMatches = [];
        preg_match_all(static::$fragmentIncludeRegex, $template, $fragmentMatches);

        $fragments = array_keys(array_flip(array_map(function ($item) {
            return trim(str_replace("...", "", $item));
        }, $fragmentMatches[1])));

        return $fragments;
    }

    /**
     * Return all fragment declaration names in a template
     *
     * @param string $template
     * @return array Unique list of matched fragment declarations
     */
    protected function matchDeclarations(string $template)
    {
        $declareMatches = [];
        preg_match_all(static::$fragmentDeclareRegex, $template, $declareMatches);
        $declarations = array_keys(array_flip(array_map(function ($item) {
            return trim($item);
        }, $declareMatches[1])));

        return $declarations;
    }

    /**
     * Resolve fragment dependencies in template, and return false if there was missing dependencies,
     * otherwise return true when every dependency was found.
     *
     * @param string $template
     * @param array $missingFragments
     * @return bool
     * @throws \Throwable
     */
    protected function resolveDependencies(string &$template, array &$missingFragments = [])
    {
        $fragments = $this->matchFragments($template);
        $declarations = $this->matchDeclarations($template);

        $missingFragments = array_diff($fragments, $declarations);

        if (!empty($missingFragments)) {
            foreach ($missingFragments as $fragment) {
                $template = $this->appendFragment($template, $fragment);
            }
            return false;
        }

        return true;
    }
}
