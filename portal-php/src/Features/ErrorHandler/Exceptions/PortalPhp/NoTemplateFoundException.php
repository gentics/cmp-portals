<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhp;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;

class NoTemplateFoundException extends PortalPhpException
{
    /**
     * NoTemplateFoundException constructor.
     * @param string $templatePath
     */
    public function __construct(string $templatePath)
    {
        parent::__construct("No template found in " . $templatePath . " to render the Mesh content");
    }
}
