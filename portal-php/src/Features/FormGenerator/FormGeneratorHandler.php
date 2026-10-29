<?php
namespace Gentics\PortalPhp\Features\FormGenerator;

use Gentics\PortalPhp\Features\HttpCache\AppCache;
use Gentics\PortalPhp\Features\Proxy\Filter\AddAuthorizationHeader;
use Gentics\PortalPhp\Features\Proxy\Filter\FilterRequestCookies;
use Gentics\PortalPhp\Features\Proxy\Filter\RemoveRoutePath;
use Gentics\PortalPhp\Features\ErrorHandler\Exceptions;
use Gentics\PortalPhp\PortalPhp;
use Illuminate\Support\Facades\Cookie;
use Illuminate\Http\Response;
use GuzzleHttp\Exception\GuzzleException;

class FormGeneratorHandler
{
    /**
     * @var string
     *              Name of the cookie, the user will get when a form is partially filled
     *              (the cookie value is the uuid of the form data node)
     *              (for the legacy form generator plugin)
     */
    public static $COOKIE_NAME = 'fg-data';

    /**
     * @var string
     *              Name of the cookie, the user will get when a form is partially filled
     *              (the cookie value is the uuid of the form data node)
     */
    public static $COOKIE_NAME_FORMS = 'forms-data';

    /**
     * @var string
     *              Name of the cookie, the user will get when a poll form is submitted
     */
    public static $COOKIE_NAME_FORMS_POLL = 'forms-voted';

    /**
     * @var string
     *              Name of the form generator secret/token header
     *              (for the legacy form generator plugin)
     */
    public static $SECRET_HEADER_NAME = 'FG-ACCESSTOKEN';

    /**
     * @var string
     *              Name of the form generator secret/token header
     */
    public static $SECRET_HEADER_NAME_FORMS = 'FORMS-ACCESSTOKEN';

    /**
     * @var PortalPhp|null
     */
    protected $portalPhp       = null;

    /**
     * @var \Illuminate\Config\Repository|mixed|null
     */
    protected $projectName     = null;

    /**
     * @var \Gentics\Mesh\Client\MeshClient|null
     */
    protected $mesh            = null;

    /**
     * @var array Paths for the Form Generator Plugin
     */
    protected static $formPluginPaths = [
        'fg',
        'forms',
    ];

    /**
     * @var string Form Generator Plugin path for render
     */
    protected $formRenderPath = 'forms';

    /**
     * @var array Cookies to be forwarded to the plugin
     */
    protected $forwardCookies = [];

    /**
     * FormGeneratorHandler constructor.
     *
     * @param PortalPhp $portalPhp
     * @throws \Throwable
     */
    public function __construct(PortalPhp $portalPhp)
    {
        $this->portalPhp = $portalPhp;
        $this->projectName = $this->portalPhp->config('projectName');

        // Create client without adminClient fallback
        $this->mesh = $this->portalPhp->getMeshClient(false);

        // Add cookies to be forwarded
        $this->forwardCookies = [
            static::$COOKIE_NAME_FORMS_POLL,
        ];
    }

    public function globalHandle()
    {
        /**
         * Proxy requests a global request directly to the Forms Plugin
         * with passing the current client token (or null, if not logged in).
         */
        $response = $this->mesh->proxy(
            '/plugins/' . $this->getPluginPath() . '/forms/',
            [
                new FilterRequestCookies($this->forwardCookies),
                new RemoveRoutePath('/' . $this->getPluginPath() . '/forms'),
                new AddAuthorizationHeader($this->portalPhp->getClientToken())
            ],
            [
                // Return status code to the user instead of following redirects
                'allow_redirects' => false,
                // Require 'Expect: 100-continue' when body is >= 1 MB
                'expect' => 1 * 1024 * 1024
            ]
        );

        return $response;
    }

    /**
     * @return mixed
     * @throws \Throwable
     */
    public function handle()
    {
        /**
         * Proxy requests directly to the FormGenerator Plugin
         * with passing the current client token (or null, if not logged in).
         */
        $response = $this->mesh->proxy(
            '/' . $this->projectName . '/plugins/' . $this->getPluginPath() . '/forms/',
            [
                new FilterRequestCookies($this->forwardCookies),
                new RemoveRoutePath('/' . $this->getPluginPath() . '/forms'),
                new AddAuthorizationHeader($this->portalPhp->getClientToken())
            ],
            [
                // Return status code to the user instead of following redirects
                'allow_redirects' => false,
                // Require 'Expect: 100-continue' when body is >= 1 MB
                'expect' => 1 * 1024 * 1024
            ]
        );

        return $response;
    }

