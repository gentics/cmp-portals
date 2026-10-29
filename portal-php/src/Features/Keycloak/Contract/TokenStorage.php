<?php

namespace Gentics\PortalPhp\Features\Keycloak\Contract;

use Illuminate\Http\Request;

interface TokenStorage
{
    /**
     * Parse Token in request
     *
     * @param Request $request
     * @return mixed
     */
    public function parse(Request $request);

    /**
     * Stores Authentication token
     *
     * @param $token
     */
    public function store($token): void;

    /**
     * Forgets Authentication token
     */
    public function forget(): void;
}
