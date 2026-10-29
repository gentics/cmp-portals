<?php

namespace Gentics\PortalPhp\Features\Redirect\Contract;

/**
 * Interface RedirectHelper
 * @package Gentics\PortalPhp\Features\Redirect\Contract
 */
interface RedirectHelper
{
    /**
     * Initialise the helper with the specified configuration.
     *
     * @param array $helperConfig The configuration for this helper.
     * @return mixed
     */
    public function init(array $helperConfig);

    /**
     * Convert the JSON data from Mesh to a redirect mapping.
     *
     * @param array $jsonData The loaded data from Mesh
     * @return mixed A mapping of URLs to their redirect destinations
     */
    public function convertData(array $jsonData);
}
