package io.nop.ib.tasks;

import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task fingerprint (design 08 §三): inputs content hash + outputs existence
 * state, stored at {@code <module>/target/nop-ib/tasks/<name>.fp.json} — inside
 * target/ so a plain {@code mvn clean} removes it together with the derived
 * products it describes.
 */
public final class TaskFingerprint {
    public final String inputsHash;
    public final List<String> outputsPresent;

    public TaskFingerprint(String inputsHash, List<String> outputsPresent) {
        this.inputsHash = inputsHash;
        this.outputsPresent = outputsPresent;
    }

    public static Path fileFor(Path moduleDir, String taskName) {
        return moduleDir.resolve("target/nop-ib/tasks/" + taskName + ".fp.json");
    }

    public void save(Path moduleDir, String taskName) throws IOException {
        Path f = fileFor(moduleDir, taskName);
        Files.createDirectories(f.getParent());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", "1");
        m.put("inputsHash", inputsHash);
        m.put("outputsPresent", outputsPresent);
        Files.writeString(f, JsonOut.write(m));
    }

    /** Returns the stored fingerprint, or null when absent/corrupt (never trust a broken state). */
    public static TaskFingerprint load(Path moduleDir, String taskName) {
        Path f = fileFor(moduleDir, taskName);
        if (!Files.isRegularFile(f))
            return null;
        try {
            Map<String, Object> m = JsonIn.readAsMap(Files.readString(f));
            Object hash = m.get("inputsHash");
            Object outs = m.get("outputsPresent");
            if (!(hash instanceof String h) || !(outs instanceof List<?> l))
                return null;
            return new TaskFingerprint(h, l.stream().map(String::valueOf).toList());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean matches(String nowInputsHash, List<String> nowOutputsPresent) {
        return inputsHash.equals(nowInputsHash) && outputsPresent.equals(nowOutputsPresent);
    }
}
