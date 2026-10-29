<?php

namespace Gentics\PortalPhp\Features\Redirect;

use Gentics\PortalPhp\Features\Redirect\Contract\RedirectHelper;
use Illuminate\Support\Arr;
use JsonMapper;

/**
 * Class BasicRedirectHelper
 * @package Gentics\PortalPhp\Features\Redirect
 */
class BasicRedirectHelper implements RedirectHelper
{
    protected $fieldName = 'addredirecttag';

    /**
     * {@inheritdoc}
     * @param array $helperConfig
     * @return mixed|void
     */
    public function init(array $helperConfig)
    {
        if (Arr::has($helperConfig, 'fieldName')) {
            $this->fieldName = Arr::get($helperConfig, 'fieldName');
        }
    }

    /**
     * {@inheritdoc}
     * @param array $jsonData
     * @return array|mixed
     */
    public function convertData(array $jsonData)
    {
        $redirectsMap = [];

        // Try to get the fieldName field and parse JSON data
        $rawData = json_decode(Arr::get($jsonData, implode('.', ['fields', $this->fieldName]), ''));

        if (!empty($rawData)) {
            $mapper = new JsonMapper();
            $redirects = $mapper->mapArray($rawData, [], Redirect::class);

            array_map(function (Redirect $item) use (&$redirectsMap) {
                $redirectsMap[$item->getOldUrl()] = $item;
            }, $redirects);
        }

        return $redirectsMap;
    }
}
