package io.nop.ib.tasks;

import java.util.List;
import java.util.Map;

/**
 * One declared code-generation task from {@code nop-ib.tasks.json} (design 08 §二).
 * Immutable after validation; the loader is the only producer.
 */
public final class TaskConfig {
    public final String name;
    /** before-compile | after-compile */
    public final String trigger;
    /** module-basedir relative globs */
    public final List<String> inputs;
    public final List<String> outputs;
    public final Exec exec;

    public TaskConfig(String name, String trigger, List<String> inputs, List<String> outputs, Exec exec) {
        this.name = name;
        this.trigger = trigger;
        this.inputs = inputs;
        this.outputs = outputs;
        this.exec = exec;
    }

    public static final class Exec {
        public final String type;
        public final String mainClass;
        public final String script;
        public final List<String> arguments;
        public final String classpathScope;

        public Exec(String type, String mainClass, String script, List<String> arguments, String classpathScope) {
            this.type = type;
            this.mainClass = mainClass;
            this.script = script;
            this.arguments = arguments;
            this.classpathScope = classpathScope;
        }

        public Map<String, Object> toMap() {
            return new java.util.LinkedHashMap<>(java.util.Map.of(
                    "type", type,
                    "mainClass", mainClass == null ? "" : mainClass,
                    "script", script == null ? "" : script,
                    "arguments", arguments,
                    "classpathScope", classpathScope));
        }
    }

    public Map<String, Object> toMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        m.put("trigger", trigger);
        m.put("inputs", inputs);
        m.put("outputs", outputs);
        m.put("exec", exec.toMap());
        return m;
    }

    /**
     * Module-level override merge (design 08 IB-T1): non-exec top-level fields
     * replace wholesale; exec merges field-by-field (override writes only the
     * fields it declares).
     */
    public static TaskConfig merge(TaskConfig base, TaskConfig override) {
        String name = override.name != null ? override.name : base.name;
        String trigger = override.trigger != null ? override.trigger : base.trigger;
        List<String> inputs = override.inputs != null ? override.inputs : base.inputs;
        List<String> outputs = override.outputs != null ? override.outputs : base.outputs;
        Exec e = base.exec;
        Exec o = override.exec;
        Exec mergedExec = new Exec(
                o.type != null ? o.type : e.type,
                o.mainClass != null ? o.mainClass : e.mainClass,
                o.script != null ? o.script : e.script,
                o.arguments != null ? o.arguments : e.arguments,
                o.classpathScope != null ? o.classpathScope : e.classpathScope);
        return new TaskConfig(name, trigger, inputs, outputs, mergedExec);
    }
}
