<?php

namespace Gentics\PortalPhp\Features\Redirect;

use BenSampo\Enum\Enum;

/**
 * Class RedirectsSourceMode
 * Options for the source of custom redirect mappings.
 * @package Gentics\PortalPhp\Features\Redirect
 */
class RedirectsSourceMode extends Enum
{
    /** The page containing the redirect map is fetched by the data provider. */
    const DATAPROVIDER = 0;

    /** The page containing the redirect map is fetched only at the first request. */
    const LOADONCE = 1;

    /** The page containing the redirect map is fetched for every request. */
    const RELOAD = 2;
}
