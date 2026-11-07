<h1 align="center">Gentics CMP Portals</h1>
<h3 align="center">Portal | php and Portal | java – the frontends that turn Mesh content into websites</h3>

<p align="center">
  <a href="LICENSING.md"><img src="https://img.shields.io/badge/core-Apache--2.0-brightgreen.svg" alt="Core: Apache-2.0" /></a>
  <a href="LICENSING.md"><img src="https://img.shields.io/badge/ee-BUSL--1.1-blue.svg" alt="Enterprise Modules: BUSL-1.1" /></a>
  <a href="https://packagist.org/packages/gentics/portal-php"><img src="https://img.shields.io/badge/packagist-gentics%2Fportal--php-blue.svg" alt="Packagist" /></a>
</p>

A portal is the piece of Gentics CMP that visitors actually see. It reads the content that
[Gentics CMS](https://github.com/gentics/cms-oss) has published into [Gentics Mesh](https://github.com/gentics/mesh)
and renders it as a website, with routing, templating, navigation, search, forms and whatever your project
needs on top. This repository holds the two portal frameworks Gentics maintains: one for the PHP/Laravel
world, one for the Java/Vert.x world. Pick the one your team is at home in; both talk to the same Mesh.

## Where it sits in Gentics CMP

| Repository | Component |
| --- | --- |
| [gentics/cms-oss](https://github.com/gentics/cms-oss) | Gentics CMS – editing, publishing, Workspace, MCP tools |
| [gentics/mesh](https://github.com/gentics/mesh) | Gentics Mesh – headless content repository, search, Content RAG, MCP server |
| [gentics/cmp-portals](https://github.com/gentics/cmp-portals) | **This repo.** Portal \| php and Portal \| java – render content from Mesh |
| [gentics/cmp](https://github.com/gentics/cmp) | Umbrella: platform changelog, guides, Helm chart, licensing overview |

The [cmp README](https://github.com/gentics/cmp#readme) has the big picture and the cross-repository
getting-started guide.

## What is in here

| Folder | What it is | Distribution |
| --- | --- | --- |
| [`portal-php`](portal-php) | **Gentics Portal \| php** – a library for [Laravel](https://laravel.com/) applications (`gentics/portal-php`) | Composer package via Packagist |
| [`portal-php-laravel`](portal-php-laravel) | Composer metapackage that pulls `gentics/portal-php` together with the matching Laravel packages (`gentics/portal-php-laravel`) | Composer package via Packagist |
| [`portal-java`](portal-java) | **Gentics Portal \| java** – libraries for building a portal server with [Vert.x](https://vertx.io/) (api, core, handler, services, server, …) | Maven artifacts |
| [`ee`](ee) | Helm charts for running the portals on Kubernetes (Enterprise Modules, BUSL-1.1) | Helm repository |
| [`ci`](ci) | Build pipeline and helper scripts | – |

All projects share one version number and are released together.

### Mirror repositories for the Composer packages

`portal-php` and `portal-php-laravel` are published to read-only mirrors, from which Packagist builds the
Composer packages: [gentics/portal-php](https://github.com/gentics/portal-php) and
[gentics/portal-php-laravel](https://github.com/gentics/portal-php-laravel). The build pipeline updates
them automatically. Issues and pull requests belong here, not in the mirrors.

## Getting started

### Portal | php

Requirements: PHP 8.3 with the `intl` and `json` extensions, [Composer](https://getcomposer.org/).

```bash
# in your Laravel application
composer require gentics/portal-php-laravel

# or work on the library itself
cd portal-php
composer install
composer test
```

The [Portal | php documentation](https://www.gentics.com/guides/latest/portal-php/) explains the Mesh
connection, routing, Blade templates and the building blocks. Its sources live in
[`portal-php/docs`](portal-php/docs).

### Portal | java

Requirements: Java 17, [Maven](https://maven.apache.org/), Docker (for the tests).

```bash
cd portal-java
mvn clean install -DskipTests
```

The [Portal | java documentation](https://www.gentics.com/guides/latest/portal-java/) explains how to
assemble your own portal server from the modules. Its sources live in [`portal-java/docs`](portal-java/docs).

### Run on Kubernetes

The Helm charts in [`ee/portal-php/helm-chart`](ee/portal-php/helm-chart) and
[`ee/portal-java/helm-chart`](ee/portal-java/helm-chart) deploy a portal next to Mesh; the
[Gentics CMP umbrella chart](https://github.com/gentics/cmp/tree/master/ee/cmp-chart) wires up the whole
platform.

### Branches

* `dev` – next release
* `main` – default branch
* `hotfix-<version>` – maintenance of released versions
* `f-<ticket>` – feature branches

## Documentation and help

* [Portal | php documentation](https://www.gentics.com/guides/latest/portal-php/)
* [Portal | java documentation](https://www.gentics.com/guides/latest/portal-java/)
* [Gentics CMP changelog](https://www.gentics.com/cmp/changelog/) and [guides](https://www.gentics.com/guides/) across all components
* Issues: [github.com/gentics/cmp-portals/issues](https://github.com/gentics/cmp-portals/issues)

## Licensing

Gentics CMP Portals is **open core**:

* **Core – Apache License 2.0.** Everything outside `ee/`. OSI-approved open source: run it in production,
  modify it, redistribute it.
* **Enterprise Modules – Business Source License 1.1.** Everything inside `ee/`: the Helm charts of the portals. The portal code itself is Apache-2.0.
  The source is public and free to use for development, testing, evaluation and demonstration. Production
  use requires a commercial license – contact [sales@gentics.com](mailto:sales@gentics.com). Every module
  converts to Apache 2.0 three years after its release (Change Date); the exact dates are in
  [`ee/LICENSE`](ee/LICENSE).

The core runs without `ee/`. [`LICENSING.md`](LICENSING.md) has the details, the
[cmp repository](https://github.com/gentics/cmp/blob/master/LICENSING.md) the overview across the platform.

## Contributing

Pull requests are welcome.

* Contributions to the core are accepted under the Apache License 2.0 with a DCO sign-off (`git commit -s`).
* Contributions to `ee/` additionally need our [Contributor License Agreement](CLA-individual.md)
  ([corporate version](CLA-corporate.md)). The CLA bot asks on your first pull request that touches `ee/`.

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Found a bug? Open an issue in this repository.

## Commercial services

Gentics Software GmbH, the company behind the Gentics CMP Portals, offers product maintenance, developer support,
consulting and custom development. Contact [sales@gentics.com](mailto:sales@gentics.com).

---

Copyright (c) 2014-2026 Gentics Software GmbH. Gentics is a registered trademark of Gentics Software GmbH,
see [`TRADEMARKS.md`](TRADEMARKS.md).
