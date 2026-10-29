<?php

namespace Gentics\PortalPhp\Console\Commands;

use Illuminate\Console\Command;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\File;
use Knuckles\Scribe\Tools\WritingUtils;

/**
 * Generates the OpenAPI specification for Portal | php at build time, instead of on every request
 * (as \Gentics\PortalPhp\Http\Controllers\OpenApiController::generateYaml() does today by calling
 * Scribe from inside a controller action). The result is written to docs/content/portal-php/examples/api/openapi.yaml,
 * so Hugo picks it up as a static asset and it becomes part of the published documentation.
 *
 * Scribe already does static route introspection when invoked from the CLI - the only reason it
 * currently runs per-request is how OpenApiController wires it up, not a limitation of Scribe itself.
 */
class GenerateOpenApiDocs extends Command
{
    /**
     * @var string
     */
    protected $signature = 'portal:openapi:generate
        {--output= : Output path for openapi.yaml (default: docs/content/portal-php/examples/api/openapi.yaml)}';

    /**
     * @var string
     */
    protected $description = 'Generate the OpenAPI specification for the current API and publish it for the docs site';

    public function handle(): int
    {
        // Resolved relative to this file - not base_path() - because base_path() is whatever
        // application hosts this package (e.g. Testbench's own bundled skeleton app when this command
        // runs via `vendor/bin/testbench`, or a real host app's directory in production). "docs/" and
        // "config/scribe.php" are fixed siblings of "src/" within the Portal | php repository itself,
        // regardless of who is hosting the package at runtime.
        $packageRoot = __DIR__ . '/../../../';

        // config/scribe.php (this project's own Scribe configuration - route matching, auth, groups)
        // is meant to override Scribe's package defaults, but is not guaranteed to already be loaded by
        // whatever harness invokes this command (e.g. `vendor/bin/testbench`, which only auto-loads the
        // package's own config via ServiceProvider::mergeConfigFrom(), not this repo's top-level config
        // directory). Load it explicitly so route matching/auth/groups match what OpenApiController
        // would produce at runtime.
        $projectScribeConfig = $packageRoot . 'config/scribe.php';

        if (File::exists($projectScribeConfig)) {
            config(['scribe' => require $projectScribeConfig]);
        }

        // config/scribe.php hardcodes an absolute in-container path (/portal/bootstrap/cache/scribe/docs)
        // for the runtime controller's use. Force a project-relative, portable location instead, so this
        // command works the same in CI, locally, or in any container.
        $buildDir = storage_path('app/openapi-build');

        config([
            'scribe.type' => 'static',
            'scribe.static.output_path' => $buildDir,
            'scribe.openapi.enabled' => true,
            // config/scribe.php derives 'title' and 'description' from config('app.name'), which is only
            // meaningful for a real deployed instance; for the package's own reference spec, use a
            // stable, descriptive title regardless of whatever host app happens to run this command.
            'scribe.title' => config('scribe.title') ?: 'Gentics Portal | php',
            'scribe.description' => 'An OpenAPI v3.0 specification for Gentics Portal | php',
        ]);

        File::deleteDirectory($buildDir);

        WritingUtils::$httpMethodToCssColour['QUERY'] = 'grey';

        // Also relocate Scribe's own intermediate/cache directory (normally ".scribe" at the current
        // working directory) next to the build output, the same way OpenApiController::generateYaml()
        // already does for its in-container invocation - otherwise every run of this command leaves a
        // ".scribe" directory behind at the repository root.
        Artisan::call('scribe:generate', [
            '--force' => true,
            '--scribe-dir' => $buildDir . '/.scribe',
        ], $this->getOutput());

        $generatedSpec = $buildDir . '/openapi.yaml';

        if (!File::exists($generatedSpec)) {
            $this->error("Scribe did not produce an openapi.yaml at {$generatedSpec}");

            return self::FAILURE;
        }

        $target = $this->option('output') ?: $packageRoot . 'docs/content/portal-php/examples/api/openapi.yaml';

        File::ensureDirectoryExists(dirname($target));
        File::copy($generatedSpec, $target);

        $this->info("OpenAPI specification written to {$target}");

        return self::SUCCESS;
    }
}
