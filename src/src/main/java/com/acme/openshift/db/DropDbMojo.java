package com.acme.openshift.db;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.nio.file.Files;
import java.util.Properties;

@Mojo(name = "drop-db", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, threadSafe = true)
public class DropDbMojo extends AbstractOcMojo {

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("openshift.db.skip=true, skipping drop-db");
            return;
        }

        // Best-effort cleanup; idempotent
        Properties st = loadStateIfExists();
        if (st.isEmpty()) {
            getLog().warn("No state file found; nothing to cleanup.");
            return;
        }

        String runId = st.getProperty("runId", "");
        String app = st.getProperty("app", "");
        String dbName = st.getProperty("dbName", "");
        String dbUser = st.getProperty("dbUser", "");
        String adminUser = st.getProperty("adminUser", "postgres");
        String adminPassword = st.getProperty("adminPassword", "");

        // Try DB drop first; ignore failures to keep cleanup idempotent
        if (!app.isBlank() && !dbName.isBlank()) {
            OcCommand.runIgnoreExit(getLog(), OcCommand.oc(ocBinary, namespace, "exec", "deployment/" + app, "--",
                    "bash", "-lc",
                    "export PGPASSWORD='" + adminPassword.replace("'", "'\"'\"'") + "'; " +
                            "psql -v ON_ERROR_STOP=1 -U '" + adminUser + "' -d postgres -c " +
                            "\"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '" + dbName + "' AND pid <> pg_backend_pid();\"; " +
                            "psql -v ON_ERROR_STOP=1 -U '" + adminUser + "' -d postgres -c \"DROP DATABASE IF EXISTS \\\"" + dbName + "\\\";\"; " +
                            "psql -v ON_ERROR_STOP=1 -U '" + adminUser + "' -d postgres -c \"DROP ROLE IF EXISTS \\\"" + dbUser + "\\\";\""
            ));
        }

        // Delete all test resources by label test-run=<runId>
        if (!runId.isBlank()) {
            OcCommand.runIgnoreExit(getLog(), OcCommand.oc(ocBinary, namespace, "delete",
                    "all,secret", "-l", "test-run=" + runId, "--ignore-not-found=true"));
        }

        // remove local state
        try {
            Files.deleteIfExists(stateFile());
        } catch (Exception e) {
            getLog().warn("Could not delete state file: " + e.getMessage());
        }

        getLog().info("Cleanup completed (idempotent) for test-run=" + runId);
    }
}