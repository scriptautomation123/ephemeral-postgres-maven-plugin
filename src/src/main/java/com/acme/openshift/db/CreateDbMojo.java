package com.acme.openshift.db;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

@Mojo(name = "create-db", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, threadSafe = true)
public class CreateDbMojo extends AbstractOcMojo {

    @Parameter(property = "openshift.db.image", required = true)
    private String image;

    @Parameter(property = "openshift.db.adminUser", defaultValue = "postgres")
    private String adminUser;

    @Parameter(property = "openshift.db.adminPassword", required = true)
    private String adminPassword;

    @Parameter(property = "openshift.db.dbUser", defaultValue = "testuser")
    private String dbUser;

    @Parameter(property = "openshift.db.dbPassword", required = true)
    private String dbPassword;

    @Parameter(property = "openshift.db.dbPrefix", defaultValue = "itdb")
    private String dbPrefix;

    @Parameter(property = "openshift.db.startupTimeoutSeconds", defaultValue = "180")
    private int startupTimeoutSeconds;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("openshift.db.skip=true, skipping create-db");
            return;
        }

        ensureOcAndAccess();

        String runId = "tr-" + UUID.randomUUID().toString().substring(0, 8);
        String app = "pg-" + runId;
        String secret = app + "-secret";
        String dbName = (dbPrefix + "_" + runId.replace("-", "")).toLowerCase(Locale.ROOT);

        Path manifest = writeManifest(app, secret);

        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "apply", "-f", manifest.toString()));
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "rollout", "status",
                "deployment/" + app, "--timeout=" + startupTimeoutSeconds + "s"));

        // Create role/db dynamically
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "exec", "deployment/" + app, "--",
                "bash", "-lc",
                "export PGPASSWORD='" + escape(adminPassword) + "'; " +
                        "psql -v ON_ERROR_STOP=1 -U " + q(adminUser) + " -d postgres -c " +
                        "\"DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '" + escSql(dbUser) + "') " +
                        "THEN CREATE ROLE " + ident(dbUser) + " LOGIN PASSWORD '" + escSql(dbPassword) + "'; END IF; END $$;\""));

        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "exec", "deployment/" + app, "--",
                "bash", "-lc",
                "export PGPASSWORD='" + escape(adminPassword) + "'; " +
                        "psql -v ON_ERROR_STOP=1 -U " + q(adminUser) + " -d postgres -c " +
                        "\"SELECT 'CREATE DATABASE " + ident(dbName) + " OWNER " + ident(dbUser) + "' " +
                        "WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '" + escSql(dbName) + "')\\\\gexec\""));

        // Export for tests
        project.getProperties().setProperty("it.db.host", app);
        project.getProperties().setProperty("it.db.port", "5432");
        project.getProperties().setProperty("it.db.name", dbName);
        project.getProperties().setProperty("it.db.user", dbUser);
        project.getProperties().setProperty("it.db.password", dbPassword);

        Properties st = new Properties();
        st.setProperty("runId", runId);
        st.setProperty("app", app);
        st.setProperty("secret", secret);
        st.setProperty("dbName", dbName);
        st.setProperty("dbUser", dbUser);
        st.setProperty("adminUser", adminUser);
        st.setProperty("adminPassword", adminPassword);
        saveState(st);

        getLog().info("Created test DB '" + dbName + "' at " + app + ":5432 (test-run=" + runId + ")");
    }

    private Path writeManifest(String app, String secret) throws MojoExecutionException {
        String yaml = ""
                + "apiVersion: v1\n"
                + "kind: Secret\n"
                + "metadata:\n"
                + "  name: " + secret + "\n"
                + "  labels:\n"
                + "    app: " + app + "\n"
                + "    test-run: " + app.substring(3) + "\n"
                + "type: Opaque\n"
                + "stringData:\n"
                + "  POSTGRES_USER: " + adminUser + "\n"
                + "  POSTGRES_PASSWORD: " + adminPassword + "\n"
                + "  POSTGRES_DB: postgres\n"
                + "---\n"
                + "apiVersion: apps/v1\n"
                + "kind: Deployment\n"
                + "metadata:\n"
                + "  name: " + app + "\n"
                + "  labels:\n"
                + "    app: " + app + "\n"
                + "    test-run: " + app.substring(3) + "\n"
                + "spec:\n"
                + "  replicas: 1\n"
                + "  selector:\n"
                + "    matchLabels:\n"
                + "      app: " + app + "\n"
                + "  template:\n"
                + "    metadata:\n"
                + "      labels:\n"
                + "        app: " + app + "\n"
                + "        test-run: " + app.substring(3) + "\n"
                + "    spec:\n"
                + "      securityContext:\n"
                + "        seccompProfile:\n"
                + "          type: RuntimeDefault\n"
                + "      containers:\n"
                + "      - name: postgres\n"
                + "        image: " + image + "\n"
                + "        imagePullPolicy: IfNotPresent\n"
                + "        ports:\n"
                + "        - containerPort: 5432\n"
                + "          name: pg\n"
                + "        env:\n"
                + "        - name: POSTGRES_USER\n"
                + "          valueFrom:\n"
                + "            secretKeyRef:\n"
                + "              name: " + secret + "\n"
                + "              key: POSTGRES_USER\n"
                + "        - name: POSTGRES_PASSWORD\n"
                + "          valueFrom:\n"
                + "            secretKeyRef:\n"
                + "              name: " + secret + "\n"
                + "              key: POSTGRES_PASSWORD\n"
                + "        - name: POSTGRES_DB\n"
                + "          value: postgres\n"
                + "        - name: PGDATA\n"
                + "          value: /opt/app-root/src/postgresql/data\n"
                + "        readinessProbe:\n"
                + "          tcpSocket:\n"
                + "            port: 5432\n"
                + "          initialDelaySeconds: 10\n"
                + "          periodSeconds: 5\n"
                + "        livenessProbe:\n"
                + "          tcpSocket:\n"
                + "            port: 5432\n"
                + "          initialDelaySeconds: 30\n"
                + "          periodSeconds: 10\n"
                + "        securityContext:\n"
                + "          allowPrivilegeEscalation: false\n"
                + "          runAsNonRoot: true\n"
                + "          capabilities:\n"
                + "            drop: [\"ALL\"]\n"
                + "        volumeMounts:\n"
                + "        - name: pgdata\n"
                + "          mountPath: /opt/app-root/src/postgresql\n"
                + "      volumes:\n"
                + "      - name: pgdata\n"
                + "        emptyDir: {}\n"
                + "---\n"
                + "apiVersion: v1\n"
                + "kind: Service\n"
                + "metadata:\n"
                + "  name: " + app + "\n"
                + "  labels:\n"
                + "    app: " + app + "\n"
                + "    test-run: " + app.substring(3) + "\n"
                + "spec:\n"
                + "  selector:\n"
                + "    app: " + app + "\n"
                + "  ports:\n"
                + "  - name: pg\n"
                + "    port: 5432\n"
                + "    targetPort: pg\n";

        try {
            Files.createDirectories(stateDir());
            Path f = stateDir().resolve("manifest.yaml");
            Files.writeString(f, yaml);
            return f;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed writing manifest", e);
        }
    }

    private static String q(String v) { return "'" + escape(v) + "'"; }
    private static String escape(String s) { return s.replace("'", "'\"'\"'"); }
    private static String escSql(String s) { return s.replace("'", "''"); }
    private static String ident(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
}