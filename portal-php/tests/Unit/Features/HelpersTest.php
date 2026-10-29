<?php

namespace Gentics\PortalPhp\Tests\Unit\Features;

use Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException;
use Gentics\PortalPhp\Features\Helpers\BaseHelper;
use Gentics\PortalPhp\Features\Helpers\Helper;
use Gentics\PortalPhp\Tests\TestCase;
use Illuminate\Support\Facades\View;

class HelpersTest extends TestCase
{
    protected static $testString = 'PortalPhpTest';

    /**
     * Test adding helper as macro.
     *
     * @throws \Throwable
     */
    public function testHelperClass()
    {
        $functionName = 'testFunction';

        // Clean up all macros
        $this->resetHelperMacros();

        // Should not exist
        $this->assertFalse(Helper::hasMacro($functionName));

        // Should exist and return correct data
        $this->addTestHelper($functionName);
        $this->assertTrue(Helper::hasMacro($functionName));
        $this->assertEquals(static::$testString, Helper::testFunction());

        // Should throw exception
        $this->expectException(PortalPhpException::class);
        $this->addTestHelper($functionName);
    }

    /**
     * Test adding helper by class.
     */
    public function testHelperBaseClass()
    {
        $functionName = 'myTest';

        // Clean up all macros
        $this->resetHelperMacros();

        // Should not exist
        $this->assertFalse(Helper::hasMacro($functionName));

        // Test Helper Class
        new class() extends BaseHelper {
            public function myTestHelper(string $string)
            {
                return $string;
            }
        };

        // Test Helper Class with Prefix and Custom Suffix
        new class() extends BaseHelper {
            protected static $helperMethodSuffix = 'Utility';
            protected static $helperMacroPrefix = 'test';

            public function myTestUtility(string $string)
            {
                return $string;
            }
        };

        // Should exist and return correct data (Helper Class)
        $this->assertTrue(Helper::hasMacro($functionName));
        $this->assertEquals(static::$testString, Helper::myTest(static::$testString));

        // Should exist and return correct data (Helper Class with Prefix and Custom Suffix)
        $this->assertTrue(Helper::hasMacro('testMyTest'));
        $this->assertEquals(static::$testString, Helper::testMyTest(static::$testString));
    }

    /**
     * Shorthand function to add test helper
     *
     * @param $name
     * @throws \Throwable
     */
    protected function addTestHelper($name)
    {
        $testString = static::$testString;
        Helper::add($name, function () use ($testString) {
            return $testString;
        });
    }
}
