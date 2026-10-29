<?php

namespace Gentics\PortalPhp\Features\FormGenerator\Http\Controllers;

use Gentics\PortalPhp\Features\FormGenerator\FormGeneratorHandler;

class FormGeneratorController3rdGen extends FormGeneratorController
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
     * @return mixed
     * @throws \Throwable
     */
    public function index()
    {
        return $this->handler->handle3rdGen();
    }

    /**
     * Manage form data
     *
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam forms3rdGenFunction string required Form function.
     *  Enum: save, submit, load, validate, cancel. Example: submit
     * @response 200 {} The form data
     * @return mixed
     * @throws \Throwable
     */
    public function manageForm()
    {
        return $this->handler->handle3rdGen();
    }

    /**
     * Get a binary from the form field from data with the UUID
     *
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_meshUuid string required Form data binary UUID. Example: 1234567890abcdef0987654321
     * @response 200 <<binary>> The form data
     * @return mixed
     * @throws \Throwable
     */
    public function binary()
    {
        return $this->handler->handle3rdGen();
    }

    /**
     * Get a multibinary item from the form field from data with the UUID
     *
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_meshUuid string required Form data binary UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_index int required Form data binary index. Example: 0
     * @response 200 <<binary>> The form data
     * @return mixed
     * @throws \Throwable
     */
    public function multibinary()
    {
        return $this->handler->handle3rdGen();
    }

    /**
     * Get all form data as JSON
     *
     * @urlParam fg_formUuid string required Form UUID. Example: 1234567890abcdef0987654321
     * @urlParam fg_language string required Form language. Example: en
     * @response 200 {} The form data
     * @return mixed
     * @throws \Throwable
     */
    public function translations()
    {
        return $this->handler->handleTranslations();
    }
}
