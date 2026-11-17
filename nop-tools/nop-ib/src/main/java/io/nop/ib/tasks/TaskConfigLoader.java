package io.nop.ib.tasks;

import io.nop.ib.NopIbException;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loader for {@code nop-ib.tasks.json} (design 08 IB-T1): discovery along the
 * root→module path chain, root-first / module-last merge (same-name tasks
 * overridden by the module level), fail-loud validation — unknown fields,
 * missing required fields and broken globs are explicit config errors
 * (NopIbException → CLI exit 3), never silent skips.
 */
public final class TaskConfigLoader {
    public static final String FILE_NAME = "nop-ib.tasks.json";
    private static final Set0 ALLOWED = new Set0();

    /** Parses one config file into validated tasks. */
    public static List<TaskConfig> parseFile(Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            throw new NopIbException("cannot read " + file + ": " + e.getMessage(), e);
        }
        Object parsed;
        try {
            parsed = JsonIn.parse(text);
        } catch (io.nop.ib.json.JsonIn.NopIbJsonException e) {
            throw new NopIbException(file + ": malformed JSON: " + e.getMessage(), e);
        }
        if (!(parsed instanceof List<?> array))
            throw new NopIbException(file + ": top level must be a JSON array of tasks");
        List<TaskConfig> tasks = new ArrayList<>();
        for (Object item : array) {
            if (!(item instanceof Map<?, ?> raw))
                throw new NopIbException(file + ": each task must be a JSON object");
            tasks.add(parseTask(file, (Map<String, Object>) raw));
        }
        return tasks;
    }

    /**
     * Merged task set for a module: every config file on the repo-root → basedir
     * path chain participates, root first; same-name module entries override.
     */
    public static List<TaskConfig> loadForModule(Path repoRoot, String moduleBasedir) {
        Path moduleDir = moduleBasedir.isEmpty() || ".".equals(moduleBasedir)
                ? repoRoot : repoRoot.resolve(moduleBasedir).normalize();
        if (!moduleDir.startsWith(repoRoot))
            throw new NopIbException("module basedir escapes repo root: " + moduleBasedir);
        Map<String, TaskConfig> merged = new LinkedHashMap<>();
        Path cursor = moduleDir;
        List<Path> chain = new ArrayList<>();
        while (true) {
            Path f = cursor.resolve(FILE_NAME);
            if (Files.isRegularFile(f))
                chain.add(f);
            if (cursor.equals(repoRoot))
                break;
            Path parent = cursor.getParent();
            if (parent == null || !parent.startsWith(repoRoot))
                break;
            cursor = parent;
        }
        // root first
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (TaskConfig t : parseFile(chain.get(i))) {
                TaskConfig prev = merged.get(t.name);
                merged.put(t.name, prev != null ? TaskConfig.merge(prev, t) : t);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static TaskConfig parseTask(Path file, Map<String, Object> raw) {
        for (String key : raw.keySet())
            if (!ALLOWED.top.contains(key))
                throw new NopIbException(file + ": unknown task field '" + key + "'");
        String name = str(file, raw, "name", true);
        String trigger = str(file, raw, "trigger", true);
        if (!"before-compile".equals(trigger) && !"after-compile".equals(trigger))
            throw new NopIbException(file + ": task '" + name + "' trigger must be before-compile|after-compile, got '"
                    + trigger + "'");
        List<String> inputs = strs(file, raw, "inputs", name, true);
        List<String> outputs = strs(file, raw, "outputs", name, true);
        Object execRaw = raw.get("exec");
        if (!(execRaw instanceof Map<?, ?>))
            throw new NopIbException(file + ": task '" + name + "' requires an exec object");
        TaskConfig.Exec exec = parseExec(file, name, (Map<String, Object>) execRaw);
        // eager glob validation: a broken glob is a config error, not a runtime surprise
        for (String g : inputs)
            GlobMatcher.compile(g);
        for (String g : outputs)
            GlobMatcher.compile(g);
        return new TaskConfig(name, trigger, inputs, outputs, exec);
    }

    private static TaskConfig.Exec parseExec(Path file, String name, Map<String, Object> raw) {
        for (String key : raw.keySet())
            if (!ALLOWED.exec.contains(key))
                throw new NopIbException(file + ": task '" + name + "' unknown exec field '" + key + "'");
        String type = str(file, raw, "type", true);
        if (!"java".equals(type) && !"xscript".equals(type))
            throw new NopIbException(file + ": task '" + name + "' exec.type must be java|xscript, got '" + type + "'");
        String mainClass = str(file, raw, "mainClass", true);
        String script = str(file, raw, "script", false);
        if ("java".equals(type) && script != null)
            throw new NopIbException(file + ": task '" + name + "' exec.script is only valid for xscript");
        if ("xscript".equals(type) && script == null)
            throw new NopIbException(file + ": task '" + name + "' xscript requires exec.script");
        List<String> arguments = strs(file, raw, "arguments", name, false);
        String scope = str(file, raw, "classpathScope", false);
        if (scope == null)
            scope = "test"; // design 08 IB-T2 default
        if (!"compile".equals(scope) && !"runtime".equals(scope) && !"test".equals(scope))
            throw new NopIbException(file + ": task '" + name + "' classpathScope must be compile|runtime|test, got '"
                    + scope + "'");
        return new TaskConfig.Exec(type, mainClass, script, arguments == null ? List.of() : arguments, scope);
    }

    private static String str(Path file, Map<String, Object> raw, String key, boolean required) {
        Object v = raw.get(key);
        if (v == null) {
            if (required)
                throw new NopIbException(file + ": missing required field '" + key + "'");
            return null;
        }
        if (!(v instanceof String s) || s.isBlank())
            throw new NopIbException(file + ": field '" + key + "' must be a non-empty string");
        return s;
    }

    private static List<String> strs(Path file, Map<String, Object> raw, String key, String taskName,
                                     boolean required) {
        Object v = raw.get(key);
        if (v == null) {
            if (required)
                throw new NopIbException(file + ": task '" + taskName + "' missing required field '" + key + "'");
            return null;
        }
        if (!(v instanceof List<?> list) || list.isEmpty())
            throw new NopIbException(file + ": task '" + taskName + "' field '" + key + "' must be a non-empty array");
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof String s) || s.isBlank())
                throw new NopIbException(file + ": task '" + taskName + "' field '" + key
                        + "' entries must be non-empty strings");
            out.add(s);
        }
        return out;
    }

    /** serialization helper for diagnostics */
    public static String writeTasks(List<TaskConfig> tasks) {
        List<Map<String, Object>> arr = new ArrayList<>();
        for (TaskConfig t : tasks)
            arr.add(t.toMap());
        return JsonOut.write(arr);
    }

    private static final class Set0 {
        final java.util.Set<String> top = java.util.Set.of("name", "trigger", "inputs", "outputs", "exec");
        final java.util.Set<String> exec = java.util.Set.of("type", "mainClass", "script", "arguments",
                "classpathScope");
    }
}
