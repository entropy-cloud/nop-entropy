package io.nop.jq.jq.runtime;

import io.nop.jq.jq.ast.AlternativeNode;
import io.nop.jq.jq.ast.BindNode;
import io.nop.jq.jq.ast.EmptyNode;
import io.nop.jq.jq.ast.FuncDefNode;
import io.nop.jq.jq.ast.CommaNode;
import io.nop.jq.jq.ast.FuncCallNode;
import io.nop.jq.jq.ast.IdentityNode;
import io.nop.jq.jq.ast.IfThenElseNode;
import io.nop.jq.jq.ast.IndexAccessNode;
import io.nop.jq.jq.ast.IteratorNode;
import io.nop.jq.jq.ast.JqAstNode;
import io.nop.jq.jq.ast.PipeNode;
import io.nop.jq.jq.ast.RecursiveDescentNode;
import io.nop.jq.jq.ast.SelectNode;
import io.nop.jq.jq.ast.SliceNode;
import io.nop.jq.jq.ast.TryCatchNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Path-expression evaluation for assignment (=, |=, += ...), del() and path().
 * Computes the concrete paths (lists of keys/indices) that a filter selects
 * on a given input.
 */
public final class JqPathEval {

    /**
     * A slice segment of a path (from assignment targets like .[2:4]).
     * Only meaningful for setAt/deleteAt; value lookup never produces these.
     */
    public record SlicePath(int from, int to) {
    }

    @FunctionalInterface
    public interface NodeExecutor {
        /**
         * Execute node with input, collecting outputs into sink.
         */
        void executeInto(JqAstNode node, JqValue input, JqEnvironment env, List<JqValue> sink);
    }

    private final NodeExecutor executor;

    public JqPathEval(NodeExecutor executor) {
        this.executor = executor;
    }

    /** Compute every path selected by expr on input, in output order. */
    public List<List<Object>> evalPaths(JqAstNode expr, JqValue input, JqEnvironment env) {
        List<List<Object>> paths = new ArrayList<>();
        collect(expr, input, env, new ArrayList<>(), paths);
        return paths;
    }

