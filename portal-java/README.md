# Gentics Portal | java

This project contains a collection of libraries which help to setup a Gentics Mesh Portal server which uses Vert.x.

It is part of the repository [gentics/cmp-portals](https://github.com/gentics/cmp-portals).

## Documentation

* [Gentics Portal | java documentation](https://www.gentics.com/guides/latest/portal-java/)
* [Gentics CMP changelog](https://www.gentics.com/cmp/changelog/)

## Building

Requirements: Java 17, Maven, Docker (for the tests).

```bash
mvn clean install
```

## Search queries

For normal searches the elastic search query named `search.es` will be used, and for auto completion options
`searchAutocomplete.es`. Both files must be in the configured `queryPath`. If there exists a file with the same
name in a subdirectory named the same as the configured Mesh project (e.g. `$QUERY_PATH/myproject/search.es`), that
file will be used instead.

### Searched projects

When the list `searchableProjects` in the server configuration is empty or missing, only the project specified
by `meshProject` will be searched. Otherwise objects from all projects in the list are valid search hits. To prevent
problems with total hit counts the provided Elasticsearch query should already restrict the project names using
the regular expression provided by the `%projects` placeholder.

The special entry `*` means that all projects in the used Mesh instance are to be searched. In this case the
Elasticsearch query _should not_ use the `%projects%` placeholder because it will be `@` (ANYSTRING) anyway and
just slow down the search.

### Query placeholders

The placeholders `%query%`, `%size%` and `%from%` in the elastic search query will be replaced by the actual
search term, the desired batch size and the requested page respectively.

Furthermore the placeholder `%projects%` will be replaced with a regular expression matching all project names
configured in `searchableProjects`, and `%tags%` will be replaced by a space separated list of filter tags
specified in the search request.

Note that the placeholders _must not_ be in quotes because those will be added when they are replaced to ensure
valid JSON data.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
