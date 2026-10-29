<?php

namespace Gentics\PortalPhp\Features\Helpers;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Illuminate\Support\Traits\Macroable;

class Helper
{
    use Macroable {
        macro as protected traitMacro;
        mixin as protected traitMixin;
    }

    /**
     * Add a helper
     *
     * @param string $name Helper name to call with
     * @param object|callable $macro Helper function to call
     * @throws \Throwable
     * @return void
     */
    public static function add(string $name, $macro)
    {
        // Prevent silent helper overwrite if not from the same class origin
        throw_if(
            Helper::hasMacro($name) && !static::isSameOrigin($name, $macro),
            PortalPhpException::class,
            sprintf("Helper with %s method name already exists!", $name)
        );

        static::macro($name, $macro);
    }

    /**
     * Protected for internal use: Register a custom macro.
     *
     * @param string $name
     * @param object|callable $macro
     * @return void
     */
    protected static function macro($name, $macro)
    {
        static::traitMacro($name, $macro);
    }

    /**
     * Protected for internal use: Mix another object into the class.
     *
     * @param object $mixin
     * @param bool $replace
     * @throws \ReflectionException
     * @return void
     */
    protected static function mixin($mixin, $replace = true)
    {
        static::traitMixin($mixin, $replace);
    }

    /**
     * Checks if the macro is added from the same class or not.
     *
     * @param string $name Name of the macro
     * @param object|callable $macro The checked macro
     * @return bool
     */
    protected static function isSameOrigin(string $name, $macro)
    {
        if (is_array($macro) &&
            count($macro) === 2 &&
            is_array(static::$macros[$name]) &&
            count(static::$macros[$name]) === 2
        ) {
            $macroClass = get_class($macro[0]);
            $existingClass = get_class(static::$macros[$name][0]);

            $macroMethod = $macro[1];
            $existingMethod = static::$macros[$name][1];

            if ($macroClass === $existingClass && $macroMethod === $existingMethod) {
                return true;
            }
        }

        return false;
    }
}
