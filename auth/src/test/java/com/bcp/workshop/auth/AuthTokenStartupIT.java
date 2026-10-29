package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenStartupIT {
    private static final String VALID_SECRET = "integration-test-only-signing-key-0001";
    private static final String SHORT_SECRET = "short-runtime-secret";
    private static final long STARTUP_TIMEOUT_SECONDS = 30;

    @Test
    void startsWithValidExternalSecret() throws Exception {
        try (RunningApplication application = launch("valid", VALID_SECRET)) {
            assertTrue(application.awaitListening(), "Runtime profile should start with a valid external secret");
            assertFalse(application.output().contains(VALID_SECRET), "Startup output must not disclose the signing secret");
        }
    }

    @Test
    void rejectsMissingExternalSecretWithoutDisclosingASecret() throws Exception {
        assertRejected("missing", null, null);
    }

    @Test
    void rejectsShortExternalSecretWithoutDisclosingItsValue() throws Exception {
        assertRejected("short", SHORT_SECRET, SHORT_SECRET);
    }

    private static void assertRejected(String scenario, String configuredSecret, String secretToProtect) throws Exception {
        try (RunningApplication application = launch(scenario, configuredSecret)) {
            assertFalse(application.awaitListening(), "Runtime profile must reject an invalid signing secret");
            assertFalse(application.process().isAlive(), "Runtime process should exit when its signing secret is invalid");
            assertNotEquals(0, application.process().exitValue(), "Runtime process should fail to start");
            if (secretToProtect != null) {
                assertFalse(application.output().contains(secretToProtect), "Startup diagnostics must not disclose the signing secret");
            }
        }
    }

    private static RunningApplication launch(String scenario, String secret) throws IOException {
        Path runner = Path.of("target", "quarkus-app", "quarkus-run.jar").toAbsolutePath();
        ProcessBuilder processBuilder = new ProcessBuilder(javaExecutable(), "-Dquarkus.http.port=0", "-jar", runner.toString())
            .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.put("QUARKUS_PROFILE", "prod");
        environment.put("QUARKUS_HTTP_PORT", "0");
        environment.put("AUTH_DB_URL", "jdbc:sqlite:./target/auth-startup-" + scenario + ".db");
        environment.remove("AUTH_TOKEN_SECRET");
        if (secret != null) {
            environment.put("AUTH_TOKEN_SECRET", secret);
        }
        return new RunningApplication(processBuilder.start());
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static final class RunningApplication implements AutoCloseable {
        private final Process process;
        private final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
        private final StringBuilder capturedOutput = new StringBuilder();
        private final Thread outputReader;

        private RunningApplication(Process process) {
            this.process = process;
            outputReader = new Thread(this::captureOutput);
            outputReader.setDaemon(true);
            outputReader.start();
        }

        private Process process() {
            return process;
        }

        private boolean awaitListening() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                String line = outputLines.poll(100, TimeUnit.MILLISECONDS);
                if (line != null && line.contains("Listening on:")) {
                    return true;
                }
                if (!process.isAlive()) {
                    return false;
                }
            }
            return output().contains("Listening on:");
        }

        private void captureOutput() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (capturedOutput) {
                        capturedOutput.append(line).append(System.lineSeparator());
                    }
                    outputLines.offer(line);
                }
            } catch (IOException error) {
                outputLines.offer("Unable to read process output");
            }
        }

        private String output() {
            synchronized (capturedOutput) {
                return capturedOutput.toString();
            }
        }

        @Override
        public void close() throws Exception {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                }
            }
            outputReader.join(TimeUnit.SECONDS.toMillis(5));
        }
    }
}