    @SuppressWarnings("unchecked")
    private void collect(JqAstNode node, JqValue value, JqEnvironment env,
                         List<Object> prefix, List<List<Object>> paths) {
        if (node instanceof IdentityNode) {
            paths.add(new ArrayList<>(prefix));
        } else if (node instanceof PipeNode pipe) {
            List<List<Object>> leftPaths;
            try {
                leftPaths = evalPaths(pipe.left(), value, env);
            } catch (JqInvalidPathException e) {
                // jq describes the failure through the consumer of the bad value
                throw describeNearAttempt(pipe.right(), e);
            }
            for (List<Object> leftPath : leftPaths) {
                JqValue sub = getAt(value, leftPath);
                List<Object> extendedPrefix = new ArrayList<>(prefix);
                extendedPrefix.addAll(leftPath);
                collect(pipe.right(), sub, env, extendedPrefix, paths);
            }
        } else if (node instanceof CommaNode comma) {
            collect(comma.left(), value, env, prefix, paths);
            collect(comma.right(), value, env, prefix, paths);
        } else if (node instanceof io.nop.jq.jq.ast.FieldAccessNode field) {
            List<List<Object>> objPaths;
            if (field.hasExplicitObject()) {
                try {
                    objPaths = evalPaths(field.object(), value, env);
                } catch (JqInvalidPathException e) {
                    throw describeNearAttempt(node, e);
                }
            } else {
                objPaths = List.of(new ArrayList<>());
            }
            for (List<Object> objPath : objPaths) {
                JqValue sub = getAt(value, objPath);
                if (sub != null && !(sub instanceof JqNull) && !(sub instanceof JqObject)) {
                    throw new JqRuntimeException("Cannot index " + sub.typeName()
                            + " with string \"" + field.fieldName() + "\"");
                }
                List<Object> path = new ArrayList<>(prefix);
                path.addAll(objPath);
                path.add(JqString.of(field.fieldName()));
                paths.add(path);
            }
        } else if (node instanceof IndexAccessNode index) {
            List<List<Object>> objPaths;
            if (index.hasExplicitObject()) {
                try {
                    objPaths = evalPaths(index.object(), value, env);
                } catch (JqInvalidPathException e) {
                    List<JqValue> keys = new ArrayList<>();
                    executor.executeInto(index.index(), value, env, keys);
                    for (JqValue key : keys) {
                        throw new JqInvalidPathException(
                                "Invalid path expression near attempt to access element "
                                        + JqPrinter.print(key) + " of "
                                        + JqPrinter.print(e.value()), e.value());
                    }
                    return;
                }
            } else {
                objPaths = List.of(new ArrayList<>());
            }
            List<JqValue> keys = new ArrayList<>();
            executor.executeInto(index.index(), value, env, keys);
            for (List<Object> objPath : objPaths) {
                JqValue sub = getAt(value, objPath);
                for (JqValue key : keys) {
                    JqValue resolved = key;
                    if (key instanceof JqNumber num && num.doubleValue() < 0
                            && sub instanceof JqArray arr) {
                        resolved = JqNumber.of(arr.size() + num.intValue());
                    }
                    List<Object> path = new ArrayList<>(prefix);
                    path.addAll(objPath);
                    path.add(resolved);
                    paths.add(path);
                }
            }
        } else if (node instanceof IteratorNode iter) {
            List<List<Object>> objPaths;
            if (iter.hasExplicitObject()) {
                try {
                    objPaths = evalPaths(iter.object(), value, env);
                } catch (JqInvalidPathException e) {
                    throw describeNearAttempt(node, e);
                }
            } else {
                objPaths = List.of(new ArrayList<>());
            }
            for (List<Object> objPath : objPaths) {
                JqValue sub = getAt(value, objPath);
                if (sub instanceof JqArray arr) {
                    for (int i = 0; i < arr.size(); i++) {
                        List<Object> path = new ArrayList<>(prefix);
                        path.addAll(objPath);
                        path.add(JqNumber.of(i));
                        paths.add(path);
                    }
                } else if (sub instanceof JqObject obj) {
                    for (String key : obj.keySet()) {
                        List<Object> path = new ArrayList<>(prefix);
                        path.addAll(objPath);
                        path.add(JqString.of(key));
                        paths.add(path);
                    }
                } else if (!(sub instanceof JqNull)) {
                    throw new JqRuntimeException("Cannot iterate over "
                            + sub.typeName() + " (" + JqPrinter.print(sub) + ")");
                }
            }
        } else if (node instanceof SliceNode slice) {
            List<List<Object>> objPaths = slice.hasExplicitObject()
                    ? evalPaths(slice.object(), value, env)
                    : List.of(new ArrayList<>());
            for (List<Object> objPath : objPaths) {
                JqValue sub = getAt(value, objPath);
                List<JqValue> startVals = new ArrayList<>();
                List<JqValue> endVals = new ArrayList<>();
                if (slice.start() != null)
                    executor.executeInto(slice.start(), value, env, startVals);
                if (slice.end() != null)
                    executor.executeInto(slice.end(), value, env, endVals);
                int[] range = sliceRange(sub, startVals, endVals);
                if (range != null) {
                    List<Object> path = new ArrayList<>(prefix);
                    path.addAll(objPath);
                    path.add(new SlicePath(range[0], range[1]));
                    paths.add(path);
                }
            }
        } else if (node instanceof SelectNode select) {
            List<JqValue> cond = new ArrayList<>();
            executor.executeInto(select.condition(), value, env, cond);
            if (!cond.isEmpty() && JqTruthiness.of(cond.get(0))) {
                paths.add(new ArrayList<>(prefix));
            }
        } else if (node instanceof FuncCallNode call) {
            collectCall(call, value, env, prefix, paths);
        } else if (node instanceof TryCatchNode tc) {
            try {
                collect(tc.tryExpr(), value, env, prefix, paths);
            } catch (JqRuntimeException e) {
                // try paths: errors simply contribute no paths
            }
        } else if (node instanceof IfThenElseNode ifNode) {
            List<JqValue> cond = new ArrayList<>();
            executor.executeInto(ifNode.condition(), value, env, cond);
            for (JqValue c : cond) {
                if (JqTruthiness.of(c)) {
                    collect(ifNode.thenBranch(), value, env, prefix, paths);
                } else if (ifNode.elseBranch() != null) {
                    collect(ifNode.elseBranch(), value, env, prefix, paths);
                }
            }
        } else if (node instanceof AlternativeNode alt) {
            int before = paths.size();
            try {
                collect(alt.left(), value, env, prefix, paths);
            } catch (JqRuntimeException e) {
                // fall through to right side
            }
            collect(alt.right(), value, env, prefix, paths);
        } else if (node instanceof io.nop.jq.jq.ast.RecursiveDescentNode recurse) {
            // .. as a path expression: every descendant path
            collectRecursiveValuePaths(value, new ArrayList<>(prefix), paths);
        } else if (node instanceof BindNode bind) {
            // bind the current value (not the input) the way `E as $x` does
            NodeExecutor exec = this.executor;
            List<JqValue> source = new ArrayList<>();
            exec.executeInto(bind.expr(), value, env, source);
            for (JqValue val : source) {
                JqDestructurer destructurer = new JqDestructurer(
                        (expr, input2, env2) -> {
                            List<JqValue> out = new ArrayList<>();
                            exec.executeInto(expr, input2, env2, out);
                            return out;
                        });
                Map<String, JqValue> bindings =
                        destructurer.destructureTop(bind.pattern(), val, env);
                if (bindings == null)
                    continue;
                env.pushScope();
                try {
                    for (Map.Entry<String, JqValue> b : bindings.entrySet()) {
                        env.bind(b.getKey(), b.getValue());
                    }
                    collect(bind.body(), value, env, prefix, paths);
                } finally {
                    env.popScope();
                }
            }
        } else if (node instanceof EmptyNode) {
            // empty contributes no paths
        } else if (node instanceof FuncDefNode def) {
            env.defineFunction(def.name(), def);
            collect(def.body(), value, env, prefix, paths);
        } else {
            List<JqValue> values = new ArrayList<>();
            executor.executeInto(node, value, env, values);
            JqValue first = values.isEmpty() ? JqValue.NULL : values.get(0);
            throw new JqInvalidPathException("Invalid path expression with result "
                    + JqPrinter.print(first), first);
        }
    }

