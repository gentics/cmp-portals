# Gentics Portal | php

> **Note:** This package is developed in the folder `portal-php` of the repository
> [gentics/cmp-portals](https://github.com/gentics/cmp-portals). If you are viewing the repository
> `gentics/portal-php`, it is a read-only mirror: please open issues and pull requests in
> [gentics/cmp-portals](https://github.com/gentics/cmp-portals).

Gentics Portal | php is a library for [Laravel](https://laravel.com/) applications that delivers content from
[Gentics Mesh](https://www.getmesh.io/).

## Requirements

* PHP 8.3 with the `intl` and `json` extensions
* [Composer](https://getcomposer.org/)

## Installation

```bash
composer require gentics/portal-php
```

The service provider `Gentics\PortalPhp\Providers\ServiceProvider` is registered automatically by Laravel's
package discovery.

To install the package together with the matching Laravel packages, use the metapackage
[`gentics/portal-php-laravel`](https://github.com/gentics/cmp-portals/tree/main/portal-php-laravel).

## Documentation

* [Gentics Portal | php documentation](https://www.gentics.com/guides/latest/portal-php/)
* [Gentics CMP changelog](https://www.gentics.com/cmp/changelog/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
