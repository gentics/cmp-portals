# Contributing to the Gentics CMP Portals

Thank you for your interest in contributing. This document applies to all projects in this repository:
`portal-php`, `portal-php-laravel` and `portal-java`. Project-specific notes are in the CONTRIBUTING.md file
of each project folder.

## Where to contribute

Open issues and pull requests in this repository: <https://github.com/gentics/cmp-portals>.

The repositories [gentics/portal-php](https://github.com/gentics/portal-php) and
[gentics/portal-php-laravel](https://github.com/gentics/portal-php-laravel) are read-only mirrors that are
generated from this repository. Pull requests opened there cannot be merged.

## Reporting issues

When reporting a bug, please include:

* the affected project (`portal-php`, `portal-php-laravel` or `portal-java`) and its version
* the version of Gentics Mesh you are using
* the steps to reproduce the problem, the expected and the actual behaviour

## Pull requests

1. Fork the repository and create a branch:
   * new features and improvements: based on `dev`
   * bugfixes for a released version: based on the corresponding `hotfix-<version>` branch
2. Make your changes. Keep a pull request focused on one topic.
3. Add or update tests for the changed behaviour.
4. Run the build and the tests of the affected project (see below) and make sure they pass.
5. Add a changelog entry (see below).
6. Update the documentation if the change affects users.
7. Open a pull request against the branch you started from and describe what you changed and why.

A maintainer will review the pull request. Accepted changes are released with the next release of the
targeted branch.

## Building and testing

### portal-php

Requirements: PHP 8.3 with the `intl` and `json` extensions, Composer.

```bash
cd portal-php
composer install
composer test      # composer validate, lint (phplint, php-cs-fixer, phpcs), phpunit, security check
composer fixcode   # fixes the code style with php-cs-fixer
```

### portal-java

Requirements: Java 17, Maven, Docker.

```bash
cd portal-java
mvn clean install
```

The module `e2e-tests` starts Gentics Mesh with [Testcontainers](https://testcontainers.com/) using images from
`docker.gentics.com`.

## Code style

* `portal-php`: PSR-1 and PSR-2, checked by `php-cs-fixer` (`.php-cs-fixer.php`) and `phpcs` (`phpcs.xml`).
  Editor settings are defined in `.editorconfig`.
* `portal-java`: follow the style of the surrounding code.

## Changelog entries

Every change that is visible to users needs a changelog entry. Entries are plain text files in

* `portal-php/changelog/src/changelog/entries/<yyyy>/<mm>/`
* `portal-java/changelog/src/changelog/entries/<yyyy>/<mm>/`

The file name has the form `<number>.<TICKET>.<type>` (the ticket part is optional), for example
`8886.SUP-20204.security`. Types in use are `feature`, `enhancement`, `bugfix`, `security`, `documentation`,
`manualchange` and `optional-manualchange`. The file content is the changelog text.

If you are an external contributor and do not have a number or ticket key, describe the changelog text in your
pull request; a maintainer will create the entry.

## Documentation

The documentation is written in [AsciiDoc](https://docs.asciidoctor.org/asciidoc/latest/):

* `portal-php/docs/content/portal-php/`
* `portal-java/docs/hugo/content/portal-java/`

Some parts of the Java documentation are generated from annotations in the source code
(for example `portal-java/config/src/main/java/com/gentics/mesh/portal/config/PortalConfig.java`).

## License

By contributing, you agree that your contributions are licensed under the
[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Licenses and contributor agreements

This repository uses an open-core model (see [`LICENSING.md`](LICENSING.md)).

Contributions outside `ee/` are accepted under the Apache License 2.0. Sign off each commit (`git commit -s`) to confirm the [Developer Certificate of Origin](https://developercertificate.org/).

Contributions to `ee/` additionally require our Contributor License Agreement ([individual](CLA-individual.md) or [corporate](CLA-corporate.md)). The CLA bot will ask you to accept it on your first pull request that touches `ee/`. We need it because the Enterprise Modules are licensed under the Business Source License 1.1 and convert to the Apache License 2.0 on their Change Date.

The Gentics name and logos are not covered by the code licenses; see [`TRADEMARKS.md`](TRADEMARKS.md).