    private void collectCall(FuncCallNode call, JqValue value, JqEnvironment env,
                             List<Object> prefix, List<List<Object>> paths) {
        switch (call.name()) {
            case "select" -> {
                List<JqValue> cond = new ArrayList<>();
                executor.executeInto(call.args().get(0), value, env, cond);
                if (!cond.isEmpty() && JqTruthiness.of(cond.get(0))) {
                    paths.add(new ArrayList<>(prefix));
                }
            }
            case "recurse" -> {
                // recurse without paths support detail: emit the identity path
                collectRecursivePaths(value, env, prefix, paths,
                        call.args().isEmpty() ? null : call.args().get(0));
            }
            case "first", "last", "limit" -> {
                if (call.args().isEmpty()) {
                    // first/0 == .[0], last/0 == .[-1]
                    List<Object> path = new ArrayList<>(prefix);
                    path.add(JqNumber.of(call.name().equals("first") ? 0 : -1));
                    paths.add(path);
                } else {
                    JqAstNode inner = call.args().get(call.args().size() - 1);
                    collect(inner, value, env, prefix, paths);
                }
            }
            case "getpath" -> {
                List<JqValue> pathVals = new ArrayList<>();
                executor.executeInto(call.args().get(0), value, env, pathVals);
                for (JqValue pv : pathVals) {
                    if (pv instanceof JqArray arr) {
                        List<Object> path = new ArrayList<>(prefix);
                        path.addAll(arr.items());
                        paths.add(path);
                    }
                }
            }
            default -> {
                Object def = env.getFunction(JqExecutor.functionKey(call.name(), call.args().size()));
                if (def == null)
                    def = env.getFunction(call.name());
                if (def instanceof JqClosureFn closure) {
                    collect(closure.body(), value, closure.env(), prefix, paths);
                } else if (def instanceof JqFunctionDef fnDef) {
                    collect(fnDef.def().body(), value, fnDef.definitionEnv(), prefix, paths);
                } else if (def instanceof FuncDefNode fd) {
                    env.defineFunction(fd.name(), fd);
                    collect(fd.body(), value, env, prefix, paths);
                } else {
                    List<JqValue> values = new ArrayList<>();
                    executor.executeInto(call, value, env, values);
                    JqValue first = values.isEmpty() ? JqValue.NULL : values.get(0);
                    throw new JqInvalidPathException("Invalid path expression with result "
                            + JqPrinter.print(first), first);
                }
            }
        }
    }

