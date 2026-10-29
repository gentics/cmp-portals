<?php
namespace Gentics\PortalPhp\Features\Proxy\Filter;

use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;
use Illuminate\Support\Str;

class RemoveRoutePath implements \Proxy\Filter\FilterInterface
{
    protected $extraPath;

    /**
     * @param string $extraPath Removes extra path from the beginning
     */
    public function __construct($extraPath = null)
    {
        $this->extraPath = $extraPath;
    }

    /**
     * @inheritdoc
     */
    public function __invoke(RequestInterface $request, callable $next): ResponseInterface
    {
        $routeStaticPrefix = \Request::route()->getCompiled()->getStaticPrefix();
        
        if (!is_null($this->extraPath)) {
            $routeStaticPrefix .= $this->extraPath;
        }

        $replacedUri = Str::replaceFirst($routeStaticPrefix, '', $request->getUri()->getPath());

        $request = $request->withUri(
            $request->getUri()->withPath($replacedUri)
        );

        $response = $next($request);
        return $response;
    }
}
