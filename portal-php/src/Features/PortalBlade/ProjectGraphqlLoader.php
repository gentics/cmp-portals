<?php
namespace Gentics\PortalPhp\Features\PortalBlade;

use Gentics\PortalPhp\Features\Mesh\GraphqlLoader;

class ProjectGraphqlLoader extends GraphqlLoader
{
    /**
     * @var null|ProjectRoot $projectRoot
     */
    protected $projectRoot = null;

    /**
     * ProjectGraphqlLoader constructor.
     * @param string|null $filename
     * @param array|null $replace
     * @param array|null $variables
     * @throws \Throwable
     */
    public function __construct(?string $filename = null, ?array $replace = null, ?array $variables = null)
    {
        $this->projectRoot = app()->make(ProjectRoot::class);
        parent::__construct($filename, $replace, $variables);
    }

    /**
     * @inheritDoc
     */
    protected function templatePath(string $path)
    {
        $projectRoot = $this->projectRoot->getProjectRoot();
        $projectGraphqlPath = $projectRoot . '/resources/graphql/' . $path;

        if (file_exists($projectGraphqlPath)) {
            return $projectGraphqlPath;
        } else {
            return resource_path('graphql/' . $path);
        }
    }
}