    /**
     * jq reports a non-path result from the left of a pipe through the lens of
     * the operation that tried to consume it: "Invalid path expression near
     * attempt to access element 0 of ..." / "... to iterate through ...".
     */
    private JqInvalidPathException describeNearAttempt(JqAstNode consumer,
                                                       JqInvalidPathException cause) {
        // jq attributes the failure to the innermost failed operation only
        if (cause.getMessage().startsWith("Invalid path expression near attempt"))
            return cause;
        if (consumer instanceof IndexAccessNode index) {
            List<JqValue> keys = new ArrayList<>();
            try {
                executor.executeInto(index.index(), JqValue.NULL, new JqEnvironment(), keys);
            } catch (JqRuntimeException ignored) {
                // fall through to the generic message
            }
            for (JqValue key : keys) {
                return new JqInvalidPathException(
                        "Invalid path expression near attempt to access element "
                                + JqPrinter.print(key) + " of "
                                + JqPrinter.print(cause.value()), cause.value());
            }
        }
        if (consumer instanceof IteratorNode) {
            return new JqInvalidPathException(
                    "Invalid path expression near attempt to iterate through "
                            + JqPrinter.print(cause.value()), cause.value());
        }
        if (consumer instanceof io.nop.jq.jq.ast.FieldAccessNode field) {
            return new JqInvalidPathException(
                    "Invalid path expression near attempt to access element \""
                            + field.fieldName() + "\" of "
                            + JqPrinter.print(cause.value()), cause.value());
        }
        return cause;
    }

