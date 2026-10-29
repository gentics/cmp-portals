<?php
namespace Gentics\PortalPhp\Features\Proxy\Filter;

use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;

class AddHeader implements \Proxy\Filter\FilterInterface
{
    protected $header;
    protected $value;

    /**
     * AddHeader constructor.
     * @param $header
     * @param $value
     */
    public function __construct($header, $value)
    {
        $this->header = $header;
        $this->value  = $value;
    }

    /**
     * @inheritdoc
     */
    public function __invoke(RequestInterface $request, callable $next): ResponseInterface
    {
        $request = $request->withHeader($this->header, $this->value);
        $response = $next($request);
        return $response;
    }
}
