<?php
namespace PHPSTORM_META {

    use Gentics\PortalPhp\Features\Mesh;

    override(  Mesh\Mesh::request(), map([
        '' => Mesh\GraphQLRequest::class
    ]));

    override(  Mesh\Mesh::request(0), map([
        '' => '@'
    ]));
}
