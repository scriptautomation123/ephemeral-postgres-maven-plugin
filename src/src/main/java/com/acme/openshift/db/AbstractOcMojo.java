package com.acme.openshift.db;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

abstract class AbstractOcMojo extends AbstractMojo {

    @Parameter(property = "openshift.db.skip", defaultValue = "false")
    protected boolean skip;

    @Parameter(property = "openshift.db.ocBinary", defaultValue = "oc")
    protected String ocBinary;

    @Parameter(property = "openshift.db.namespace", required = true)
    protected String namespace;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    protected String buildDirectory;

    protected Path stateDir() {
        return Path.of(buildDirectory, "openshift-db");
    }

    protected Path stateFile() {
        return stateDir().resolve("state.properties");
    }

    protected void ensureOcAndAccess() throws MojoExecutionException {
        getLog().info("Validating oc login and namespace access early...");
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, null, "whoami"));
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "get", "namespace", namespace));
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "auth", "can-i", "create", "deployment"));
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "auth", "can-i", "create", "service"));
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "auth", "can-i", "create", "secret"));
        OcCommand.run(getLog(), OcCommand.oc(ocBinary, namespace, "auth", "can-i", "create", "pods/exec"));
    }

    protected void saveState(Properties p) throws MojoExecutionException {
        try {
            Files.createDirectories(stateDir());
            try (var out = Files.newOutputStream(stateFile())) {
                p.store(out, "openshift db plugin state");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to save state file: " + stateFile(), e);
        }
    }

    protected Properties loadStateIfExists() throws MojoExecutionException {
        Properties p = new Properties();
        Path file = stateFile();
        if (!Files.exists(file)) return p;
        try (var in = Files.newInputStream(file)) {
            p.load(in);
            return p;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read state file: " + file, e);
        }
    }
}