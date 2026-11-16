package io.nop.ib.model;

import io.nop.ib.NopIbException;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Load/save {@code .nop-ib/model.json} plus staleness detection by pom re-hash
 * (design 05 §5.3: any pom change invalidates the model; a stale model makes
 * every fast-path conclusion invalid).
 */
public final class ModelStore {
    public static final String STATE_DIR = ".nop-ib";
    public static final String MODEL_FILE = "model.json";

    private final Path repoRoot;

    public ModelStore(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public Path modelFile() {
        return repoRoot.resolve(STATE_DIR).resolve(MODEL_FILE);
    }

    public boolean exists() {
        return Files.isRegularFile(modelFile());
    }

    public IbModel load() {
        Path file = modelFile();
        if (!Files.isRegularFile(file))
            throw new NopIbException("model.json not found; run 'nop-ib model refresh' first");
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return IbModel.fromMap(JsonIn.asMapOrEmpty(JsonIn.parse(text)));
        } catch (IOException e) {
            throw new NopIbException("failed to read " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    public void save(IbModel model) {
        Path file = modelFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JsonOut.write(model.toMap()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopIbException("failed to write " + file, e);
        }
    }

    /**
     * Staleness check: re-hash every recorded pom, detect poms ADDED after the
     * and detect a JDK toolchain change (wave6 audit F8 — recorded but never
     * compared). New-module coverage is enforced at PLAN time instead (wave6
     * revision: a pom on disk outside the recorded reactor is NOT staleness —
     * profile-gated/aggregator poms are legitimately unrecorded and plain mvn
     * does not build them either; ScopePlanner routes changed sources outside
     * reactor modules to the Maven fallback, which covers the new-module case
     * without false positives). Returns null when fresh, else a
     * reason string. Also stale when the recorded repo root differs from this
     * one. Maven-version and dumpSha256 revalidation stay at explicit
     * {@code model refresh} / {@code cache verify} only: an {@code mvn -v}
     * subprocess per command would triple the noop cost (design 05 §5.3 note).
     */
    public String stalenessReason(IbModel model) {
        if (!repoRoot.toString().equals(model.getRepoRoot()))
            return "model was built for a different repo root: " + model.getRepoRoot();
        String modelJdk = model.getJdkVersion();
        if (modelJdk != null && !modelJdk.isBlank()) {
            String modelJdkVersion = modelJdk.split(",")[0].trim();
            String nowJdk = System.getProperty("java.version");
            if (nowJdk != null && !nowJdk.equals(modelJdkVersion))
                return "jdk changed since refresh: model=" + modelJdkVersion + " now=" + nowJdk;
        }
        for (Map.Entry<String, String> e : model.getPomHashes().entrySet()) {
            Path pom = repoRoot.resolve(e.getKey());
            if (!Files.isRegularFile(pom))
                return "pom missing: " + e.getKey();
            String now = sha256(pom);
            if (!now.equals(e.getValue()))
                return "pom changed: " + e.getKey();
        }
        return null;
    }


    public static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            return hex(digest.digest(bytes));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new NopIbException("sha256 failed for " + file, e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new NopIbException("SHA-256 unavailable", e);
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
