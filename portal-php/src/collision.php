<?php

/*
 * Replace the final console ExceptionHandler adapter of nunomaduro/collision with a copy that forwards the
 * Portal | php Handler methods, see \Gentics\PortalPhp\Features\ErrorHandler\CollisionExceptionHandler.
 * Loaded via composer "autoload.files", so the alias exists before nunomaduro/collision can load its own class.
 */
if (!class_exists('NunoMaduro\Collision\Adapters\Laravel\ExceptionHandler', false)) {
    class_alias(
        \Gentics\PortalPhp\Features\ErrorHandler\CollisionExceptionHandler::class,
        'NunoMaduro\Collision\Adapters\Laravel\ExceptionHandler'
    );
}
