# Contributing to Gentics Portal | php

> **Note:** This package is developed in the folder `portal-php` of the repository
> [gentics/cmp-portals](https://github.com/gentics/cmp-portals). The repository `gentics/portal-php` is a
> read-only mirror. Please open issues and pull requests in
> [gentics/cmp-portals](https://github.com/gentics/cmp-portals).

The general contribution guidelines (branches, pull requests, changelog entries, license) are in the
[CONTRIBUTING.md of gentics/cmp-portals](https://github.com/gentics/cmp-portals/blob/main/CONTRIBUTING.md).

## Development setup

Requirements: PHP 8.3 with the `intl` and `json` extensions, Composer.

```bash
cd portal-php
composer install
```

## Tests and code style

```bash
composer test      # composer validate, lint (phplint, php-cs-fixer, phpcs), phpunit, security check
composer phpunit   # unit tests only
composer lint      # code style checks only
composer fixcode   # fixes the code style with php-cs-fixer
```

The code style is PSR-1 and PSR-2 (`.php-cs-fixer.php`, `phpcs.xml`). Editor settings are defined in
`.editorconfig`.

## Changelog

Add a changelog entry in `changelog/src/changelog/entries/<yyyy>/<mm>/`. The file name format is described in the
[CONTRIBUTING.md of gentics/cmp-portals](https://github.com/gentics/cmp-portals/blob/main/CONTRIBUTING.md#changelog-entries).

## Documentation

The documentation sources are in `docs/content/portal-php/` (AsciiDoc).