    /**
     * @return mixed
     * @throws \Throwable
     */
    public function handle3rdGen()
    {
        /**
         * Proxy requests directly to the FormGenerator Plugin 3rd generation (unified)
         * with passing the current client token (or null, if not logged in).
         */
        $response = $this->mesh->proxy(
            '/' . $this->projectName . '/plugins/forms/form/',
            [
                new FilterRequestCookies($this->forwardCookies),
                new RemoveRoutePath(null),
                new AddAuthorizationHeader($this->portalPhp->getClientToken())
            ],
            [
                // Return status code to the user instead of following redirects
                'allow_redirects' => false,
                // Require 'Expect: 100-continue' when body is >= 1 MB
                'expect' => 1 * 1024 * 1024
            ]
        );

        return $response;
    }

    /**
     * @return mixed
     * @throws \Throwable
     */
    public function handleTranslations()
    {
        /**
         * Proxy requests directly to the FormGenerator Plugin 3rd generation (unified)
         * with passing the current client token (or null, if not logged in).
         */
        $response = $this->mesh->proxy(
            '/' . $this->projectName . '/plugins/forms/translations/',
            [
                new FilterRequestCookies($this->forwardCookies),
                new RemoveRoutePath(null),
                new AddAuthorizationHeader($this->portalPhp->getClientToken())
            ],
            [
                // Return status code to the user instead of following redirects
                'allow_redirects' => false,
                // Require 'Expect: 100-continue' when body is >= 1 MB
                'expect' => 1 * 1024 * 1024
            ]
        );

        return $response;
    }

    /**
     * Fetch form data
     *
     * @param string $url
     * @return string|null
     * @throws \GuzzleHttp\Exception\GuzzleException
     */
    protected function getForm(string $url): ?string
    {
        // Remove leading slash because it will override the base uri otherwise
        $url = ltrim($url, '/');
        $response_data = null;

        try {
            if (!is_null($this->portalPhp->getClientToken())) {
                $headers['Authorization'] = "Bearer " . $this->portalPhp->getClientToken();
            }

            $cookies = [];

            foreach ($this->forwardCookies as $cookieName) {
                if (Cookie::has($cookieName)) {
                    $cookies[$cookieName] = Cookie::get($cookieName);
                }
            }

            if (request()->hasHeader(('referer'))) {
                $headers['referer'] = request()->header('referer');
            }

            if (!empty($cookies)) {
                $headers['cookie'] = PortalPhp::cookieHeaderFromArray($cookies);
            }

            $headers['Accept'] = 'text/html';

            $response = $this->mesh->request('GET', $url, [
                'headers' => $headers
            ]);

            if ($response->getStatusCode() == Response::HTTP_OK) {
                $response_data = $response->getBody()->getContents();
            }
        } catch (GuzzleException $exception) {
        }

        return $response_data;
    }

    /**
     * Fetch form result data as a JSON-decoded array.
     *
     * @param string $url
     * @return array|null
     */
    protected function getFormResults(string $url): ?array
    {
        $url = ltrim($url, '/');

        try {
            $headers = [];

            if (!is_null($this->portalPhp->getClientToken())) {
                $headers['Authorization'] = "Bearer " . $this->portalPhp->getClientToken();
            }

            $cookies = [];

            foreach ($this->forwardCookies as $cookieName) {
                if (Cookie::has($cookieName)) {
                    $cookies[$cookieName] = Cookie::get($cookieName);
                }
            }

            if (!empty($cookies)) {
                $headers['cookie'] = PortalPhp::cookieHeaderFromArray($cookies);
            }

            $headers['Accept'] = 'application/json';

            $response = $this->mesh->request('GET', $url, [
                'headers' => $headers
            ]);

            if ($response->getStatusCode() == Response::HTTP_OK) {
                $decoded = json_decode($response->getBody()->getContents(), true);
                return is_array($decoded) ? $decoded : null;
            }
        } catch (GuzzleException $exception) {
        }

        return null;
    }

    /**
     * Validates the plugin path against the request
     * @return string
     * @throws \Gentics\PortalPhp\Features\ErrorHandler\Exceptions\PortalPhpException
     */
    protected function getPluginPath(): ?string
    {
        foreach (static::$formPluginPaths as $path) {
            if (request()->route('fg_pluginPath') === $path) {
                return $path;
            }
        }

        throw new Exceptions\PortalPhpException("Invalid form path");
    }