    private void collectRecursiveValuePaths(JqValue value, List<Object> prefix,
                                            List<List<Object>> paths) {
        paths.add(new ArrayList<>(prefix));
        if (value instanceof JqObject obj) {
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<Object> path = new ArrayList<>(prefix);
                path.add(JqString.of(e.getKey()));
                collectRecursiveValuePaths(e.getValue(), path, paths);
            }
        } else if (value instanceof JqArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                List<Object> path = new ArrayList<>(prefix);
                path.add(JqNumber.of(i));
                collectRecursiveValuePaths(arr.get(i), path, paths);
            }
        }
    }

    private void collectRecursivePaths(JqValue value, JqEnvironment env,
                                       List<Object> prefix, List<List<Object>> paths,
                                       JqAstNode filter) {
        paths.add(new ArrayList<>(prefix));
        if (value instanceof JqObject obj) {
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<Object> path = new ArrayList<>(prefix);
                path.add(JqString.of(e.getKey()));
                collectRecursivePaths(e.getValue(), env, path, paths, filter);
            }
        } else if (value instanceof JqArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                List<Object> path = new ArrayList<>(prefix);
                path.add(JqNumber.of(i));
                collectRecursivePaths(arr.get(i), env, path, paths, filter);
            }
        }
    }

    /** Normalized [from, to) slice bounds, or null when the container is not sliceable. */
    private int[] sliceRange(JqValue sub, List<JqValue> startVals, List<JqValue> endVals) {
        int start = 0;
        int end;
        if (sub instanceof JqArray arr) {
            end = arr.size();
        } else if (sub instanceof JqString s) {
            end = s.value().length();
        } else {
            return null;
        }
        if (!startVals.isEmpty() && startVals.get(0) instanceof JqNumber sn)
            start = clamp(sn.doubleValue() < 0 ? (int) Math.ceil(sn.doubleValue()) + len(sub)
                    : (int) Math.floor(sn.doubleValue()), 0, len(sub));
        if (!endVals.isEmpty() && endVals.get(0) instanceof JqNumber en)
            end = clamp(en.doubleValue() < 0 ? (int) Math.ceil(en.doubleValue()) + len(sub)
                    : (int) Math.ceil(en.doubleValue()), 0, len(sub));
        return new int[]{start, end};
    }

    private int len(JqValue v) {
        if (v instanceof JqArray a)
            return a.size();
        if (v instanceof JqString s)
            return s.value().length();
        return 0;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    /** Read the value at a concrete path. */
    public static JqValue getAt(JqValue value, List<?> path) {
        JqValue current = value;
        for (Object keyObj : path) {
            if (!(keyObj instanceof JqValue key)) {
                throw new JqRuntimeException("Invalid path component");
            }
            if (current == null)
                return JqValue.NULL;
            if (key instanceof JqString s) {
                if (current instanceof JqObject obj) {
                    JqValue next = obj.get(s.value());
                    current = next != null ? next : JqValue.NULL;
                } else {
                    current = JqValue.NULL;
                }
            } else if (key instanceof JqNumber num) {
                if (current instanceof JqArray arr) {
                    int idx = num.intValue();
                    if (idx < 0)
                        idx += arr.size();
                    current = idx >= 0 && idx < arr.size() ? arr.get(idx) : JqValue.NULL;
                } else {
                    current = JqValue.NULL;
                }
            } else {
                current = JqValue.NULL;
            }
        }
        return current;
    }

    /** Functional update: set the value at a concrete path, returning a new structure. */
    public static JqValue setAt(JqValue value, List<?> path, JqValue newValue) {
        if (path.isEmpty())
            return newValue;
        Object keyObj = path.get(0);
        List<?> rest = path.subList(1, path.size());
        if (keyObj instanceof SlicePath slice) {
            return setSlice(value, slice, newValue);
        }
        if (!(keyObj instanceof JqValue))
            throw new JqRuntimeException("Invalid path component");
        JqValue key = (JqValue) keyObj;
        if (key instanceof JqString s) {
            if (value instanceof JqNull) {
                Map<String, JqValue> props = new LinkedHashMap<>();
                props.put(s.value(), setAt(JqValue.NULL, rest, newValue));
                return new JqObject(props);
            }
            if (value instanceof JqObject obj) {
                JqValue child = obj.get(s.value());
                JqValue newChild = setAt(child != null ? child : JqValue.NULL, rest, newValue);
                return obj.put(s.value(), newChild);
            }
            throw new JqRuntimeException("Cannot index " + value.typeName()
                    + " with string \"" + s.value() + "\"");
        }
        if (key instanceof JqNumber num) {
            if (Double.isNaN(num.doubleValue())) {
                throw new JqRuntimeException("Cannot set array element at NaN index");
            }
            int idx = num.intValue();
            if (value instanceof JqNull) {
                if (idx < 0)
                    throw new JqRuntimeException("Out of bounds negative array index");
                List<JqValue> items = new ArrayList<>();
                for (int i = 0; i < idx; i++)
                    items.add(JqValue.NULL);
                items.add(setAt(JqValue.NULL, rest, newValue));
                return new JqArray(items);
            }
            if (value instanceof JqArray arr) {
                if (idx < 0)
                    idx += arr.size();
                if (idx < 0)
                    throw new JqRuntimeException("Out of bounds negative array index");
                List<JqValue> items = new ArrayList<>(arr.items());
                while (items.size() < idx)
                    items.add(JqValue.NULL);
                if (idx == items.size()) {
                    items.add(setAt(JqValue.NULL, rest, newValue));
                    return new JqArray(items);
                }
                JqValue child = items.get(idx);
                items.set(idx, setAt(child, rest, newValue));
                return new JqArray(items);
            }
            throw new JqRuntimeException("Cannot index " + value.typeName()
                    + " with number");
        }
        throw new JqRuntimeException("Invalid path component: " + JqPrinter.print(key));
    }

    /** Functional update: delete the value at a concrete path. */
    public static JqValue deleteAt(JqValue value, List<?> path, int depth) {
        if (path.isEmpty())
            return JqValue.NULL;
        Object keyObj = path.get(depth);
        if (keyObj instanceof SlicePath slice) {
            return setSlice(value, slice, JqValue.NULL);
        }
        if (!(keyObj instanceof JqValue))
            return value;
        JqValue key = (JqValue) keyObj;
        if (key instanceof JqString s && value instanceof JqObject obj) {
            if (depth == path.size() - 1)
                return obj.remove(s.value());
            JqValue child = obj.get(s.value());
            if (child != null)
                return obj.put(s.value(), deleteAt(child, path, depth + 1));
            return obj;
        }
        if (key instanceof JqNumber num && value instanceof JqArray arr) {
            int idx = num.intValue();
            if (idx < 0)
                idx += arr.size();
            if (idx < 0 || idx >= arr.size())
                return arr;
            if (depth == path.size() - 1)
                return arr.remove(idx);
            return arr.set(idx, deleteAt(arr.get(idx), path, depth + 1));
        }
        return value;
    }

    /** Splice a slice segment: assigning an array replaces the range, null deletes it. */
    private static JqValue setSlice(JqValue value, SlicePath slice, JqValue newValue) {
        if (value instanceof JqArray arr) {
            List<JqValue> items = new ArrayList<>(arr.items());
            int from = clamp(slice.from(), 0, items.size());
            int to = clamp(slice.to(), 0, items.size());
            List<JqValue> replacement;
            if (newValue instanceof JqNull) {
                replacement = new ArrayList<>();
            } else if (newValue instanceof JqArray inserted) {
                replacement = new ArrayList<>(inserted.items());
            } else {
                throw new JqRuntimeException("A slice of an array can only be assigned another array");
            }
            items.subList(from, to).clear();
            items.addAll(from, replacement);
            return new JqArray(items);
        }
        if (value instanceof JqNull) {
            return JqValue.NULL;
        }
        throw new JqRuntimeException("Cannot update " + value.typeName() + " slices");
    }

    /** Compare mixed path lists for stable delete ordering. */
    public static int compareObjectPaths(List<?> a, List<?> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int cmp;
            Object ea = a.get(i);
            Object eb = b.get(i);
            if (ea instanceof SlicePath sa && eb instanceof SlicePath sb) {
                cmp = Integer.compare(sa.from(), sb.from());
                if (cmp == 0)
                    cmp = Integer.compare(sa.to(), sb.to());
            } else if (ea instanceof JqValue va && eb instanceof JqValue vb) {
                cmp = JqOrdering.compare(va, vb);
            } else {
                cmp = Boolean.compare(ea instanceof SlicePath, eb instanceof SlicePath);
            }
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(b.size(), a.size());
    }
}
