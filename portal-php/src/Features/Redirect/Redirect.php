<?php

namespace Gentics\PortalPhp\Features\Redirect;

/**
 * Class Redirect
 * @package Gentics\PortalPhp\Features\Redirect
 */
class Redirect
{
    /**
     * @var string The old url to be redirected (only the path)
     */
    private $oldUrl;

    /**
     * @var string The target url to redirect
     */
    private $newUrl;

    /**
     * @var bool The redirect type
     */
    private $permanent;

    public function getOldUrl()
    {
        return $this->oldUrl;
    }

    public function setOldUrl(string $oldUrl)
    {
        $this->oldUrl = RedirectHandler::normalizeUrl($oldUrl);
    }

    public function getNewUrl()
    {
        return $this->newUrl;
    }

    public function setNewUrl(string $newUrl)
    {
        $this->newUrl = $newUrl;
    }

    public function getPermanent()
    {
        return $this->permanent;
    }

    public function setPermanent(bool $permanent)
    {
        $this->permanent = $permanent;
    }
}
