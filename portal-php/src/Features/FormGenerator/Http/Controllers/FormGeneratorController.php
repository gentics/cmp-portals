<?php

namespace Gentics\PortalPhp\Features\FormGenerator\Http\Controllers;

use Illuminate\Routing\Controller as BaseController;
use Gentics\PortalPhp\Features\FormGenerator\FormGeneratorHandler;

class FormGeneratorController extends BaseController
{
    /**
     * @var FormGeneratorHandler|null
     */
    protected $handler = null;

    /**
     * FormGeneratorController constructor.
     * @param FormGeneratorHandler $handler
     */
    public function __construct(FormGeneratorHandler $handler)
    {
        $this->handler = $handler;
    }

    /**
     * Post a multipart form data
     *
     * @bodyParam body file The form data
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @queryParam publish bool Publish the post at once. Example: true
     * @response 205 Data posted
     * @response 303 Data posted, a redirect to the success page issued
     *
     * @return mixed
     * @throws \Throwable
     */
    public function postData()
    {
        return $this->handler->handle();
    }

    /**
     * Post a JSON form data
     *
     * @bodyParam body object The form data
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @queryParam publish bool Publish the post at once. Example: true
     * @response 205 Data posted
     * @response 303 Data posted, a redirect to the success page issued
     *
     * @return mixed
     * @throws \Throwable
     */
    public function postJsonData()
    {
        return $this->handler->handle();
    }

    /**
     * Update a multipart form data
     *
     * @bodyParam body file The form data
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_formDataUuid string required Form data UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @queryParam publish bool Publish the post at once. Example: true
     * @response 205 Data updated
     * @response 303 Data updated, a redirect to the success page issued
     * @return mixed
     * @throws \Throwable
     */
    public function updateData()
    {
        return $this->handler->handle();
    }

    /**
     * Update a JSON form data
     *
     * @bodyParam body object The form data
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_formDataUuid string required Form data UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @queryParam publish bool Publish the post at once. Example: true
     * @response 205 Data updated
     * @response 303 Data updated, a redirect to the success page issued
     * @return mixed
     * @throws \Throwable
     */
    public function updateJsonData()
    {
        return $this->handler->handle();
    }

    /**
     * Get all form data
     *
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @response 200 string The form data
     * @return mixed
     * @throws \Throwable
     */
    public function getData()
    {
        return $this->handler->handle();
    }

    /**
     * Get all form data as JSON
     *
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @response 200 {} The form data
     * @return mixed
     * @throws \Throwable
     */
    public function getJsonData()
    {
        return $this->handler->handle();
    }

    /**
     * Get a binary from the form field from data with the UUID
     *
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_formDataUuid string required Form data UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_binaryFieldName string required Binary field key. Example: uploadedFile
     * @queryParam lang string Language. Example: en
     * @response 200 <<binary>> The form data
     * @return mixed
     * @throws \Throwable
     */
    public function getPostedDataBinary()
    {
        return $this->handler->handle();
    }

    /**
     * Get a form data by its UUID
     *
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_formDataUuid string required Form data UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @response 200 string The form data
     * @return mixed
     * @throws \Throwable
     */
    public function getPostedData()
    {
        return $this->handler->handle();
    }

    /**
     * Get a JSON form data by its UUID
     *
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_formDataUuid string required Form data UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @response 200 {} The form data
     * @return mixed
     * @throws \Throwable
     */
    public function getPostedJsonData()
    {
        return $this->handler->handle();
    }

    /**
     * @return mixed
     * @throws \Throwable
     */
    public function index()
    {
        return $this->handler->handle();
    }

    /**
     * Get elements, aka schema, of a form
     *
     * @urlParam fg_pluginPath string required Form processing plugin ID. Enum: fg, forms. Example: forms
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @queryParam lang string Language. Example: en
     * @response 200 string The form schema
     * @return mixed
     * @throws \Throwable
     */
    public function getElements()
    {
        return $this->handler->handle();
    }

    /**
     * Get an OpenAPI v3.0 YAML spec for the Mesh Forms Plugin
     *
     * @response 200 <<binary>> YAML file
     */
    public function openapi()
    {
        request()->route()->setParameter('fg_pluginPath', 'forms');
        return $this->handler->globalHandle();
    }
}
