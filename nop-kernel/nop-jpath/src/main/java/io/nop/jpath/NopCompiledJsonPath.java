package io.nop.jpath;

import io.nop.jpath.JsonAccessor;
import io.nop.jpath.NopJsonAccessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A compiled JsonPath expression that can be reused against multiple root objects.
 * Created by {@link NopJsonPath#compile(String)}.
 */
public class NopCompiledJsonPath {
    private static final JsonPathExecutor SHARED_EXECUTOR = new JsonPathExecutor(NopJsonAccessor.INSTANCE);

    private final String pathString;
    private final List<Segment> segments;

    NopCompiledJsonPath(String pathString, List<Segment> segments) {
        this.pathString = pathString;
        this.segments = segments;
    }

    public String getPathString() {
        return pathString;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public Object eval(Object root) {
        return SHARED_EXECUTOR.execute(segments, root);
    }

    public Object evalOne(Object root) {
        Object result = eval(root);
        if (result instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) result;
            return list.isEmpty() ? null : list.get(0);
        }
        return result;
    }

    /**
     * Set a value at the last property segment of this path.
     * Walks the path to find the parent, then sets the property.
     */
    public boolean set(Object root, Object value) {
        JsonAccessor accessor = NopJsonAccessor.INSTANCE;
        if (segments.isEmpty())
            return false;

        Segment lastSegment = segments.get(segments.size() - 1);

        if (lastSegment instanceof FusedPropertySegment) {
            String[] props = ((FusedPropertySegment) lastSegment).getPropertyNames();
            if (props.length == 0)
                return false;
            // Evaluate all segments except the last fused segment, then walk the remaining properties
            List<Segment> parentSegments = segments.subList(0, segments.size() - 1);
            Object current = SHARED_EXECUTOR.execute(parentSegments, root);
            // Walk all but the last property name to find the parent
            for (int i = 0; i < props.length - 1; i++) {
                if (current == null)
                    return false;
                current = accessor.getProperty(current, props[i]);
            }
            if (current == null)
                return false;
            accessor.setProperty(current, props[props.length - 1], value);
            return true;
        }

        // Find the parent: all segments except the last
        List<Segment> parentSegments = segments.subList(0, segments.size() - 1);
        Object parent = SHARED_EXECUTOR.execute(parentSegments, root);
        if (parent == null)
            return false;

        if (lastSegment instanceof PropertySegment) {
            accessor.setProperty(parent, ((PropertySegment) lastSegment).getPropertyName(), value);
            return true;
        }
        if (lastSegment instanceof ArrayAccessSegment) {
            int index = ((ArrayAccessSegment) lastSegment).getIndex();
            if (accessor.isArray(parent)) {
                int actualIndex = index < 0 ? accessor.size(parent) + index : index;
                if (actualIndex >= 0 && actualIndex < accessor.size(parent)) {
                    accessor.setArrayItem(parent, actualIndex, value);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove the value at the last property segment of this path.
     */
    public boolean remove(Object root) {
        JsonAccessor accessor = NopJsonAccessor.INSTANCE;
        if (segments.isEmpty())
            return false;

        Segment lastSegment = segments.get(segments.size() - 1);

        if (lastSegment instanceof FusedPropertySegment) {
            String[] props = ((FusedPropertySegment) lastSegment).getPropertyNames();
            if (props.length == 0)
                return false;
            List<Segment> parentSegments = segments.subList(0, segments.size() - 1);
            Object current = SHARED_EXECUTOR.execute(parentSegments, root);
            for (int i = 0; i < props.length - 1; i++) {
                if (current == null)
                    return false;
                current = accessor.getProperty(current, props[i]);
            }
            if (current == null)
                return false;
            if (current instanceof java.util.Map) {
                ((java.util.Map<?, ?>) current).remove(props[props.length - 1]);
                return true;
            }
            return false;
        }

        List<Segment> parentSegments = segments.subList(0, segments.size() - 1);
        Object parent = SHARED_EXECUTOR.execute(parentSegments, root);
        if (parent == null)
            return false;

        if (lastSegment instanceof PropertySegment) {
            if (parent instanceof java.util.Map) {
                ((java.util.Map<?, ?>) parent).remove(((PropertySegment) lastSegment).getPropertyName());
                return true;
            }
        }
        return false;
    }

    public int size(Object root) {
        Object result = eval(root);
        if (result == null)
            return 0;
        if (result instanceof List)
            return ((List<?>) result).size();
        if (result instanceof java.util.Map)
            return ((java.util.Map<?, ?>) result).size();
        return 1;
    }

    public boolean contains(Object root) {
        return eval(root) != null;
    }

    /**
     * Check if the path exists and the value equals the expected value.
     */
    public boolean containsValue(Object root, Object expectedValue) {
        Object actual = eval(root);
        if (actual == null)
            return expectedValue == null;
        if (expectedValue == null)
            return false;
        return actual.equals(expectedValue);
    }

    /**
     * Get the property keys of the object at this path.
     */
    public Set<String> keySet(Object root) {
        Object result = eval(root);
        if (result == null)
            return new LinkedHashSet<>();
        JsonAccessor accessor = NopJsonAccessor.INSTANCE;
        if (accessor.isMap(result)) {
            return accessor.getPropertyNames(result);
        }
        return new LinkedHashSet<>();
    }

    /**
     * Get all paths (as lists of property names/indices) in the result.
     */
    public List<List<Object>> paths(Object root) {
        Object result = eval(root);
        if (result == null)
            return new ArrayList<>();
        List<List<Object>> allPaths = new ArrayList<>();
        collectPaths(result, new ArrayList<>(), allPaths);
        return allPaths;
    }

    private void collectPaths(Object obj, List<Object> currentPath, List<List<Object>> allPaths) {
        JsonAccessor accessor = NopJsonAccessor.INSTANCE;
        if (accessor.isMap(obj)) {
            for (String key : accessor.getPropertyNames(obj)) {
                List<Object> path = new ArrayList<>(currentPath);
                path.add(key);
                allPaths.add(path);
                Object child = accessor.getProperty(obj, key);
                if (child != null && (accessor.isMap(child) || accessor.isArray(child))) {
                    collectPaths(child, path, allPaths);
                }
            }
        } else if (accessor.isArray(obj)) {
            int size = accessor.size(obj);
            for (int i = 0; i < size; i++) {
                List<Object> path = new ArrayList<>(currentPath);
                path.add(i);
                allPaths.add(path);
                Object child = accessor.getArrayItem(obj, i);
                if (child != null && (accessor.isMap(child) || accessor.isArray(child))) {
                    collectPaths(child, path, allPaths);
                }
            }
        }
    }

    @Override
    public String toString() {
        return pathString;
    }
}
