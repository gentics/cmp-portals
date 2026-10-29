<?php

namespace Gentics\PortalPhp\Features\HttpCache\Http\Controllers;

use Illuminate\Routing\Controller as BaseController;
use Illuminate\Support\Facades\Cookie;
use Illuminate\Support\Str;

use Gentics\PortalPhp\Features\Mesh\Mesh;
use Gentics\PortalPhp\Features\Mesh\GraphqlRequests\LoadUser;
use Gentics\PortalPhp\PortalPhp;

class UserContextController extends BaseController
{
    protected $defaultContextVariables = [
        'roleshash',
        'branch',
        'language',
    ];

    /**
     * @var PortalPhp|null
     */
    protected $portalPhp    = null;

    /**
     * BaseController constructor.
     *
     * @param PortalPhp $portalPhp
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp  = $portalPhp;
    }

    /**
     * Builds a hash from the provided list of variables
     *
     * @param string|array $variables
     * @return string Hashed variables
     */
    protected function getVariables($variables)
    {
        $variablesData = [];

        if (!is_array($variables)) {
            $variables = [$variables];
        }

        foreach ($variables as $variable) {
            $variablesData[] = $this->getVariable($variable);
        }

        return sha1(implode('.', $variablesData));
    }

    /**
     * Resolves a variable for hash usage
     *
     * @param string $variable
     * @return mixed Resolved variable
     */
    protected function getVariable(string $variable)
    {
        switch ($variable) {
            case 'roleshash':
                return $this->rolesHash();
                break;
            case 'branch':
                return $this->portalPhp->findBranchNameFromRequest('/' . request()->path());
                break;
            case 'language':
                return $this->portalPhp->getUserLanguage();
                break;
            default:
                if (Str::startsWith($variable, 'claim_')) {
                    return $this->claim(substr($variable, 6));
                }

                if (Str::startsWith($variable, 'cookie_') && Cookie::has(substr($variable, 7))) {
                    return Cookie::get(substr($variable, 7));
                }
                break;
        }
    }

    /**
     * Resolves a claim for hash usage
     *
     * @param string $claim
     * @return mixed Resolved claim
     */
    protected function claim(string $claim)
    {
        try {
            $claim = \Auth::user()->claims[$claim]->getValue();
        } catch (Exception $e) {
            $claim = null;
        }

        return $claim;
    }

    /**
     * Resolves the rolesHash for hash usage
     *
     * @param string $variable
     * @return string|null Resolved rolesHash
     */
    protected function rolesHash()
    {
        $response = Mesh::request(LoadUser::class)
            ->setAdminClient(false)
            ->fetchCached()
            ->toArray()['data'];
        return $response['me']['rolesHash'];
    }

    /**
     * Responses with the User Context Hash
     *
     * @return string The user context hash or null for anonymous
     */
    public function hash()
    {
        $accept = request()->header('accept');
        if ($accept === 'application/vnd.gtx.user-context-hash') {
            $app = app();
            $contextHash = null;

            if (\Auth::user() !== null) {
                $variableList = config('portal.cache.http.context', []);

                if (empty($variableList)) {
                    $variableList = $this->defaultContextVariables;
                }

                $contextHash = $this->getVariables($variableList);
            }

            \Log::debug('Context Hash: ' . $contextHash);

            return response('', 200)
                        ->header('Content-Type', 'application/vnd.gtx.user-context-hash')
                        ->header('Cache-Control', 'no-cache, must-revalidate, no-store, max-age=0, private')
                        ->header($app['portal.cache.http.userContextHeader'], $contextHash);
        } else {
            return response('', 406);
        }
    }
}
