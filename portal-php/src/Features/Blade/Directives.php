<?php


namespace Gentics\PortalPhp\Features\Blade;

use Illuminate\Support\Facades\View;
use Illuminate\Support\Facades\Blade;

/**
 * A collection of additional directives for Blade templates
 */
class Directives
{
    /**
     * Blade directive @eval enables you to dynamically render blade code from strings and
     * also handles the caching.
     * @param string $expression
     * @return string
     */
    public static function evalDirective(string $expression)
    {
        $bladeCompiler = resolve('blade.compiler');
        $strippedExpression = $bladeCompiler->stripParentheses($expression);

        return "<?php echo " . __CLASS__ . "::renderBladeString({$strippedExpression},"
         . "\Illuminate\Support\Arr::except(get_defined_vars(),['__data', '__path'])); ?>";
    }

    /**
     * @param string $bladeCode
     * @param array $data
     * @return \Illuminate\Contracts\View\View
     */
    public static function renderBladeString($bladeCode = "", array $data = [], array $mergeData = [])
    {
        if ($bladeCode == "" || !is_string($bladeCode)) {
            return "";
        }

        return Blade::render(
            $bladeCode,
            array_merge($mergeData, $data),
            deleteCachedView: config('portal.cache.blade.deleteCachedView', false)
        );
    }
}
