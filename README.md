# Gentics CMP Portals

<p>
  <a href="LICENSING.md"><img src="https://img.shields.io/badge/core-Apache--2.0-brightgreen.svg" alt="Core: Apache-2.0" /></a>
  <a href="LICENSING.md"><img src="https://img.shields.io/badge/ee-BUSL--1.1-blue.svg" alt="Enterprise Modules: BUSL-1.1" /></a>
</p>

This repository contains the portal frameworks of **Gentics CMP** (Content Management Platform).
A portal renders content managed in [Gentics Mesh](https://github.com/gentics/mesh) and delivers it to the website visitors.

## Gentics CMP

The platform consists of four repositories that work together:

| Repository | Component |
| --- | --- |
| [gentics/cms-oss](https://github.com/gentics/cms-oss) | Gentics CMS – editing, publishing, Workspace, MCP tools |
| [gentics/mesh](https://github.com/gentics/mesh) | Gentics Mesh – headless content repository, search, Content RAG, MCP server |
| [gentics/cmp-portals](https://github.com/gentics/cmp-portals) | **This repo.** Portal \| php and Portal \| java – render content from Mesh |
| [gentics/cmp](https://github.com/gentics/cmp) | Umbrella: platform changelog, guides, Helm chart, licensing overview |

The content flows from Gentics CMS into Gentics Mesh and from there into the portals. The [cmp README](https://github.com/gentics/cmp#readme) explains the big picture and how to get started across repositories.

## Projects

| Folder | Description | Distribution |
| ------ | ----------- | ------------ |
| [`portal-php`](portal-php) | **Gentics Portal \| php**: a library for [Laravel](https://laravel.com/) applications (`gentics/portal-php`) | Composer package via Packagist |
| [`portal-php-laravel`](portal-php-laravel) | Composer metapackage that requires `gentics/portal-php` together with the matching Laravel packages (`gentics/portal-php-laravel`) | Composer package via Packagist |
| [`portal-java`](portal-java) | **Gentics Portal \| java**: libraries for building a portal server with [Vert.x](https://vertx.io/) | Maven artifacts |
| [`ee`](ee) | Helm charts for running the portals on Kubernetes (Enterprise Modules, BUSL-1.1) | Helm repository |
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

Pull requests are welcome. Contributions to the portal code are accepted under the Apache License 2.0 with a DCO sign-off (`git commit -s`). Contributions to `ee/` additionally need our [Contributor License Agreement](CLA-individual.md) ([corporate version](CLA-corporate.md)). See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

The portal code is licensed under the [Apache License, Version 2.0](LICENSE). The Helm charts in `ee/` are
licensed under the Business Source License 1.1 and convert to Apache 2.0 on their Change Date. See
[LICENSING.md](LICENSING.md) and [TRADEMARKS.md](TRADEMARKS.md).

Gentics is a registered trade mark of Gentics Software GmbH.
