package io.nop.ai.shell.script;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ScriptEngine {

    String getEngineName();

    Collection<String> getExtensions();

    boolean hasVariable(String name);

    void put(String name, Object value);

    Object get(String name);

    default Map<String, Object> find() {
        return find(null);
    }

    Map<String, Object> find(String name);

    void del(String... vars);

    String toJson(Object object);

    String toString(Object object);

    Map<String, Object> toMap(Object object);

    default Object deserialize(String value) {
        return deserialize(value, null);
    }

    Object deserialize(String value, String format);

    List<String> getSerializationFormats();

    List<String> getDeserializationFormats();

    default void persist(Path file, Object object) {
        persist(file, object, null);
    }

    void persist(Path file, Object object, String format);

    Object execute(String statement) throws Exception;

    default Object execute(Path script) throws Exception {
        return execute(script.toFile(), null);
    }

    default Object execute(File script) throws Exception {
        return execute(script, null);
    }

    default Object execute(Path script, Object[] args) throws Exception {
        return execute(script.toFile(), args);
    }

    Object execute(File script, Object[] args) throws Exception;

    Object execute(Object closure, Object... args);
}
