<?php

namespace Gentics\PortalPhp\Features\ErrorHandler;

use Gentics\PortalPhp\Features\ErrorHandler\Contract\ErrorHandler;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadError;
use Gentics\PortalPhp\PortalPhp;
use Gentics\PortalPhp\Traits\NeedsNavigation;

use Illuminate\Foundation\Exceptions\Handler as ExceptionHandler;
use Illuminate\Contracts\Container\Container;
use Illuminate\Support\Arr;
use Illuminate\Support\Str;
use Illuminate\View\ViewException;

use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Exception\HttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

use GuzzleHttp\Exception\ClientException;

use Throwable;
use Exception;

/**
 * Class Handler
 * @package Gentics\PortalPhp\Exceptions
 */
class Handler extends ExceptionHandler
{
    use NeedsNavigation;

    protected $levels = [
        //
    ];

    protected $dontFlash = [
        //
    ];

    /**
     * @var array Additional error handlers
     */
    protected $featureErrorHandler = [];

    /**
     * @var PortalPhp
     */
    protected $portalPhp;

    public function __construct(Container $container, PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
        parent::__construct($container);
    }

    /**
     * {@inheritdoc}
     */
    public function register()
    {
        $this->reportable(function (\Throwable $e) {
            //
        });
    }

    /**
     * Render an exception into an HTTP response.
     *
     * @param \Illuminate\Http\Request $request
     * @param  \Throwable  $exception
     * @return \Illuminate\Http\Response|\Symfony\Component\HttpFoundation\Response
     * @throws \Throwable
     */
    public function render($request, Throwable $exception)
    {
        $response = null;

        // We need to Fetch out the NotFoundException otherwise it'll always be a 505 when abort is called
        // inside of a view
        if ($exception instanceof ViewException && $exception->getPrevious() instanceof NotFoundHttpException) {
            $exception = $exception->getPrevious();
        }

        if ($errorHandlerResponse = $this->sendThroughErrorHandlers($request, $exception)) {
            return $errorHandlerResponse;
        }

        // Check if debugging is enabled or if the exception is a server side error
        // In debug mode, we let Laravel handle server side errors
        if (!config('app.debug') &&
            (
                $exception instanceof HttpException ||
                $exception instanceof ClientException ||
                $exception instanceof ViewException ||
                $exception instanceof \ErrorException ||
                $exception instanceof Exceptions\MeshException
            )
        ) {
            try {
                $response = $this->renderExceptionPage($exception);
            } catch (Throwable $e) {
                $message = "Catched exception \"" . $e->getMessage()
                    . "\" in exception handler while handling an exception";

                $exceptionException = new Exceptions\PortalPhpException($message, $e->getCode(), $exception);
                return parent::render($request, $exceptionException);
            }
        }

        if ($response === null) {
            if (config('app.debug') && $this->isHttpException($exception)) {
                $response = $this->renderDebugException($exception);
            } else {
                $response = parent::render($request, $exception);
            }
        }

        if (config('app.debugForceLog', false)) {
            \Log::debug(
                sprintf("[ERROR HANDLER] [FORCE LOG] %s %s %s", $request->getMethod(), $request->getUri(), $exception)
            );
        }

        // No error page found in Mesh, hand over the exception handling to something else
        return $response;
    }

    /**
     * Return debug data
     *
     * @param Exception $exception
     * @return \Illuminate\Http\Response
     */
    public function renderDebugException(Exception $exception)
    {
        return $this->toIlluminateResponse($this->convertExceptionToResponse($exception), $exception);
    }

