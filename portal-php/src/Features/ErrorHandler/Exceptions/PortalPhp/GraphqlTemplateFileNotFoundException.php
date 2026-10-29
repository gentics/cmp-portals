<?php

namespace Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhp;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;

class GraphqlTemplateFileNotFoundException extends PortalPhpException
{
    /**
     * GraphqlTemplateFileNotFoundException constructor.
     * @param string $graphqlFile
     */
    public function __construct(string $graphqlFile)
    {
        parent::__construct("GraphQL query template " . $graphqlFile . " not found");
    }
}
