<?php
namespace Gentics\PortalPhp\Features\Proxy\Filter;

use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;

class AddAuthorizationHeader implements \Proxy\Filter\FilterInterface
{
    protected $token;

    /**
     * AddAuthorizationHeader constructor.
     * @param string $token
     */
    public function __construct($token = null)
    {
        $this->token = $token;
    }

    /**
     * @inheritdoc
     */
    public function __invoke(RequestInterface $request, callable $next): ResponseInterface
    {
        /*
         * remove existing Authentication header first - i.e. when authentication basic is used
         * (i.e. htaccess access protection) this authentication would be picked up by the proxy
         */
        if ($request->hasHeader('Authorization')) {
            $request = $request->withoutHeader('Authorization');
        }

        if (isset($this->token)) {
            $request = $request->withHeader('Authorization', "Bearer " . $this->token);
        }

        $response = $next($request);
        return $response;
    }
}
