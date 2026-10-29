<?php

namespace Gentics\PortalPhp\Features\Search;

use Illuminate\Support\Arr;
use Gentics\PortalPhp\Features\Helpers\BaseHelper;

class SearchHelper extends BaseHelper
{
    /**
     * Normalize search hit by remapping keys to standard GraphQL response
     *
     * @param array $hit Search hit array
     * @return array Search hit array with remapped keys
     */
    public function normalizeSearchHitHelper(array $hit): array
    {
        // Translate URL as Path
        if (Arr::has($hit, 'url')) {
            $hit['path'] = Arr::get($hit, 'url');
        }

        // Merge raw and additional fields in the standard fields key
        // The raw fields will override additional fields
        $hit['fields'] = array_merge(
            Arr::get($hit, 'additional', []),
            Arr::get($hit, 'raw.fields', [])
        );

        return $hit;
    }
}
