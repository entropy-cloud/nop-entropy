package io.nop.jq.jq.runtime;

import io.nop.jq.jq.ast.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core execution engine for jq AST nodes.
 *
 * <p>Execution model: every expression maps an input to a list of outputs.
 * Control flow that jq realizes with backtracking is approximated here with
 * exceptions: label/break unwinds via {@link JqBreakException}, and lazy
 * consumers (limit/first/any/all/isempty) stop producers early via
 * {@link JqShortCircuitList} + {@link JqStopException}.
 */
public class JqExecutor {

    private final JqDestructurer destructurer = new JqDestructurer(
            (expr, input, env) -> execute(expr, input, env));
    private final JqPathEval pathEval = new JqPathEval(this::executeInto);

    /** Debug/trace sink for the debug builtin; tests may replace it. */
    private java.util.function.Consumer<String> debugSink = System.err::println;

    public void setDebugSink(java.util.function.Consumer<String> sink) {
        this.debugSink = sink;
    }

    // ===== entry points =====

    public List<JqValue> execute(JqAstNode node, JqValue input, JqEnvironment env) {
        List<JqValue> outputs = new ArrayList<>();
        executeInto(node, input, env, outputs);
        return outputs;
    }

    public void executeInto(JqAstNode node, JqValue input, JqEnvironment env, List<JqValue> outputs) {
        if (outputs instanceof JqShortCircuitList sink && sink.stopRequested()) {
            // a lazy consumer already has enough outputs: stop before evaluating
            throw JqStopException.INSTANCE;
        }
        dispatchLocal.get().run(node, input, env, outputs);
    }

    /**
     * One dispatch object per thread: node.accept is a double dispatch that
     * would otherwise allocate a fresh visitor per executeInto call. The
     * current input/env/outputs live in fields; run() saves and restores them
     * so nested executeInto calls re-entering the same object stay correct.
     */
    private final ThreadLocal<Dispatch> dispatchLocal = ThreadLocal.withInitial(Dispatch::new);

    private final class Dispatch implements JqAstVisitor<JqValue> {
        private JqValue input;
        private JqEnvironment env;
        private List<JqValue> outputs;

        void run(JqAstNode node, JqValue input, JqEnvironment env, List<JqValue> outputs) {
            JqValue prevInput = this.input;
            JqEnvironment prevEnv = this.env;
            List<JqValue> prevOutputs = this.outputs;
            this.input = input;
            this.env = env;
            this.outputs = outputs;
            try {
                node.accept(this);
            } finally {
                this.input = prevInput;
                this.env = prevEnv;
                this.outputs = prevOutputs;
            }
        }

        @Override public JqValue visitNull(NullLiteralNode n) {
            outputs.add(JqValue.NULL);
            return JqValue.NULL;
        }

            @Override public JqValue visitBoolean(BooleanLiteralNode n) {
                outputs.add(JqBoolean.of(n.value()));
                return JqValue.NULL;
            }

            @Override public JqValue visitNumber(NumberLiteralNode n) {
                outputs.add(JqNumber.of(n.value()));
                return JqValue.NULL;
            }

            @Override public JqValue visitString(StringLiteralNode n) {
                outputs.add(JqString.of(n.value()));
                return JqValue.NULL;
            }

            @Override public JqValue visitIdentity(IdentityNode n) {
                outputs.add(input);
                return JqValue.NULL;
            }

