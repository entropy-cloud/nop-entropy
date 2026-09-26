package io.nop.jq.jsonpath;

import io.nop.core.lang.json.JsonTool;
import io.nop.jq.NopJqErrors;
import io.nop.jq.NopJqException;

import java.util.List;
import java.util.Set;

/**
 * Facade for JsonPath operations. API names and signatures follow fastjson's
 * {@code com.alibaba.fastjson.JSONPath} to ease migration.
 */
public class NopJsonPath {
    private static final JsonPathCompiler COMPILER = new JsonPathCompiler();

    /**
     * Compile a JsonPath expression into a reusable query object.
     */
    public static NopCompiledJsonPath compile(String path) {
        return compile(path, false);
    }

    /**
     * Compile a JsonPath expression with options.
     *
     * @param ignoreNullValue if true, null values are treated as missing
     */
    public static NopCompiledJsonPath compile(String path, boolean ignoreNullValue) {
        List<Segment> segments;
        try {
            segments = COMPILER.compile(path);
        } catch (NopJqException e) {
            throw e;
        } catch (Exception e) {
            throw new NopJqException(NopJqErrors.ERR_JQ_COMPILE_ERROR)
                    .param(NopJqErrors.ARG_EXPR, path);
        }
        return new NopCompiledJsonPath(path, segments);
    }

    /**
     * Evaluate a JsonPath expression against a root object and return the result.
     */
    public static Object eval(Object root, String path) {
        return compile(path).eval(root);
    }

    /**
     * Evaluate a compiled path against a root object.
     */
    public static Object eval(Object root, NopCompiledJsonPath compiledPath) {
        return compiledPath.eval(root);
    }

    /**
     * Evaluate and return only the first result.
     */
    public static Object evalOne(Object root, String path) {
        return compile(path).evalOne(root);
    }

    /**
     * Read a JSON string and evaluate a JsonPath expression against it.
     */
    public static Object read(String json, String path) {
        Object root = JsonTool.parse(json);
        return eval(root, path);
    }

    /**
     * Set a value at the given path. Returns false when the parent container
     * selected by the path (all segments but the last) does not exist;
     * intermediate containers are not created.
     */
    public static boolean set(Object root, String path, Object value) {
        return compile(path).set(root, value);
    }

    /**
     * Remove the value at the given path.
     */
    public static boolean remove(Object root, String path) {
        return compile(path).remove(root);
    }

    /**
     * Get the size (length/count) of the result at the given path.
     */
    public static int size(Object root, String path) {
        return compile(path).size(root);
    }

    /**
     * Check if the path exists and is non-null.
     */
    public static boolean contains(Object root, String path) {
        return compile(path).contains(root);
    }

    /**
     * Check if the path exists and the value equals the expected value.
     */
    public static boolean containsValue(Object root, String path, Object expectedValue) {
        return compile(path).containsValue(root, expectedValue);
    }

    /**
     * Get the property keys of the object at the given path.
     */
    public static Set<String> keySet(Object root, String path) {
        return compile(path).keySet(root);
    }

    /**
     * Get all paths in the result as lists of property names/indices.
     */
    public static List<List<Object>> paths(Object root, String path) {
        return compile(path).paths(root);
    }
}
