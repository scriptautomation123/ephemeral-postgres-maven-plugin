# OpenShift DB Maven Plugin

This project provides a Maven plugin that provisions an ephemeral PostgreSQL database on OpenShift for integration tests, then cleans it up after the test phase.

The plugin creates a unique OpenShift deployment, secret, service, and PostgreSQL database for each run. It also exports connection details into the Maven project properties so downstream tests can connect without extra wiring.

## What It Does

The plugin exposes two goals:

- `create-db`: runs during `pre-integration-test` and creates the test database resources.
- `drop-db`: runs during `post-integration-test` and removes the resources created for that run.

At runtime the plugin:

- checks that `oc` is available and that the current user can access the target namespace
- applies a generated manifest for a PostgreSQL deployment, service, and secret
- waits for the deployment rollout to complete
- creates a database role and database inside the running PostgreSQL container
- writes the runtime state under `${project.build.directory}/openshift-db/state.properties`
- exports these Maven properties for tests:
  - `it.db.host`
  - `it.db.port`
  - `it.db.name`
  - `it.db.user`
  - `it.db.password`

Cleanup is idempotent: it tries to drop the database and role, deletes resources by label, and removes the local state file.

## Requirements

- Java 17
- Maven 3.9.x
- OpenShift CLI `oc`
- Access to an OpenShift namespace with permission to create deployments, services, secrets, and exec into pods
- A PostgreSQL image that can run in an OpenShift-friendly, rootless container

## Build The Container Image

The repository includes a rootless PostgreSQL image definition in [Dockerfile](Dockerfile) and matching OpenShift build assets under [k8s/](k8s/).

The container entrypoint is [run-postgresql.sh](run-postgresql.sh). It initializes the database on first start and then runs PostgreSQL on port `5432`.

Build or publish the image in whatever registry your OpenShift cluster can pull from, then pass that image reference to the plugin with `openshift.db.image`.

## Use In A Consumer Project

The repository includes a sample plugin configuration in [examples/consumer-pom-snippet.xml](examples/consumer-pom-snippet.xml).

Typical usage looks like this:

```xml
<plugin>
  <groupId>com.acme</groupId>
  <artifactId>openshift-db-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <id>create-db</id>
      <phase>pre-integration-test</phase>
      <goals>
        <goal>create-db</goal>
      </goals>
      <configuration>
        <namespace>${env.OPENSHIFT_NAMESPACE}</namespace>
        <image>image-registry.openshift-image-registry.svc:5000/${env.OPENSHIFT_NAMESPACE}/postgres-rootless:latest</image>
        <adminUser>postgres</adminUser>
        <adminPassword>postgres</adminPassword>
        <dbUser>testuser</dbUser>
        <dbPassword>testpass</dbPassword>
        <dbPrefix>itdb</dbPrefix>
      </configuration>
    </execution>
    <execution>
      <id>drop-db</id>
      <phase>post-integration-test</phase>
      <goals>
        <goal>drop-db</goal>
      </goals>
      <configuration>
        <namespace>${env.OPENSHIFT_NAMESPACE}</namespace>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The plugin also works well with the Maven Failsafe plugin, using the `it.db.*` properties in your integration tests.

## Plugin Parameters

`create-db` accepts the following parameters:

- `openshift.db.namespace` - target OpenShift namespace, required
- `openshift.db.image` - PostgreSQL image to run, required
- `openshift.db.adminUser` - PostgreSQL admin user, defaults to `postgres`
- `openshift.db.adminPassword` - PostgreSQL admin password, required
- `openshift.db.dbUser` - test database user, defaults to `testuser`
- `openshift.db.dbPassword` - test database password, required
- `openshift.db.dbPrefix` - prefix used for generated database names, defaults to `itdb`
- `openshift.db.startupTimeoutSeconds` - rollout wait timeout, defaults to `180`
- `openshift.db.ocBinary` - path to `oc`, defaults to `oc`
- `openshift.db.skip` - skips plugin execution when `true`, defaults to `false`

## Development Notes

- Java sources currently live under `src/src/main/java` in this repository snapshot.
- The plugin stores generated state in `target/openshift-db/`.
- If a run fails after creating resources, `drop-db` can be rerun safely to clean up the namespace.

## Repository Layout

- [pom.xml](pom.xml) - Maven plugin build definition
- [src/src/main/java/com/acme/openshift/db/](src/src/main/java/com/acme/openshift/db/) - plugin implementation
- [Dockerfile](Dockerfile) - OpenShift-friendly PostgreSQL image
- [run-postgresql.sh](run-postgresql.sh) - PostgreSQL entrypoint script
- [k8s/](k8s/) - OpenShift build assets and helper scripts
- [k8s/apply-order.sh](k8s/apply-order.sh) - apply the Dockerfile-based build and deployment manifests
- [k8s/build-run.sh](k8s/build-run.sh) - apply the Git-based build manifest and start the image build
- [k8s/create-git-secret.sh](k8s/create-git-secret.sh) - create a Git credentials secret for OpenShift builds
- [examples/consumer-pom-snippet.xml](examples/consumer-pom-snippet.xml) - example consumer configuration
- [tests/drop.sql](tests/drop.sql) - SQL used by cleanup examples and database teardown
- [scripts/run-tests.sh](scripts/run-tests.sh) - convenience wrapper for running the Maven verification build