            @Override public JqValue visitFieldAccess(FieldAccessNode n) {
                if (n.hasExplicitObject()) {
                    for (JqValue obj : execute(n.object(), input, env)) {
                        outputs.add(getField(obj, n.fieldName()));
                    }
                } else {
                    outputs.add(getField(input, n.fieldName()));
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitIndexAccess(IndexAccessNode n) {
                List<JqValue> keys = execute(n.index(), input, env);
                List<JqValue> objects = n.hasExplicitObject()
                        ? execute(n.object(), input, env)
                        : List.of(input);
                for (JqValue obj : objects) {
                    for (JqValue key : keys) {
                        JqValue val = getIndex(obj, key);
                        if (val != null)
                            outputs.add(val);
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitSlice(SliceNode n) {
                List<JqValue> objects = n.hasExplicitObject()
                        ? execute(n.object(), input, env)
                        : List.of(input);
                for (JqValue obj : objects) {
                    List<JqValue> startVals = n.start() != null
                            ? execute(n.start(), input, env) : List.of();
                    List<JqValue> endVals = n.end() != null
                            ? execute(n.end(), input, env) : List.of();
                    outputs.add(getSlice(obj, startVals, endVals));
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitIterator(IteratorNode n) {
                List<JqValue> objects = n.hasExplicitObject()
                        ? execute(n.object(), input, env)
                        : List.of(input);
                for (JqValue obj : objects) {
                    if (obj instanceof JqArray arr) {
                        outputs.addAll(arr.items());
                    } else if (obj instanceof JqObject object) {
                        outputs.addAll(object.properties().values());
                    } else if (obj instanceof JqNull) {
                        // iterating null produces nothing
                    } else {
                        throw new JqRuntimeException("Cannot iterate over "
                                + obj.typeName() + " (" + JqPrinter.print(obj) + ")");
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitRecursiveDescent(RecursiveDescentNode n) {
                collectRecursive(input, n.fieldName(), outputs, env, 0);
                return JqValue.NULL;
            }

            @Override public JqValue visitPipe(PipeNode n) {
                if (n.left() instanceof FuncDefNode def) {
                    // lexical scoping: the definition lives in a child environment,
                    // resolves by name/arity, and captures that environment as its
                    // definition site
                    JqEnvironment childEnv = env.fork();
                    childEnv.defineFunction(functionKey(def.name(), def.params().size()),
                            new JqFunctionDef(def, childEnv));
                    executeInto(n.right(), input, childEnv, outputs);
                    return JqValue.NULL;
                }
                for (JqValue leftOut : execute(n.left(), input, env)) {
                    executeInto(n.right(), leftOut, env, outputs);
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitComma(CommaNode n) {
                executeInto(n.left(), input, env, outputs);
                executeInto(n.right(), input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitMathOp(MathOpNode n) {
                List<JqValue> leftVals = execute(n.left(), input, env);
                List<JqValue> rightVals = execute(n.right(), input, env);
                for (JqValue left : leftVals) {
                    for (JqValue right : rightVals) {
                        outputs.add(evalMathOp(n.op(), left, right));
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitComparison(ComparisonNode n) {
                List<JqValue> leftVals = execute(n.left(), input, env);
                List<JqValue> rightVals = execute(n.right(), input, env);
                for (JqValue left : leftVals) {
                    for (JqValue right : rightVals) {
                        outputs.add(JqBoolean.of(evalComparison(n.op(), left, right)));
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitBooleanOp(BooleanOpNode n) {
                if (n.op() == BooleanOpNode.Op.NOT) {
                    for (JqValue v : execute(n.right(), input, env)) {
                        outputs.add(JqBoolean.of(!JqTruthiness.of(v)));
                    }
                    return JqValue.NULL;
                }
                for (JqValue left : execute(n.left(), input, env)) {
                    boolean leftBool = JqTruthiness.of(left);
                    if (n.op() == BooleanOpNode.Op.AND && !leftBool) {
                        outputs.add(JqBoolean.FALSE);
                    } else if (n.op() == BooleanOpNode.Op.OR && leftBool) {
                        outputs.add(JqBoolean.TRUE);
                    } else {
                        for (JqValue right : execute(n.right(), input, env)) {
                            outputs.add(JqBoolean.of(JqTruthiness.of(right)));
                        }
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitNegate(NegateNode n) {
                for (JqValue v : execute(n.operand(), input, env)) {
                    if (!(v instanceof JqNumber num)) {
                        throw new JqRuntimeException(v.typeName() + " ("
                                + JqPrinter.printTruncated(v) + ") cannot be negated");
                    }
                    outputs.add(JqNumber.of(-num.doubleValue()));
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitIfThenElse(IfThenElseNode n) {
                for (JqValue cond : execute(n.condition(), input, env)) {
                    if (JqTruthiness.of(cond)) {
                        executeInto(n.thenBranch(), input, env, outputs);
                        continue;
                    }
                    boolean handled = false;
                    for (IfThenElseNode.ElifClause elif : n.elifClauses()) {
                        boolean branchTaken = false;
                        for (JqValue elifCond : execute(elif.condition(), input, env)) {
                            if (JqTruthiness.of(elifCond)) {
                                executeInto(elif.branch(), input, env, outputs);
                                branchTaken = true;
                                break;
                            }
                        }
                        if (branchTaken) {
                            handled = true;
                            break;
                        }
                    }
                    if (!handled) {
                        if (n.elseBranch() != null)
                            executeInto(n.elseBranch(), input, env, outputs);
                        else
                            outputs.add(input);
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitTryCatch(TryCatchNode n) {
                try {
                    // outputs produced before the error remain in the stream
                    executeInto(n.tryExpr(), input, env, outputs);
                } catch (JqBreakException | JqStopException e) {
                    throw e;
                } catch (JqRuntimeException e) {
                    if (n.hasCatch()) {
                        executeInto(n.catchExpr(), e.errorValue(), env, outputs);
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitLabel(LabelNode n) {
                try {
                    executeInto(n.body(), input, env, outputs);
                } catch (JqBreakException e) {
                    if (!e.labelName().equals(n.name())) {
                        throw e;
                    }
                    // break unwinds here: outputs produced so far are kept
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitBreak(BreakNode n) {
                throw new JqBreakException(n.labelName(), input);
            }

            @Override public JqValue visitBind(BindNode n) {
                for (JqValue val : execute(n.expr(), input, env)) {
                    Map<String, JqValue> bindings =
                            destructurer.destructureTop(n.pattern(), val, env);
                    if (bindings == null)
                        continue;
                    env.pushScope();
                    try {
                        for (Map.Entry<String, JqValue> b : bindings.entrySet()) {
                            env.bind(b.getKey(), b.getValue());
                        }
                        executeInto(n.body(), input, env, outputs);
                    } finally {
                        env.popScope();
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitVariable(VariableNode n) {
                String name = n.name();
                if (name.equals("$ENV")) {
                    outputs.add(environmentObject());
                    return JqValue.NULL;
                }
                if (name.equals("$__loc__")) {
                    Map<String, JqValue> loc = new LinkedHashMap<>();
                    loc.put("file", JqString.of("<top-level>"));
                    loc.put("line", JqNumber.of(1));
                    outputs.add(new JqObject(loc));
                    return JqValue.NULL;
                }
                if (env.hasFunction(functionKey(name, 0))) {
                    callFunction(functionKey(name, 0), List.of(), input, env, outputs);
                    return JqValue.NULL;
                }
                outputs.add(env.lookup(name));
                return JqValue.NULL;
            }

            @Override public JqValue visitObjectConstruct(ObjectConstructNode n) {
                constructObjects(n.fields(), 0, new LinkedHashMap<>(), input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitArrayConstruct(ArrayConstructNode n) {
                env.checkOutputLimit();
                outputs.add(new JqArray(execute(n.element(), input, env)));
                return JqValue.NULL;
            }

            @Override public JqValue visitStringInterp(StringInterpNode n) {
                interpParts(n.parts(), 0, new StringBuilder(), input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitSelect(SelectNode n) {
                // parser now produces select as a function call; kept for hand-built ASTs
                executeInto(new FuncCallNode("select",
                        List.of(n.condition())), input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitMap(MapNode n) {
                executeInto(new FuncCallNode("map",
                        List.of(n.function())), input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitReduce(ReduceNode n) {
                JqValue acc = first(execute(n.init(), input, env));
                for (JqValue item : execute(n.expr(), input, env)) {
                    env.pushScope();
                    try {
                        bindPattern(n.pattern(), item, env);
                        List<JqValue> bodyVals = execute(n.body(), acc, env);
                        acc = bodyVals.isEmpty() ? JqValue.NULL
                                : bodyVals.get(bodyVals.size() - 1);
                    } finally {
                        env.popScope();
                    }
                }
                outputs.add(acc);
                return JqValue.NULL;
            }

            @Override public JqValue visitForEach(ForEachNode n) {
                JqValue state = first(execute(n.init(), input, env));
                for (JqValue item : execute(n.expr(), input, env)) {
                    env.pushScope();
                    try {
                        bindPattern(n.pattern(), item, env);
                        List<JqValue> updateVals = execute(n.update(), state, env);
                        state = updateVals.isEmpty() ? JqValue.NULL
                                : updateVals.get(updateVals.size() - 1);
                        if (n.extract() != null) {
                            outputs.addAll(execute(n.extract(), state, env));
                        } else {
                            env.checkOutputLimit();
                            outputs.add(state);
                        }
                    } finally {
                        env.popScope();
                    }
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitLimit(LimitNode n) {
                executeInto(new FuncCallNode("limit", List.of(n.count(), n.expr())),
                        input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitEmpty(EmptyNode n) {
                // empty produces no output
                return JqValue.NULL;
            }

            @Override public JqValue visitDebug(DebugNode n) {
                outputs.add(input);
                return JqValue.NULL;
            }

            @Override public JqValue visitError(ErrorNode n) {
                if (n.hasMessage()) {
                    List<JqValue> msgVals = execute(n.message(), input, env);
                    JqValue errorValue = msgVals.isEmpty() ? JqValue.NULL : msgVals.get(0);
                    throw errorFor(errorValue);
                }
                throw errorFor(input);
            }

            @Override public JqValue visitFuncCall(FuncCallNode n) {
                callBuiltinOrUser(n, input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitFuncDef(FuncDefNode n) {
                env.defineFunction(n.name(), n);
                return JqValue.NULL;
            }

            @Override public JqValue visitFormat(FormatNode n) {
                executeFormat(n, input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitAlternative(AlternativeNode n) {
                List<JqValue> leftVals = new ArrayList<>();
                try {
                    executeInto(n.left(), input, env, leftVals);
                } catch (JqBreakException | JqStopException e) {
                    throw e;
                } catch (JqRuntimeException e) {
                    // errors in the left side fall through to the right side
                }
                boolean any = false;
                for (JqValue v : leftVals) {
                    if (JqTruthiness.of(v)) {
                        outputs.add(v);
                        any = true;
                    }
                }
                if (!any) {
                    executeInto(n.right(), input, env, outputs);
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitUpdateAssign(UpdateAssignNode n) {
                executeUpdateAssign(n, input, env, outputs);
                return JqValue.NULL;
            }

            @Override public JqValue visitWhile(WhileNode n) {
                JqValue state = input;
                while (JqTruthiness.of(first(execute(n.condition(), state, env)))) {
                    env.checkOutputLimit();
                    outputs.add(state);
                    state = first(execute(n.update(), state, env));
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitUntil(UntilNode n) {
                JqValue state = input;
                int guard = 0;
                while (!JqTruthiness.of(first(execute(n.condition(), state, env)))) {
                    if (++guard > JqEnvironment.MAX_CALL_DEPTH) {
                        throw new JqRuntimeException("until did not converge");
                    }
                    state = first(execute(n.update(), state, env));
                }
                outputs.add(state);
                return JqValue.NULL;
            }

            @Override public JqValue visitInput(InputNode n) {
                if (n.all()) {
                    while (env.hasMoreInputs()) {
                        outputs.add(env.nextInput());
                    }
                } else {
                    outputs.add(env.nextInput());
                }
                return JqValue.NULL;
            }

            @Override public JqValue visitEnv(EnvNode n) {
                outputs.add(environmentObject());
                return JqValue.NULL;
            }
    }

    // ===== helpers: value access =====

    private JqValue first(List<JqValue> vals) {
        return vals.isEmpty() ? JqValue.NULL : vals.get(0);
    }

    private JqValue getField(JqValue obj, String name) {
        if (obj instanceof JqObject map) {
            JqValue val = map.get(name);
            return val != null ? val : JqValue.NULL;
        }
        if (obj instanceof JqNull)
            return JqValue.NULL;
        throw new JqRuntimeException("Cannot index " + obj.typeName()
                + " with string \"" + name + "\"");
    }

    private JqValue getIndex(JqValue obj, JqValue index) {
        if (index instanceof JqString s) {
            if (obj instanceof JqObject map) {
                JqValue val = map.get(s.value());
                return val != null ? val : JqValue.NULL;
            }
            if (obj instanceof JqNull)
                return JqValue.NULL;
            throw new JqRuntimeException("Cannot index " + obj.typeName()
                    + " with string \"" + s.value() + "\"");
        }
        if (index instanceof JqNumber num) {
            if (obj instanceof JqArray arr) {
                if (Double.isNaN(num.doubleValue()))
                    return JqValue.NULL;
                int idx = num.intValue();
                if (idx < 0)
                    idx += arr.size();
                return idx >= 0 && idx < arr.size() ? arr.get(idx) : JqValue.NULL;
            }
            if (obj instanceof JqNull)
                return JqValue.NULL;
            throw new JqRuntimeException("Cannot index " + obj.typeName()
                    + " with number");
        }
        if (index instanceof JqNull && obj instanceof JqNull)
            return JqValue.NULL;
        throw new JqRuntimeException("Cannot index " + obj.typeName()
                + " with " + index.typeName());
    }

    private JqValue getSlice(JqValue obj, List<JqValue> startVals, List<JqValue> endVals) {
        int start = 0;
        int end = Integer.MAX_VALUE;
        if (!startVals.isEmpty() && startVals.get(0) instanceof JqNumber n)
            start = Double.isNaN(n.doubleValue()) ? 0 : (int) Math.floor(n.doubleValue());
        if (!endVals.isEmpty() && endVals.get(0) instanceof JqNumber n)
            end = Double.isNaN(n.doubleValue()) ? Integer.MAX_VALUE
                    : (int) Math.ceil(n.doubleValue());
        if (obj instanceof JqArray arr) {
            int size = arr.size();
            int from = start < 0 ? Math.max(0, size + start) : Math.min(start, size);
            int to = end < 0 ? Math.max(0, size + end) : Math.min(end, size);
            return new JqArray(new ArrayList<>(arr.items().subList(from, Math.max(from, to))));
        }
        if (obj instanceof JqString s) {
            int size = s.value().length();
            int from = start < 0 ? Math.max(0, size + start) : Math.min(start, size);
            int to = end < 0 ? Math.max(0, size + end) : Math.min(end, size);
            if (to < from)
                to = from;
            return JqString.of(s.value().substring(from, to));
        }
        if (obj instanceof JqNull)
            return JqValue.NULL;
        throw new JqRuntimeException("Cannot index " + obj.typeName() + " with object");
    }

    private void collectRecursive(JqValue obj, String fieldName, List<JqValue> results,
                                  JqEnvironment env, int depth) {
        if (fieldName == null) {
            env.checkOutputLimit();
            results.add(obj);
        }
        if (obj instanceof JqObject map) {
            for (Map.Entry<String, JqValue> entry : map.properties().entrySet()) {
                if (fieldName != null && entry.getKey().equals(fieldName)) {
                    env.checkOutputLimit();
                    results.add(entry.getValue());
                }
                collectRecursive(entry.getValue(), fieldName, results, env, depth + 1);
            }
        } else if (obj instanceof JqArray arr) {
            for (JqValue item : arr.items()) {
                collectRecursive(item, fieldName, results, env, depth + 1);
            }
        }
    }

    // ===== object construction (cartesian over keys and values) =====

    private void constructObjects(List<ObjectConstructNode.Field> fields, int index,
                                  Map<String, JqValue> current, JqValue input,
                                  JqEnvironment env, List<JqValue> outputs) {
        if (index == fields.size()) {
            outputs.add(JqObject.ofFresh(current));
            return;
        }
        ObjectConstructNode.Field field = fields.get(index);
        for (JqValue keyVal : execute(field.key(), input, env)) {
            if (!(keyVal instanceof JqString keyStr)) {
                throw new JqRuntimeException("Cannot use " + keyVal.typeName()
                        + " (" + JqPrinter.print(keyVal) + ") as object key");
            }
            for (JqValue value : execute(field.value(), input, env)) {
                Map<String, JqValue> next = new LinkedHashMap<>(current);
                next.put(keyStr.value(), value);
                constructObjects(fields, index + 1, next, input, env, outputs);
            }
        }
    }

    // ===== string interpolation (cartesian over parts) =====

    private void interpParts(List<Object> parts, int index, StringBuilder current,
                             JqValue input, JqEnvironment env, List<JqValue> outputs) {
        if (index == parts.size()) {
            outputs.add(JqString.of(current.toString()));
            return;
        }
        Object part = parts.get(index);
        if (part instanceof String s) {
            current.append(s);
            interpParts(parts, index + 1, current, input, env, outputs);
            current.setLength(current.length() - s.length());
            return;
        }
        if (part instanceof JqAstNode node) {
            for (JqValue v : execute(node, input, env)) {
                String text = JqPrinter.tostring(v);
                current.append(text);
                interpParts(parts, index + 1, current, input, env, outputs);
                current.setLength(current.length() - text.length());
            }
        }
    }

    // ===== destructuring helper for reduce/foreach =====

    private void bindPattern(BindPattern pattern, JqValue value, JqEnvironment env) {
        Map<String, JqValue> bindings = destructurer.destructureTop(pattern, value, env);
        if (bindings == null)
            return;
        for (Map.Entry<String, JqValue> b : bindings.entrySet()) {
            env.bind(b.getKey(), b.getValue());
        }
    }

    // ===== assignment =====

    private void executeUpdateAssign(UpdateAssignNode n, JqValue input,
                                     JqEnvironment env, List<JqValue> outputs) {
        List<List<Object>> paths = pathEval.evalPaths(n.path(), input, env);
        switch (n.op()) {
            case ASSIGN -> {
                for (JqValue value : execute(n.value(), input, env)) {
                    JqValue result = input;
                    for (List<Object> path : paths) {
                        result = JqPathEval.setAt(result, path, value);
                    }
                    outputs.add(result);
                }
            }
            case UPDATE -> {
                // jq's _modify: getpath reads the original, updates apply to the
                // result, deletions are applied last so indices do not shift
                JqValue result = input;
                List<List<Object>> deletions = new ArrayList<>();
                for (List<Object> path : paths) {
                    JqValue current = JqPathEval.getAt(input, path);
                    List<JqValue> newVals = execute(n.value(), current, env);
                    if (newVals.isEmpty()) {
                        deletions.add(path);
                    } else {
                        result = JqPathEval.setAt(result, path, newVals.get(0));
                    }
                }
                deletions.sort(JqPathEval::compareObjectPaths);
                for (int i = deletions.size() - 1; i >= 0; i--) {
                    result = JqPathEval.deleteAt(result, deletions.get(i), 0);
                }
                outputs.add(result);
            }
            case ALTERNATIVE -> {
                JqValue result = input;
                for (List<Object> path : paths) {
                    JqValue current = JqPathEval.getAt(result, path);
                    if (JqTruthiness.of(current))
                        continue;
                    for (JqValue value : execute(n.value(), input, env)) {
                        result = JqPathEval.setAt(result, path, value);
                        break;
                    }
                }
                outputs.add(result);
            }
            default -> {
                for (JqValue arg : execute(n.value(), input, env)) {
                    JqValue result = input;
                    for (List<Object> path : paths) {
                        JqValue current = JqPathEval.getAt(result, path);
                        JqValue updated = arith(n.op(), current, arg);
                        result = JqPathEval.setAt(result, path, updated);
                    }
                    outputs.add(result);
                }
            }
        }
    }

    private JqValue arith(UpdateAssignNode.Op op, JqValue current, JqValue arg) {
        return switch (op) {
            case ADD -> evalMathOp(MathOpNode.Op.ADD, current, arg);
            case SUB -> evalMathOp(MathOpNode.Op.SUB, current, arg);
            case MUL -> evalMathOp(MathOpNode.Op.MUL, current, arg);
            case DIV -> evalMathOp(MathOpNode.Op.DIV, current, arg);
            case MOD -> evalMathOp(MathOpNode.Op.MOD, current, arg);
            default -> throw new IllegalStateException("not an arithmetic op: " + op);
        };
    }

    // ===== builtins bridge =====

    void callBuiltinOrUser(FuncCallNode n, JqValue input, JqEnvironment env, List<JqValue> outputs) {
        JqBuiltins.call(this, n, input, env, outputs);
    }

    /** Called by JqBuiltins when a user-defined function shadows a plain builtin. */
    public void callFunctionFromBuiltin(String name, List<JqAstNode> args, JqValue input,
                                        JqEnvironment env, List<JqValue> outputs) {
        callFunction(name, args, input, env, outputs);
    }

    JqValue evalMathOpPublic(MathOpNode.Op op, JqValue left, JqValue right) {
        return evalMathOp(op, left, right);
    }

    void debug(JqValue v) {
        debugSink.accept(JqPrinter.print(v));
    }

    JqValue environmentObject() {
        Map<String, JqValue> props = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            props.put(e.getKey(), JqString.of(e.getValue()));
        }
        return new JqObject(props);
    }

    // ===== user function invocation =====

    private void callFunction(String name, List<JqAstNode> args, JqValue input,
                              JqEnvironment env, List<JqValue> outputs) {
        Object def = env.getFunction(name);
        if (def instanceof JqFunctionDef fnDef) {
            callUserFunction(fnDef, args, input, env, outputs);
            return;
        }
        if (def instanceof JqClosureFn closure) {
            closure.executeInto(input, outputs);
            return;
        }
        if (def instanceof JqFunction fn) {
            List<JqValue> argVals = new ArrayList<>();
            for (JqAstNode arg : args) {
                argVals.addAll(execute(arg, input, env));
            }
            outputs.add(fn.apply(argVals, input));
            return;
        }
        if (def instanceof FuncDefNode fd) {
            callUserFunction(new JqFunctionDef(fd, env), args, input, env, outputs);
            return;
        }
        throw new JqRuntimeException(name + "/" + args.size() + " is not defined");
    }

    /** User functions resolve by name/arity, like jq. */
    static String functionKey(String name, int arity) {
        return name + "/" + arity;
    }

    private void callUserFunction(JqFunctionDef fnDef, List<JqAstNode> args, JqValue input,
                                  JqEnvironment env, List<JqValue> outputs) {
        FuncDefNode fd = fnDef.def();
        if (fd.params().size() > args.size()) {
            throw new JqRuntimeException(fd.name() + " expects " + fd.params().size()
                    + " arguments but only " + args.size() + " given");
        }
        // the body resolves functions from the definition site (lexical scope)
        JqEnvironment fnEnv = fnDef.definitionEnv().fork();
        List<List<JqValue>> valueParamVals = new ArrayList<>();
        List<String> valueParams = new ArrayList<>();
        for (int i = 0; i < fd.params().size(); i++) {
            String param = fd.params().get(i);
            JqAstNode argNode = args.get(i);
            if (param.startsWith("$")) {
                // value parameter: evaluated at the call site, cartesian over outputs
                valueParams.add(param);
                valueParamVals.add(execute(argNode, input, env));
            } else {
                // filter parameter: a closure over the call site
                fnEnv.defineFunction(JqExecutor.functionKey(param, 0),
                        new JqClosureFn(env, argNode, this));
            }
        }
        env.enterCall();
        try {
            bindValueParamsCartesian(fd, valueParams, valueParamVals, input, fnEnv, outputs, 0);
        } finally {
            env.exitCall();
        }
    }



    /** Value parameters bind every output, producing the cartesian product of calls. */
    private void bindValueParamsCartesian(FuncDefNode fd, List<String> params,
                                          List<List<JqValue>> vals, JqValue input,
                                          JqEnvironment fnEnv, List<JqValue> outputs,
                                          int index) {
        if (index == params.size()) {
            executeInto(fd.body(), input, fnEnv, outputs);
            return;
        }
        for (JqValue v : vals.get(index)) {
            fnEnv.pushScope();
            try {
                fnEnv.bind(params.get(index), v);
                bindValueParamsCartesian(fd, params, vals, input, fnEnv, outputs, index + 1);
            } finally {
                fnEnv.popScope();
            }
        }
    }

    // ===== format strings =====

    private void executeFormat(FormatNode n, JqValue input, JqEnvironment env,
                               List<JqValue> outputs) {
        String format = n.format();
        JqAstNode object = n.object();
        if (object == null) {
            outputs.add(JqString.of(formatValue(format, input)));
            return;
        }
        // @format "..." applies the format to interpolated values only
        if (object instanceof StringInterpNode interp) {
            StringBuilder text = new StringBuilder();
            for (Object part : interp.parts()) {
                if (part instanceof String s) {
                    text.append(s);
                } else if (part instanceof JqAstNode node) {
                    for (JqValue v : execute(node, input, env)) {
                        text.append(formatValue(format, v));
                    }
                }
            }
            outputs.add(JqString.of(text.toString()));
            return;
        }
        for (JqValue v : execute(object, input, env)) {
            outputs.add(JqString.of(formatValue(format, v)));
        }
    }

    private String formatValue(String format, JqValue v) {
        return switch (format) {
            case "text" -> JqPrinter.tostring(v);
            case "json" -> JqPrinter.print(v);
            case "csv" -> v instanceof JqArray arr
                    ? JqFormatStrings.csv(arr)
                    : JqFormatStrings.apply(format, v);
            case "tsv" -> v instanceof JqArray arr
                    ? JqFormatStrings.tsv(arr)
                    : JqFormatStrings.apply(format, v);
            case "sh" -> JqFormatStrings.shEncode(v);
            default -> JqFormatStrings.apply(format, v);
        };
    }

    private JqRuntimeException errorFor(JqValue errorValue) {
        if (errorValue instanceof JqString s) {
            return new JqRuntimeException(s.value(), s);
        }
        return new JqRuntimeException(JqPrinter.print(errorValue), errorValue);
    }

    // ===== arithmetic =====

    private JqValue evalMathOp(MathOpNode.Op op, JqValue left, JqValue right) {
        if (left instanceof JqNumber ln && right instanceof JqNumber rn) {
            return numbers(op, ln, rn);
        }
        switch (op) {
            case ADD -> {
                if (left instanceof JqNull)
                    return right;
                if (right instanceof JqNull)
                    return left;
                if (left instanceof JqString ls && right instanceof JqString rs)
                    return JqString.of(ls.value() + rs.value());
                if (left instanceof JqArray la && right instanceof JqArray ra) {
                    List<JqValue> merged = new ArrayList<>(la.items());
                    merged.addAll(ra.items());
                    return new JqArray(merged);
                }
                if (left instanceof JqObject lo && right instanceof JqObject ro) {
                    Map<String, JqValue> merged = new LinkedHashMap<>(lo.properties());
                    merged.putAll(ro.properties());
                    return new JqObject(merged);
                }
            }
            case SUB -> {
                if (left instanceof JqArray la && right instanceof JqArray ra) {
                    List<JqValue> result = new ArrayList<>();
                    for (JqValue item : la.items()) {
                        boolean found = false;
                        for (JqValue remove : ra.items()) {
                            if (JqOrdering.compare(item, remove) == 0) {
                                found = true;
                                break;
                            }
                        }
                        if (!found)
                            result.add(item);
                    }
                    return new JqArray(result);
                }
            }
            case MUL -> {
                JqValue repeated = repeatString(left, right);
                if (repeated == null)
                    repeated = repeatString(right, left);
                if (repeated != null)
                    return repeated;
                if (left instanceof JqObject lo && right instanceof JqObject ro)
                    return deepMerge(lo, ro);
            }
            case DIV -> {
                if (left instanceof JqString ls && right instanceof JqString rs) {
                    // string split by separator
                    String sep = rs.value();
                    String value = ls.value();
                    if (sep.isEmpty()) {
                        List<JqValue> chars = new ArrayList<>(value.length());
                        for (int i = 0; i < value.length(); i++)
                            chars.add(JqString.of(String.valueOf(value.charAt(i))));
                        return new JqArray(chars);
                    }
                    List<JqValue> parts = new ArrayList<>();
                    int from = 0;
                    int at;
                    while ((at = value.indexOf(sep, from)) >= 0) {
                        parts.add(JqString.of(value.substring(from, at)));
                        from = at + sep.length();
                    }
                    parts.add(JqString.of(value.substring(from)));
                    return new JqArray(parts);
                }
            }
            default -> {
            }
        }
        throw new JqRuntimeException(left.typeName() + " (" + JqPrinter.print(left)
                + ") and " + right.typeName() + " (" + JqPrinter.print(right)
                + ") cannot be " + opName(op));
    }

    private String opName(MathOpNode.Op op) {
        return switch (op) {
            case ADD -> "added";
            case SUB -> "subtracted";
            case MUL -> "multiplied";
            case DIV -> "divided";
            case MOD -> "divided (remainder)";
        };
    }

    /**
     * jq string repetition: s * n repeats s floor(n) times; a negative or NaN
     * count yields null. Returns null when the operands are not (string, number).
     */
    private JqValue repeatString(JqValue a, JqValue b) {
        if (a instanceof JqString s && b instanceof JqNumber n) {
            double count = n.doubleValue();
            if (Double.isNaN(count) || count < 0)
                return JqValue.NULL;
            long reps = (long) Math.floor(count);
            StringBuilder sb = new StringBuilder();
            for (long i = 0; i < reps; i++)
                sb.append(s.value());
            return JqString.of(sb.toString());
        }
        return null;
    }

    private JqValue deepMerge(JqObject lo, JqObject ro) {
        Map<String, JqValue> merged = new LinkedHashMap<>(lo.properties());
        for (Map.Entry<String, JqValue> e : ro.properties().entrySet()) {
            JqValue existing = merged.get(e.getKey());
            if (existing instanceof JqObject innerLeft && e.getValue() instanceof JqObject innerRight) {
                merged.put(e.getKey(), deepMerge(innerLeft, innerRight));
            } else {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return new JqObject(merged);
    }

    private JqValue numbers(MathOpNode.Op op, JqNumber ln, JqNumber rn) {
        double l = ln.doubleValue();
        double r = rn.doubleValue();
        switch (op) {
            case ADD:
                return JqNumber.of(l + r);
            case SUB:
                return JqNumber.of(l - r);
            case MUL:
                return JqNumber.of(l * r);
            case DIV:
                if (r == 0) {
                    throw new JqRuntimeException(dividendError(ln, rn, "divided"));
                }
                return JqNumber.of(l / r);
            case MOD: {
                if (Double.isNaN(l) || Double.isNaN(r))
                    return JqNumber.of(Double.NaN);
                long bi = (long) r; // saturating cast like C intmax_t
                if (bi == 0) {
                    throw new JqRuntimeException(dividendError(ln, rn, "divided (remainder)"));
                }
                long ai = (long) l;
                return JqNumber.of(bi == -1 ? 0L : ai % bi);
            }
            default:
                throw new IllegalStateException();
        }
    }

    private String dividendError(JqNumber ln, JqNumber rn, String verb) {
        return ln.typeName() + " (" + JqPrinter.print(ln) + ") and "
                + rn.typeName() + " (" + JqPrinter.print(rn) + ")"
                + " cannot be " + verb + " because the divisor is zero";
    }

    // ===== comparison =====

    private boolean evalComparison(ComparisonNode.Op op, JqValue left, JqValue right) {
        int cmp = JqOrdering.compare(left, right);
        return switch (op) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
        };
    }
}
