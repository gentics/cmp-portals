<?php

namespace Gentics\PortalPhp\Features\PortalBlade;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;

class ProjectRoot
{
    /**
     * Returns the project name from the assigned environment variable
     *
     * @return mixed|null
     * @throws \Throwable
     */
    public function getProjectName()
    {
        $portalBladeProject = isset($_SERVER['GPPHP_BLADE_PROJECT']) ? $_SERVER['GPPHP_BLADE_PROJECT'] : null;

        throw_if(
            !app()->runningInConsole() && is_null($portalBladeProject),
            Exceptions\PortalPhpException::class,
            "GPPHP_BLADE_PROJECT environment variable must be set in the VirtualHost"
        );

        return $portalBladeProject;
    }

    /**
     * Returns the project root path based on the project environment variable
     *
     * @return string
     * @throws \Throwable
     */
    public function getProjectRoot()
    {
        $projectName = $this->getProjectName();
        return base_path() . '/projects/' . $projectName;
    }

    /**
     * Returns the project mode based on the environment variable or fallback to folder
     *
     * @return string
     * @throws \Throwable
     */
    public function getProjectMode()
    {
        $portalBladeMode = isset($_SERVER['GPPHP_BLADE_MODE']) ? $_SERVER['GPPHP_BLADE_MODE'] : 'folder';
        return $portalBladeMode;
    }
}
