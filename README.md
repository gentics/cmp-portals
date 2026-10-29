# Gentics CMP Portals

This repository contains the portal frameworks of the Gentics Content Management Platform (CMP).
A portal renders content managed in [Gentics Mesh](https://www.getmesh.io/) and delivers it to the website visitors.

## Projects

| Folder | Description | Distribution |
| ------ | ----------- | ------------ |
| [`portal-php`](portal-php) | **Gentics Portal \| php**: a library for [Laravel](https://laravel.com/) applications (`gentics/portal-php`) | Composer package via Packagist |
| [`portal-php-laravel`](portal-php-laravel) | Composer metapackage that requires `gentics/portal-php` together with the matching Laravel packages (`gentics/portal-php-laravel`) | Composer package via Packagist |
| [`portal-java`](portal-java) | **Gentics Portal \| java**: libraries for building a portal server with [Vert.x](https://vertx.io/) | Maven artifacts |
| [`ee`](ee) | Helm charts for running the portals on Kubernetes | Helm repository |
| [`ci`](ci) | Build pipeline and helper scripts | – |

All projects share one version number and are released together.

### Mirror repositories for the Composer packages

The folders `portal-php` and `portal-php-laravel` are published to their own read-only repositories, from which
Packagist builds the Composer packages:

* `portal-php` → [github.com/gentics/portal-php](https://github.com/gentics/portal-php)
* `portal-php-laravel` → [github.com/gentics/portal-php-laravel](https://github.com/gentics/portal-php-laravel)

The mirrors are updated automatically by the build pipeline.
Do not open issues or pull requests in the mirrors; use this repository instead.

## Documentation

* [Gentics Portal | php documentation](https://www.gentics.com/guides/latest/portal-php/)
* [Gentics Portal | java documentation](https://www.gentics.com/guides/latest/portal-java/)
* [Gentics CMP changelog](https://www.gentics.com/cmp/changelog/)

The sources of the documentation are part of this repository (`portal-php/docs`, `portal-java/docs`).

## Building

### Gentics Portal | php

Requirements: PHP 8.3 with the `intl` and `json` extensions, [Composer](https://getcomposer.org/).

```bash
cd portal-php
composer install
composer test
```

### Gentics Portal | java

Requirements: Java 17, [Maven](https://maven.apache.org/), Docker (for the tests).

```bash
cd portal-java
mvn clean install
```

See the README and CONTRIBUTING files of the individual projects for details.

## Branches

* `dev`: development of the next release
* `main`: default branch
* `hotfix-<version>`: maintenance of released versions

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
