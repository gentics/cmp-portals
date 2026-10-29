<?php

namespace Gentics\PortalPhp\Console;

use Illuminate\Foundation\Console\Kernel as ConsoleKernel;

/**
 * Overridden Kernel implementation, to support OpenAPI generation.
 */
class BaseKernel extends ConsoleKernel
{
    /**
     * OpenAPI generation command
     */
    protected $commands = [
        \Knuckles\Scribe\Commands\GenerateDocumentation::class
    ];
}