    /**
     * Render the form for the 3rd generation Forms plugin.
     *
     * When $showResults is true, fetches poll result data from the plugin
     * and renders it through a configured Blade template
     * (forms.pollResultsTemplate, optionally prefixed with $templateContext).
     * Otherwise emits the formgen web app markup: CSS/JS bundle includes
     * and a root div with data attributes that bootstrap the web app.
     *
     * @param string $formUuid
     * @param string|null $formDataUuid  Optional UUID of partially-filled
     *                                   form data, exposed to the web app
     *                                   as the data-formDataID attribute
     * @param string|null $language
     * @param bool|null $showResults
     * @param string|null $templateContext  Subdirectory prefix for the
     *                                      pollResultsTemplate view name
     * @param string|null $externalId
     * @param string|null $pluginName
     * @return string|null
     */
    public function render(
        string $formUuid,
        ?string $formDataUuid = null,
        ?string $language = null,
        ?bool $showResults = false,
        ?string $templateContext = "",
        ?string $externalId = null,
        ?string $pluginName = null
    ): ?string {
        AppCache::bypassWhen('renderForm');

        if ($showResults === true) {
            $pollResultsTemplate = $this->portalPhp->config('forms.pollResultsTemplate', '', true);

            if (empty($pollResultsTemplate)) {
                return null;
            }

            $templateName = (!empty($templateContext) ? rtrim($templateContext, '/') . '/' : '')
                . $pollResultsTemplate;

            $url = $this->projectName . '/plugins/forms/form/' . urlencode($formUuid) . '/results';

            if (isset($language) && trim($language) !== '') {
                $url .= '?lang=' . $language;
            }

            $data = $this->getFormResults($url);

            if ($data === null) {
                return null;
            }

            return view($templateName, $data)->render();
        }

        $staticFileDir = $this->portalPhp->config('forms.staticFileDirectory', '', true);
        $staticServePath = $this->portalPhp->config('forms.staticServePath', '/static/', true);

        $cssDir = rtrim($staticFileDir, '/') . '/formgen/_expo/static/css/';
        $jsDir  = rtrim($staticFileDir, '/') . '/formgen/_expo/static/js/web/';

        if (!is_dir($cssDir) || !is_dir($jsDir)) {
            return null;
        }

        $serveBase = rtrim($staticServePath, '/') . '/formgen/_expo/static/';

        $markup = '';

        foreach (scandir($cssDir) as $file) {
            if (str_ends_with($file, '.css')) {
                $markup .= "\n<link rel=\"stylesheet\" href=\"" . $serveBase . "css/" . $file . "\"/>";
            }
        }

        $markup .= "\n<div id=\"root\" tabindex=\"0\" data-formID=\""
            . htmlspecialchars($formUuid, ENT_QUOTES, 'UTF-8')
            . "\"";

        if (!empty($formDataUuid)) {
            $markup .= " data-formDataID=\"" . htmlspecialchars($formDataUuid, ENT_QUOTES, 'UTF-8') . "\"";
        }

        if (!empty($externalId)) {
            $markup .= " data-externalId=\"" . htmlspecialchars($externalId, ENT_QUOTES, 'UTF-8') . "\"";
        }

        if (!empty($language)) {
            $markup .= " data-culture=\"" . htmlspecialchars($language, ENT_QUOTES, 'UTF-8') . "\"";
        }

        if (!empty($pluginName)) {
            $markup .= " data-plugin-name=\"" . htmlspecialchars($pluginName, ENT_QUOTES, 'UTF-8') . "\"";
        }

        if (!empty($staticServePath)) {
            if (!str_ends_with($staticServePath, '/')) {
                $staticServePath .= '/';
            }
            $markup .= " data-static-serve-path=\""
                . htmlspecialchars($staticServePath, ENT_QUOTES, 'UTF-8')
                . "formgen/\"";
        }

        $markup .= "></div>";

        foreach (scandir($jsDir) as $file) {
            if (str_ends_with($file, '.js')) {
                $markup .= "\n<script type=\"module\" src=\"" . $serveBase . "js/web/" . $file . "\"></script>";
            }
        }

        return $markup . "\n";
    }

    /**
     * Sets the Form Generator Plugin Path for render
     *
     * @param string $path
     */
    public function setRenderPath(string $path): void
    {
        if (in_array($path, static::$formPluginPaths)) {
            $this->formRenderPath = $path;
        }
    }

    /**
     * @return string|null Form data (legacy plugin)
     */
    public static function getFormCookie(): ?string
    {
        if (Cookie::has(static::$COOKIE_NAME)) {
            return Cookie::get(static::$COOKIE_NAME);
        }

        return null;
    }

    /**
     * @return string|null Form data
     */
    public static function getFormsCookie(): ?string
    {
        if (Cookie::has(static::$COOKIE_NAME_FORMS)) {
            return Cookie::get(static::$COOKIE_NAME_FORMS);
        }

        return null;
    }

    /**
     * @return string|null Form poll cookie
     */
    public static function getFormsPollCookie(): ?string
    {
        if (Cookie::has(static::$COOKIE_NAME_FORMS_POLL)) {
            return Cookie::get(static::$COOKIE_NAME_FORMS_POLL);
        }

        return null;
    }
}
