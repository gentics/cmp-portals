<?php

namespace Gentics\PortalPhp\Features\FormGenerator;

use Illuminate\Support\ServiceProvider as ServiceProviderIlluminate;
use Illuminate\Support\Facades\Blade;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\View;
use Illuminate\Cookie\Middleware\EncryptCookies;

class ServiceProvider extends ServiceProviderIlluminate
{
    /**
     * Bootstrap any application services.
     *
     * @return void
     */
    public function boot()
    {
        $this->loadRoutesFrom(realpath(__DIR__ . '/Http/routes.php'));
        $this->addGlobalViewVariables();
        $this->addBladeDirectives();
    }

    /**
     * Register any application services.
     *
     * @return void
     */
    public function register()
    {
        // Disable encryption for Forms related cookies to allow access them as-is.
        $this->app->resolving(EncryptCookies::class, function ($object) {
            $object->disableFor(FormGeneratorHandler::$COOKIE_NAME);
            $object->disableFor(FormGeneratorHandler::$COOKIE_NAME_FORMS);
            $object->disableFor(FormGeneratorHandler::$COOKIE_NAME_FORMS_POLL);
        });
    }

    /**
     * Add variables to views
     */
    public function addGlobalViewVariables()
    {
        // Inject Legacy form generator cookie as global view variable
        View::share('fgCookie', [
            'key' => FormGeneratorHandler::$COOKIE_NAME,
            'value' => FormGeneratorHandler::getFormCookie()
        ]);

        // Inject Form Generator cookie as global view variable
        View::share('formsCookie', [
            'key' => FormGeneratorHandler::$COOKIE_NAME_FORMS,
            'value' => FormGeneratorHandler::getFormsCookie()
        ]);

        // Inject Form Generator cookie as global view variable
        View::share('formsPollCookie', [
            'key' => FormGeneratorHandler::$COOKIE_NAME_FORMS_POLL,
            'value' => FormGeneratorHandler::getFormsPollCookie()
        ]);
    }

    /**
     * Add Blade directives
     *
     * @return void
     */
    public function addBladeDirectives()
    {
        // Add fgRender directive
        Blade::directive('fgRender', function ($expression): ?string {
            $expression = explode(',', $expression, 2);

            if (count($expression) > 1) {
                list($formUuid, $options) = $expression;
            } else {
                $options = '[]'; // empty array
                list($formUuid) = $expression;
            }

            return '<?php
                if (is_string(' . $formUuid . ')) {
                    $formGenerator = app(' . FormGeneratorHandler::class . '::class);
                    $formGenerator->setRenderPath("fg");

                    $formDataUuid  = ' . Arr::class . '::get(
                        ' . $options . ',
                        ' . FormGeneratorHandler::class . '::$COOKIE_NAME,
                        null
                    );

                    if (!is_null($formDataUuid)) {
                        echo $formGenerator->render(' . $formUuid . ', $formDataUuid);
                    } else {
                        echo $formGenerator->render(' . $formUuid . ');
                    }
                }
            ?>';
        });

        // Add renderForm directive
        Blade::directive('renderForm', function ($expression): ?string {
            $expression = explode(',', $expression, 2);

            if (count($expression) > 1) {
                list($formUuid, $options) = $expression;
            } else {
                $options = '[]'; // empty array
                list($formUuid) = $expression;
            }

            return '<?php
                if (is_string(' . $formUuid . ')) {
                    $formGenerator = app(' . FormGeneratorHandler::class . '::class);
                    $formGenerator->setRenderPath("forms");

                    $formDataUuid  = ' . Arr::class . '::get(
                        ' . $options . ',
                        ' . FormGeneratorHandler::class . '::$COOKIE_NAME_FORMS,
                        null
                    );

                    $language  = ' . Arr::class . '::get(
                        ' . $options . ',
                        "language",
                        null
                    );

                    $showResults  = ' . Arr::class . '::get(
                        ' . $options . ',
                        "showResults",
                        null
                    );

                    $templateContext  = ' . Arr::class . '::get(
                        ' . $options . ',
                        "templateContext",
                        null
                    );

                    $externalId  = ' . Arr::class . '::get(
                        ' . $options . ',
                        "externalId",
                        null
                    );

                    $pluginName  = ' . Arr::class . '::get(
                        ' . $options . ',
                        "pluginName",
                        null
                    );

                    echo $formGenerator->render(
                        ' . $formUuid . ',
                        $formDataUuid,
                        $language,
                        $showResults,
                        $templateContext,
                        $externalId,
                        $pluginName
                    );
                }
            ?>';
        });
    }
}