    /**
     * @param \Throwable $exception
     * @return \Illuminate\Http\Response|null
     * @throws \Throwable
     */
    public function renderExceptionPage(Throwable $exception)
    {
        $exceptionHandlerConfig = $this->portalPhp->config("exceptionHandler");
        $errorPages = [];
        $httpStatusCode = Response::HTTP_INTERNAL_SERVER_ERROR;

        if ($exception instanceof HttpException) {
            $httpStatusCode = $exception->getStatusCode();
        }

        // Error pages for specific HTTP codes
        if (!empty($exceptionHandlerConfig['httpCodes'][$httpStatusCode])) {
            $errorPage = $exceptionHandlerConfig['httpCodes'][$httpStatusCode];

            if (is_string($errorPage)) {
                array_push($errorPages, $errorPage);
            }
        }

        // Default error page(s)
        if (!empty($exceptionHandlerConfig['default'])) {
            $errorPageOrFolder = $exceptionHandlerConfig['default'];

            if (is_string($errorPageOrFolder)) {
                if (Str::endsWith($errorPageOrFolder, '/')) {
                    array_push($errorPages, $errorPageOrFolder . $httpStatusCode . '.html');
                    array_push($errorPages, $errorPageOrFolder . 'default.html');
                } else {
                    array_push($errorPages, $errorPageOrFolder);
                }
            }
        }

        $errorPages = array_unique($errorPages);

        // Try to load an error page from Mesh
        foreach ($errorPages as $errorPage) {
            $responseData = $this->getErrorPage($errorPage);
            $data = Arr::get($responseData, 'data');
            if (empty($responseData['errors']) && !is_null($data['node'])) {
                $errorPageData = $this->getLocalizedErrorPage($data);
                // Make sure, that this is a content page
                if (Arr::get($errorPageData, 'node.schema.name') === $this->portalPhp->config('schemas.content')) {
                    // Add navigation to the response
                    $errorPageData = $this->addNavigation($errorPageData);

                    return response()->view(
                        $this->portalPhp->getTemplateName($errorPageData),
                        array_merge($errorPageData, ["exception" => $exception]),
                        $httpStatusCode
                    );
                }
            }
        }

        return null;
    }

    /**
     * Get cached error page as an array
     *
     * @param string $path
     * @return mixed
     */
    protected function getErrorPage(string $path)
    {
        return Mesh::request(LoadError::class)
            ->setPath($path)
            ->setAbortOnError(false)
            ->setAdminClient(true)
            ->fetchCached()
            ->toArray();
    }

    /**
     * Return correct localized error page with fallback
     *
     * @param array $errorPageResponse
     * @return array|mixed
     * @throws \Throwable
     */
    protected function getLocalizedErrorPage(array $errorPageResponse)
    {
        $localizedErrorPages = Arr::pluck(
            Arr::get($errorPageResponse, 'node.languages'),
            'path',
            'language'
        );

        $preferredLanguage = $this->portalPhp->getPreferredLanguage(array_keys($localizedErrorPages));

        if (!is_null($preferredLanguage) && $preferredLanguage !== Arr::get($errorPageResponse, 'node.language')) {
            return Arr::get($this->getErrorPage($localizedErrorPages[$preferredLanguage]), 'data');
        }

        return $errorPageResponse;
    }

    /**
     * @param $request
     * @param \Throwable $exception
     * @return bool|mixed
     * @throws \Illuminate\Contracts\Container\BindingResolutionException
     */
    protected function sendThroughErrorHandlers($request, \Throwable $exception)
    {
        foreach ($this->featureErrorHandler as $errorHandler) {
            if (! is_string($errorHandler)) {
                continue;
            }

            $instance = app()->make($errorHandler);

            if ($instance instanceof ErrorHandler) {
                if ($errorHandlerResponse = $instance->handle($request, $exception)) {
                    return $errorHandlerResponse;
                }
            }
        }

        return false;
    }

    /**
     * Determine if there is a given errorHandler.
     *
     * @param  string  $errorHandler
     * @return bool
     */
    public function hasErrorHandler($errorHandler)
    {
        return in_array($errorHandler, $this->featureErrorHandler);
    }

    /**
     * Add a new errorHandler to beginning of the stack if it does not already exist.
     *
     * @param  string  $errorHandler
     * @return $this
     */
    public function prependErrorHandler($errorHandler)
    {
        if (array_search($errorHandler, $this->featureErrorHandler) === false) {
            array_unshift($this->featureErrorHandler, $errorHandler);
        }
        return $this;
    }

    /**
     * Add a new errorHandler to end of the stack if it does not already exist.
     *
     * @param  string  $errorHandler
     * @return $this
     */
    public function pushErrorHandler($errorHandler)
    {
        if (array_search($errorHandler, $this->featureErrorHandler) === false) {
            $this->featureErrorHandler[] = $errorHandler;
        }
        return $this;
    }
}
