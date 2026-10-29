# Contributing to Gentics Portal | java

The general contribution guidelines (branches, pull requests, changelog entries, license) are in the
[CONTRIBUTING.md](../CONTRIBUTING.md) in the root of this repository.

## Building and testing

Requirements: Java 17, Maven, Docker.

```bash
cd portal-java
mvn clean install
```

The module `e2e-tests` starts Gentics Mesh with Testcontainers using images from `docker.gentics.com`.

## IDE Setup - Eclipse

Make sure that you use at least Eclipse Oxygen.

Install the following maven m2e workshop plugins:

  * m2e-apt-plugin

Note: Make sure that your Eclipse Maven APT settings are set to "Automatically configure JDT APT".
If you don't find this option, you most likely need to install the M2E APT Plugin for eclipse.

Import all maven modules in your IDE.

Please note that this project is using Google Dagger for dependency injection. Adding new dependencies or beans
may require a fresh build (via Project->Clean).

## Changelog

Add a changelog entry in `changelog/src/changelog/entries/<yyyy>/<mm>/`. The file name format is described in the
[CONTRIBUTING.md](../CONTRIBUTING.md#changelog-entries) in the root of this repository.

## Documentation

The official [Gentics Portal | java documentation](https://www.gentics.com/guides/latest/portal-java/) should be
improved consistently. You may add or change content in `docs/hugo/content`.
Some documentation is generated from annotations in the source code (example: `config/src/main/java/com/gentics/mesh/portal/config/PortalConfig.java`)
Write using the [AsciiDoc syntax](https://docs.asciidoctor.org/asciidoc/latest/syntax-quick-reference/).

## Releasing (maintainers)

Releases are built by the Gentics CI for the required branch (i.e. the `dev` or the desired `hotfix` branch).
Portal | java and Portal | php are always released together with the same version.
