<?php
namespace Gentics\PortalPhp\Features\Proxy\Filter;

use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;
use Gentics\PortalPhp\PortalPhp;

class FilterRequestCookies implements \Proxy\Filter\FilterInterface
{
    protected $allowList = [];

    /**
     * FilterRequestCookies constructor.
     * @param $header
     * @param $value
     */
    public function __construct($allowList = [])
    {
        $this->allowList = $allowList;
    }

    /**
     * @inheritdoc
     */
    public function __invoke(RequestInterface $request, callable $next): ResponseInterface
    {
        $requestCookies = $request->getCookieParams();
        $allowedCookies = [];
        $allowedCookiesHeader = "";

        $request = $request->withoutHeader('cookie');

        foreach ($requestCookies as $cookieName => $cookieValue) {
            if (in_array($cookieName, $this->allowList)) {
                $allowedCookies[$cookieName] = $cookieValue;
            }
        }

        $request = $request->withCookieParams($allowedCookies);

        if (!empty($allowedCookies)) {
            $allowedCookiesHeader = PortalPhp::cookieHeaderFromArray($allowedCookies);
            $request = $request->withHeader('cookie', $allowedCookiesHeader);
        }

        $response = $next($request);
        return $response;
    }
}
