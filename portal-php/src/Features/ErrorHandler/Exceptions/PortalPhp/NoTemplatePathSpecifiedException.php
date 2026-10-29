<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhp;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;

class NoTemplatePathSpecifiedException extends PortalPhpException
{
    /**
     * NoTemplatePathSpecifiedException constructor.
     */
    public function __construct()
    {
        parent::__construct("No templateNamePath specified to determine templateName to render the current page");
    }
}
