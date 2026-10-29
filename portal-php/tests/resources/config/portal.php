<?php

return [
    'url' => 'http://fakeurl',
    'apiKey' => 'fakeapikey',
    'projectName' => 'portalphp',
    'schemas' => [
        'content'       => 'portalphp_content',
        'folder'        => 'portalphp_folder',
        'binarycontent' => 'portalphp_binary_content',
    ],
    'templateNamePath' => 'data.node.fields.templateName',
    'exceptionHandler' => [
        'httpCodes' => [
            404 => 'portalphp/errors/404.html',
        ],
        'default' => 'portalphp/errors/',
    ]
];
