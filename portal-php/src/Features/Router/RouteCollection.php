<?php

namespace Gentics\PortalPhp\Features\Router;

use Illuminate\Routing\RouteCollection as IlluminateRouteCollection;

class RouteCollection extends IlluminateRouteCollection
{
    /**
     * {@inheritdoc}
     */
    protected function matchAgainstRoutes(array $routes, $request, $includingMethod = true)
    {
        // Prioritize routes
        uasort($routes, [ $this, 'comparePriority' ]);
        return parent::matchAgainstRoutes($routes, $request, $includingMethod);
    }

    /**
     * {@inheritdoc}
     */
    protected function addToCollections($route)
    {
        $priority = RouterPriority::DEFAULT - (count($this->allRoutes) / 10000000);

        $route->priority($priority);

        $domainAndUri = $route->getDomain().$route->uri().$route->getPriority();

        foreach ($route->methods() as $method) {
            $this->routes[$method][$domainAndUri] = $route;
        }

        $this->allRoutes[$method.$domainAndUri] = $route;
    }

    /**
     * Comparator for the routes priority
     *
     * @param $r1
     * @param $r2
     * @return int
     */
    protected function comparePriority($r1, $r2)
    {
        $a = $r1->getPriority();
        $b = $r2->getPriority();
        if ($a == $b) {
            return 0;
        }
        return ($a < $b) ? 1 : -1;
    }
}
