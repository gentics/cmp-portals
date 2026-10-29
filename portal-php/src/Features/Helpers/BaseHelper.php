<?php

namespace Gentics\PortalPhp\Features\Helpers;

use Illuminate\Support\Str;
use ReflectionClass;
use ReflectionMethod;

abstract class BaseHelper
{
    /**
     * @var string Suffix of the helper functions to be auto-detected. This suffix will not exist on the Helper class.
     */
    protected static $helperMethodSuffix = 'Helper';

    /**
     * @var string Prefixes the helper function for the macro.
     */
    protected static $helperMacroPrefix = '';

    /**
     * BaseHelper constructor.
     * @throws \ReflectionException
     * @throws \Throwable
     */
    public function __construct()
    {
        $this->setupHelpers();
    }

    /**
     * Autoload helpers to the Helper class as macros without the $helperSuffix.
     *
     * @throws \ReflectionException
     * @throws \Throwable
     */
    protected function setupHelpers()
    {
        // Only load public functions
        $class_methods = (new ReflectionClass($this))->getMethods(ReflectionMethod::IS_PUBLIC);

        foreach ($class_methods as $class_method) {
            if (Str::endsWith($class_method->getName(), static::$helperMethodSuffix)) {
                $methodName = Str::replaceLast(static::$helperMethodSuffix, '', $class_method->getName());
                $finalMethodName = Str::camel(static::$helperMacroPrefix . '_' . $methodName);

                Helper::add(
                    Str::camel($finalMethodName),
                    [$this, $class_method->getName()]
                );
            }
        }
    }
}
