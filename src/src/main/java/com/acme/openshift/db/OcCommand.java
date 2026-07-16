package com.acme.openshift.db;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class OcCommand {

    static Result run(Log log, List<String> command) throws MojoExecutionException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        try {
            Process p = pb.start();
            String stdout = readFully(p.getInputStream());
            String stderr = readFully(p.getErrorStream());
            int exit = p.waitFor();

            if (exit != 0) {
                throw new MojoExecutionException("Command failed (" + exit + "): "
                        + String.join(" ", command) + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr);
            }

            if (stdout != null && !stdout.isBlank()) {
                log.debug(stdout.trim());
            }
            return new Result(exit, stdout, stderr);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Failed running command: " + String.join(" ", command), e);
        }
    }

    static Result runIgnoreExit(Log log, List<String> command) throws MojoExecutionException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            String stdout = readFully(p.getInputStream());
            String stderr = readFully(p.getErrorStream());
            int exit = p.waitFor();
            if (stdout != null && !stdout.isBlank()) log.debug(stdout.trim());
            if (stderr != null && !stderr.isBlank()) log.debug(stderr.trim());
            return new Result(exit, stdout, stderr);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Failed running command: " + String.join(" ", command), e);
        }
    }

    static List<String> oc(String ocBinary, String namespace, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ocBinary);
        if (namespace != null && !namespace.isBlank()) {
            cmd.add("-n");
            cmd.add(namespace);
        }
        for (String arg : args) cmd.add(arg);
        return cmd;
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        return out.toString(StandardCharsets.UTF_8);
    }

    static final class Result {
        final int exitCode;
        final String stdout;
        final String stderr;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private OcCommand() {}
}