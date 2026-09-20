package io.nop.jq.jq.runtime;

import io.nop.jq.jq.ast.*;

import java.util.*;

/**
 * Core execution engine for jq AST nodes.
 * Implements the streaming output model: each expression produces zero or more outputs.
 */
public class JqExecutor {

    /**
     * Execute an AST node and return all outputs.
     */
    public List<JqValue> execute(JqAstNode node, JqValue input, JqEnvironment env) {
        List<JqValue> outputs = new ArrayList<>();
        executeInto(node, input, env, outputs);
        return outputs;
    }

    /**
     * Execute an AST node and collect outputs into the provided list.
     */
    public void executeInto(JqAstNode node, JqValue input, JqEnvironment env, List<JqValue> outputs) {
        node.accept(new JqAstVisitor<>() {
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
                    List<JqValue> objOutputs = execute(n.object(), input, env);
                    for (JqValue obj : objOutputs) {
                        JqValue val = getField(obj, n.fieldName());
                        outputs.add(val);
                    }
                } else {
                    JqValue val = getField(input, n.fieldName());
                    outputs.add(val);
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitIndexAccess(IndexAccessNode n) {
                if (n.hasExplicitObject()) {
                    List<JqValue> objOutputs = execute(n.object(), input, env);
                    for (JqValue obj : objOutputs) {
                        List<JqValue> idxOutputs = execute(n.index(), input, env);
                        for (JqValue idx : idxOutputs) {
                            JqValue val = getIndex(obj, idx);
                            if (val != null) outputs.add(val);
                        }
                    }
                } else {
                    List<JqValue> idxOutputs = execute(n.index(), input, env);
                    for (JqValue idx : idxOutputs) {
                        JqValue val = getIndex(input, idx);
                        if (val != null) outputs.add(val);
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitSlice(SliceNode n) {
                JqValue obj = input;
                if (n.hasExplicitObject()) {
                    List<JqValue> objOutputs = execute(n.object(), input, env);
                    if (objOutputs.isEmpty()) return JqValue.NULL;
                    obj = objOutputs.get(0);
                }
                int start = 0, end = Integer.MAX_VALUE;
                if (n.start() != null) {
                    List<JqValue> startVals = execute(n.start(), input, env);
                    if (!startVals.isEmpty()) start = floorToInt(startVals.get(0));
                }
                if (n.end() != null) {
                    List<JqValue> endVals = execute(n.end(), input, env);
                    if (!endVals.isEmpty()) end = ceilToInt(endVals.get(0));
                }
                if (obj instanceof JqArray arr) {
                    int size = arr.size();
                    int from = start < 0 ? Math.max(0, size + start) : Math.min(start, size);
                    int to = end == Integer.MAX_VALUE ? size : (end < 0 ? Math.max(0, size + end) : Math.min(end, size));
                    List<JqValue> sliced = arr.items().subList(from, to);
                    outputs.add(new JqArray(new ArrayList<>(sliced)));
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitIterator(IteratorNode n) {
                JqValue obj = input;
                if (n.hasExplicitObject()) {
                    List<JqValue> objOutputs = execute(n.object(), input, env);
                    if (objOutputs.isEmpty()) return JqValue.NULL;
                    obj = objOutputs.get(0);
                }
                if (obj instanceof JqArray arr) {
                    outputs.addAll(arr.items());
                } else if (obj instanceof JqObject obj2) {
                    outputs.addAll(obj2.properties().values());
                } else if (obj instanceof JqNull) {
                    // null iteration produces nothing
                } else {
                    throw new JqRuntimeException("Cannot iterate over " + obj.typeName() + " (" + obj + ")");
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitRecursiveDescent(RecursiveDescentNode n) {
                collectRecursive(input, n.fieldName(), outputs, env, 0);
                return JqValue.NULL;
            }
            @Override public JqValue visitPipe(PipeNode n) {
                if (n.left() instanceof LabelNode label) {
                    List<JqValue> leftOutputs = execute(n.left(), input, env);
                    for (JqValue leftOut : leftOutputs) {
                        int before = outputs.size();
                        executeInto(n.right(), leftOut, env, outputs);
                        if (env.isBreak() && env.breakLabel().equals(label.name())) {
                            if (outputs.size() == before) {
                                outputs.add(env.breakValue());
                            }
                            env.clearBreak();
                            return JqValue.NULL;
                        }
                    }
                } else {
                    List<JqValue> leftOutputs = execute(n.left(), input, env);
                    if (leftOutputs.isEmpty()) {
                        // Left side produced no output (e.g., def statement)
                        // Pass the original input to the right side
                        executeInto(n.right(), input, env, outputs);
                    } else {
                        for (JqValue leftOut : leftOutputs) {
                            executeInto(n.right(), leftOut, env, outputs);
                            if (env.isBreak()) return JqValue.NULL;
                        }
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitComma(CommaNode n) {
                executeInto(n.left(), input, env, outputs);
                if (env.isBreak()) return JqValue.NULL;
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
                    List<JqValue> rightVals = execute(n.right(), input, env);
                    for (JqValue right : rightVals) {
                        outputs.add(JqBoolean.of(!toBool(right)));
                    }
                } else {
                    List<JqValue> leftVals = execute(n.left(), input, env);
                    for (JqValue left : leftVals) {
                        boolean leftBool = toBool(left);
                        if (n.op() == BooleanOpNode.Op.AND) {
                            if (!leftBool) { outputs.add(JqBoolean.of(false)); }
                            else {
                                List<JqValue> rightVals = execute(n.right(), input, env);
                                for (JqValue right : rightVals) {
                                    outputs.add(JqBoolean.of(toBool(right)));
                                }
                            }
                        } else { // OR
                            if (leftBool) { outputs.add(JqBoolean.of(true)); }
                            else {
                                List<JqValue> rightVals = execute(n.right(), input, env);
                                for (JqValue right : rightVals) {
                                    outputs.add(JqBoolean.of(toBool(right)));
                                }
                            }
                        }
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitNegate(NegateNode n) {
                List<JqValue> vals = execute(n.operand(), input, env);
                for (JqValue v : vals) {
                    if (v instanceof JqNumber num) {
                        // Preserve integer type when possible
                        if (num.value() instanceof Integer) {
                            outputs.add(JqNumber.of(-num.intValue()));
                        } else if (num.value() instanceof Long) {
                            outputs.add(JqNumber.of(-num.longValue()));
                        } else {
                            outputs.add(JqNumber.of(-num.doubleValue()));
                        }
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitIfThenElse(IfThenElseNode n) {
                List<JqValue> condVals = execute(n.condition(), input, env);
                for (JqValue cond : condVals) {
                    if (toBool(cond)) {
                        executeInto(n.thenBranch(), input, env, outputs);
                        return JqValue.NULL;
                    }
                }
                for (IfThenElseNode.ElifClause elif : n.elifClauses()) {
                    List<JqValue> elifCond = execute(elif.condition(), input, env);
                    for (JqValue c : elifCond) {
                        if (toBool(c)) {
                            executeInto(elif.branch(), input, env, outputs);
                            return JqValue.NULL;
                        }
                    }
                }
                if (n.elseBranch() != null) {
                    executeInto(n.elseBranch(), input, env, outputs);
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitTryCatch(TryCatchNode n) {
                try {
                    executeInto(n.tryExpr(), input, env, outputs);
                } catch (JqRuntimeException e) {
                    if (n.hasCatch()) {
                        executeInto(n.catchExpr(), JqString.of(e.errorMessage()), env, outputs);
                    }
                    // try without catch: silently ignore error
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitLabel(LabelNode n) {
                outputs.add(JqValue.NULL);
                return JqValue.NULL;
            }
            @Override public JqValue visitBreak(BreakNode n) {
                env.setBreak(n.labelName(), input);
                return JqValue.NULL;
            }
            @Override public JqValue visitBind(BindNode n) {
                List<JqValue> exprVals = execute(n.expr(), input, env);
                for (JqValue val : exprVals) {
                    env.pushScope();
                    env.bind(n.varName(), val);
                    try {
                        executeInto(n.body(), input, env, outputs);
                    } finally {
                        env.popScope();
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitVariable(VariableNode n) {
                // Check if this is a user-defined function (called without parentheses)
                if (env.hasFunction(n.name())) {
                    // Treat as function call with no arguments
                    Object funcDef = env.getFunction(n.name());
                    if (funcDef instanceof FuncDefNode fd) {
                        env.pushScope();
                        try {
                            // No arguments to bind
                            List<JqValue> bodyResults = execute(fd.body(), input, env);
                            for (JqValue v : bodyResults) {
                                outputs.add(v);
                            }
                        } finally {
                            env.popScope();
                        }
                        return JqValue.NULL;
                    }
                }
                outputs.add(env.lookup(n.name()));
                return JqValue.NULL;
            }
            @Override public JqValue visitObjectConstruct(ObjectConstructNode n) {
                Map<String, JqValue> props = new LinkedHashMap<>();
                for (ObjectConstructNode.Field f : n.fields()) {
                    // Evaluate key
                    List<JqValue> keyVals = execute(f.key(), input, env);
                    if (!keyVals.isEmpty()) {
                        String key = keyVals.get(0) instanceof JqString s ? s.value() : keyVals.get(0).toString();
                        // Evaluate value
                        List<JqValue> valVals = execute(f.value(), input, env);
                        JqValue val = valVals.isEmpty() ? JqValue.NULL : valVals.get(0);
                        props.put(key, val);
                    }
                }
                outputs.add(new JqObject(props));
                return JqValue.NULL;
            }
            @Override public JqValue visitArrayConstruct(ArrayConstructNode n) {
                List<JqValue> elements = execute(n.element(), input, env);
                env.clearBreak();
                outputs.add(new JqArray(elements));
                return JqValue.NULL;
            }
            @Override public JqValue visitStringInterp(StringInterpNode n) {
                StringBuilder sb = new StringBuilder();
                for (Object part : n.parts()) {
                    if (part instanceof String s) {
                        sb.append(s);
                    } else if (part instanceof JqAstNode node) {
                        List<JqValue> vals = execute(node, input, env);
                        for (JqValue v : vals) {
                            sb.append(v.toString());
                        }
                    }
                }
                outputs.add(JqString.of(sb.toString()));
                return JqValue.NULL;
            }
            @Override public JqValue visitSelect(SelectNode n) {
                JqValue obj = input;
                if (n.object() != null) {
                    List<JqValue> objOutputs = execute(n.object(), input, env);
                    for (JqValue o : objOutputs) {
                        List<JqValue> condVals = execute(n.condition(), o, env);
                        if (!condVals.isEmpty() && toBool(condVals.get(0))) {
                            outputs.add(o);
                        }
                    }
                } else {
                    List<JqValue> condVals = execute(n.condition(), input, env);
                    if (!condVals.isEmpty() && toBool(condVals.get(0))) {
                        outputs.add(input);
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitMap(MapNode n) {
                JqValue obj = input;
                if (n.object() != null) {
                    List<JqValue> objOutputs = execute(n.object(), input, env);
                    if (!objOutputs.isEmpty()) obj = objOutputs.get(0);
                }
                if (obj instanceof JqArray arr) {
                    List<JqValue> mapped = new ArrayList<>();
                    for (JqValue item : arr.items()) {
                        List<JqValue> vals = execute(n.function(), item, env);
                        mapped.addAll(vals);
                    }
                    outputs.add(new JqArray(mapped));
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitReduce(ReduceNode n) {
                List<JqValue> initVals = execute(n.init(), input, env);
                JqValue acc = initVals.isEmpty() ? JqValue.NULL : initVals.get(0);
                List<JqValue> seqVals = execute(n.expr(), input, env);
                for (JqValue item : seqVals) {
                    env.pushScope();
                    env.bind(n.varName(), item);
                    // Execute body with accumulator as input
                    List<JqValue> bodyVals = execute(n.body(), acc, env);
                    acc = bodyVals.isEmpty() ? acc : bodyVals.get(bodyVals.size() - 1);
                    env.popScope();
                }
                outputs.add(acc);
                return JqValue.NULL;
            }
            @Override public JqValue visitForEach(ForEachNode n) {
                List<JqValue> initVals = execute(n.init(), input, env);
                JqValue state = initVals.isEmpty() ? JqValue.NULL : initVals.get(0);
                List<JqValue> seqVals = execute(n.expr(), input, env);
                for (JqValue item : seqVals) {
                    if (env.isBreak()) break;
                    env.pushScope();
                    env.bind(n.varName(), item);
                    List<JqValue> updateVals = execute(n.update(), state, env);
                    state = updateVals.isEmpty() ? state : updateVals.get(updateVals.size() - 1);
                    if (n.extract() != null) {
                        List<JqValue> extractVals = execute(n.extract(), state, env);
                        outputs.addAll(extractVals);
                    } else {
                        outputs.add(state);
                    }
                    env.popScope();
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitLimit(LimitNode n) {
                List<JqValue> countVals = execute(n.count(), input, env);
                if (countVals.isEmpty()) return JqValue.NULL;
                // For each count value, take that many outputs from the expression
                for (JqValue countVal : countVals) {
                    int count = toInt(countVal);
                    List<JqValue> results = execute(n.expr(), input, env);
                    int limit = Math.min(count, results.size());
                    for (int i = 0; i < limit; i++) {
                        outputs.add(results.get(i));
                    }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitEmpty(EmptyNode n) {
                // empty produces no output
                return JqValue.NULL;
            }
            @Override public JqValue visitDebug(DebugNode n) {
                List<JqValue> vals = execute(n.expr(), input, env);
                for (JqValue v : vals) {
                    System.err.println(v);
                    outputs.add(v);
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitError(ErrorNode n) {
                if (n.hasMessage()) {
                    List<JqValue> msgVals = execute(n.message(), input, env);
                    String msg = msgVals.isEmpty() ? "error" : msgVals.get(0).toString();
                    throw new JqRuntimeException(msg);
                }
                throw new JqRuntimeException("error");
            }
            @Override public JqValue visitFuncCall(FuncCallNode n) {
                // Handle built-in functions
                switch (n.name()) {
                    case "length":
                        outputs.add(JqNumber.of(toLength(input)));
                        break;
                    case "keys":
                        if (input instanceof JqObject obj) {
                            List<JqValue> keys = new ArrayList<>();
                            for (String key : obj.keySet()) {
                                keys.add(JqString.of(key));
                            }
                            outputs.add(new JqArray(keys));
                        }
                        break;
                    case "values":
                        if (input instanceof JqObject obj) {
                            outputs.add(new JqArray(new ArrayList<>(obj.properties().values())));
                        }
                        break;
                    case "type":
                        outputs.add(JqString.of(input.typeName()));
                        break;
                    case "empty":
                        // empty produces no output
                        break;
                    case "not":
                        outputs.add(JqBoolean.of(!toBool(input)));
                        break;
                    case "tostring":
                        outputs.add(JqString.of(input.toString()));
                        break;
                    case "tonumber":
                        outputs.add(JqNumber.of(Double.parseDouble(input.toString())));
                        break;
                    case "tojson":
                        if (input instanceof JqNumber num && Double.isNaN(num.doubleValue())) {
                            outputs.add(JqString.of("null"));
                        } else if (input instanceof JqNumber num && Double.isInfinite(num.doubleValue())) {
                            outputs.add(JqString.of("null"));
                        } else {
                            outputs.add(JqString.of(input.toString()));
                        }
                        break;
                    case "fromjson":
                        if (input instanceof JqString s) {
                            String val = s.value().trim();
                            // Handle NaN and Infinity literals
                            if ("NaN".equals(val) || "Infinity".equals(val) || "-Infinity".equals(val)
                                    || "-NaN".equals(val) || "+NaN".equals(val)) {
                                outputs.add(JqValue.NULL);
                            } else {
                                try {
                                    Object parsed = io.nop.core.lang.json.JsonTool.parse(val);
                                    outputs.add(JqValue.of(parsed));
                                } catch (Exception e) {
                                    throw new JqRuntimeException("Invalid JSON: " + e.getMessage());
                                }
                            }
                        }
                        break;
                    case "nan":
                        outputs.add(JqNumber.of(Double.NaN));
                        break;
                    case "modulemeta":
                        // Simplified modulemeta - return empty object
                        outputs.add(new JqObject(java.util.Map.of()));
                        break;
                    case "mktime":
                        // Simplified mktime - return input as number
                        outputs.add(input);
                        break;
                    case "abs":
                        if (input instanceof JqNumber num) {
                            // Preserve integer types when possible
                            if (num.value() instanceof Long || num.value() instanceof Integer) {
                                long val = num.longValue();
                                outputs.add(JqNumber.of(Math.abs(val)));
                            } else {
                                double val = num.doubleValue();
                                if (Double.isNaN(val) || Double.isInfinite(val)) {
                                    outputs.add(JqNumber.of(Math.abs(val)));
                                } else if (val == Math.floor(val) && !Double.isInfinite(val)) {
                                    outputs.add(JqNumber.of(Math.abs((long) val)));
                                } else {
                                    outputs.add(JqNumber.of(Math.abs(val)));
                                }
                            }
                        }
                        break;
                    case "min":
                        if (input instanceof JqArray arr && !arr.isEmpty()) {
                            JqValue min = arr.get(0);
                            for (JqValue item : arr.items()) {
                                if (compare(item, min) < 0) min = item;
                            }
                            outputs.add(min);
                        }
                        break;
                    case "max":
                        if (input instanceof JqArray arr && !arr.isEmpty()) {
                            JqValue max = arr.get(0);
                            for (JqValue item : arr.items()) {
                                if (compare(item, max) > 0) max = item;
                            }
                            outputs.add(max);
                        }
                        break;
                    case "gmtime":
                        // Simplified gmtime - return input as number
                        outputs.add(input);
                        break;
                    case "builtins":
                        // Simplified builtins - return empty array
                        outputs.add(new JqArray(java.util.List.of()));
                        break;
                    case "min_by":
                        if (input instanceof JqArray arr && !n.args().isEmpty() && !arr.isEmpty()) {
                            JqValue min = arr.get(0);
                            for (JqValue item : arr.items()) {
                                List<JqValue> itemVals = execute(n.args().get(0), item, env);
                                List<JqValue> minVals = execute(n.args().get(0), min, env);
                                JqValue itemVal = itemVals.isEmpty() ? JqValue.NULL : itemVals.get(0);
                                JqValue minVal = minVals.isEmpty() ? JqValue.NULL : minVals.get(0);
                                if (compare(itemVal, minVal) < 0) min = item;
                            }
                            outputs.add(min);
                        }
                        break;
                    case "scalars":
                        // Simplified scalars - return input
                        outputs.add(input);
                        break;
                    case "paths":
                        {
                            List<JqValue> paths = new ArrayList<>();
                            collectPaths(input, new ArrayList<>(), paths);
                            outputs.add(new JqArray(paths));
                        }
                        break;
                    case "isnan":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqBoolean.of(Double.isNaN(num.doubleValue())));
                        }
                        break;
                    case "infinite":
                        outputs.add(JqNumber.of(Double.POSITIVE_INFINITY));
                        break;
                    case "floor":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqNumber.of(Math.floor(num.doubleValue())));
                        }
                        break;
                    case "max_by":
                        if (input instanceof JqArray arr && !n.args().isEmpty() && !arr.isEmpty()) {
                            JqValue max = arr.get(0);
                            for (JqValue item : arr.items()) {
                                List<JqValue> itemVals = execute(n.args().get(0), item, env);
                                List<JqValue> maxVals = execute(n.args().get(0), max, env);
                                JqValue itemVal = itemVals.isEmpty() ? JqValue.NULL : itemVals.get(0);
                                JqValue maxVal = maxVals.isEmpty() ? JqValue.NULL : maxVals.get(0);
                                if (compare(itemVal, maxVal) > 0) max = item;
                            }
                            outputs.add(max);
                        }
                        break;
                    case "utf8bytelength":
                        if (input instanceof JqString s) {
                            outputs.add(JqNumber.of(s.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
                        } else {
                            throw new JqRuntimeException(input.typeName() + " (" + input + ") only strings have UTF-8 byte length");
                        }
                        break;
                    case "toboolean":
                        if (input instanceof JqNull) {
                            throw new JqRuntimeException("null (" + input + ") cannot be parsed as a boolean");
                        } else if (input instanceof JqString s) {
                            String val = s.value().toLowerCase();
                            if ("true".equals(val)) {
                                outputs.add(JqBoolean.of(true));
                            } else if ("false".equals(val)) {
                                outputs.add(JqBoolean.of(false));
                            } else {
                                throw new JqRuntimeException("string (\"" + s.value() + "\") cannot be parsed as a boolean");
                            }
                        } else if (input instanceof JqNumber num) {
                            throw new JqRuntimeException("number (" + num + ") cannot be parsed as a boolean");
                        } else if (input instanceof JqBoolean) {
                            outputs.add(input);
                        } else {
                            throw new JqRuntimeException(input.typeName() + " (" + input + ") cannot be parsed as a boolean");
                        }
                        break;
                    case "trim":
                        if (input instanceof JqString s) {
                            outputs.add(JqString.of(s.value().trim()));
                        } else {
                            throw new JqRuntimeException("trim input must be a string");
                        }
                        break;
                    case "transpose":
                        if (input instanceof JqArray arr) {
                            if (arr.isEmpty()) {
                                outputs.add(new JqArray(new ArrayList<>()));
                            } else {
                                // Find max row length
                                int maxLen = 0;
                                for (JqValue item : arr.items()) {
                                    if (item instanceof JqArray row) {
                                        maxLen = Math.max(maxLen, row.size());
                                    }
                                }
                                // Transpose
                                List<List<JqValue>> transposed = new ArrayList<>();
                                for (int i = 0; i < maxLen; i++) {
                                    List<JqValue> col = new ArrayList<>();
                                    for (JqValue item : arr.items()) {
                                        if (item instanceof JqArray row) {
                                            col.add(i < row.size() ? row.get(i) : JqValue.NULL);
                                        } else {
                                            col.add(JqValue.NULL);
                                        }
                                    }
                                    transposed.add(col);
                                }
                                List<JqValue> result = new ArrayList<>();
                                for (List<JqValue> col : transposed) {
                                    result.add(new JqArray(col));
                                }
                                outputs.add(new JqArray(result));
                            }
                        }
                        break;
                    case "ascii_downcase":
                        if (input instanceof JqString s) {
                            outputs.add(JqString.of(s.value().toLowerCase()));
                        } else {
                            throw new JqRuntimeException("ascii_downcase input must be a string");
                        }
                        break;
                    case "ascii_upcase":
                        if (input instanceof JqString s) {
                            outputs.add(JqString.of(s.value().toUpperCase()));
                        } else {
                            throw new JqRuntimeException("ascii_upcase input must be a string");
                        }
                        break;
                    case "ltrimstr":
                        if (input instanceof JqString s && !n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString prefix) {
                                String val = s.value();
                                String pfx = prefix.value();
                                if (val.startsWith(pfx)) {
                                    outputs.add(JqString.of(val.substring(pfx.length())));
                                } else {
                                    outputs.add(s);
                                }
                            }
                        } else if (!(input instanceof JqString)) {
                            throw new JqRuntimeException("ltrimstr input must be a string");
                        }
                        break;
                    case "rtrimstr":
                        if (input instanceof JqString s && !n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString suffix) {
                                String val = s.value();
                                String sfx = suffix.value();
                                if (val.endsWith(sfx)) {
                                    outputs.add(JqString.of(val.substring(0, val.length() - sfx.length())));
                                } else {
                                    outputs.add(s);
                                }
                            }
                        } else if (!(input instanceof JqString)) {
                            throw new JqRuntimeException("rtrimstr input must be a string");
                        }
                        break;
                    case "has":
                        if (!n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty()) {
                                JqValue key = argVals.get(0);
                                if (input instanceof JqObject obj && key instanceof JqString s) {
                                    outputs.add(JqBoolean.of(obj.has(s.value())));
                                } else if (input instanceof JqArray arr && key instanceof JqNumber num) {
                                    // NaN index is never valid
                                    if (Double.isNaN(num.doubleValue())) {
                                        outputs.add(JqBoolean.of(false));
                                    } else {
                                        int idx = num.intValue();
                                        if (idx < 0) idx = arr.size() + idx;
                                        outputs.add(JqBoolean.of(idx >= 0 && idx < arr.size()));
                                    }
                                } else {
                                    outputs.add(JqBoolean.of(false));
                                }
                            }
                        }
                        break;
                    case "in":
                        if (!n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqObject obj) {
                                outputs.add(JqBoolean.of(input instanceof JqString s && obj.has(s.value())));
                            }
                        }
                        break;
                    case "contains":
                        if (!n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty()) {
                                JqValue arg = argVals.get(0);
                                if (input instanceof JqString s && arg instanceof JqString a) {
                                    outputs.add(JqBoolean.of(s.value().contains(a.value())));
                                } else if (input instanceof JqArray ia && arg instanceof JqArray aa) {
                                    outputs.add(JqBoolean.of(jqContainsArray(ia, aa)));
                                } else if (input instanceof JqObject io && arg instanceof JqObject ao) {
                                    outputs.add(JqBoolean.of(jqContainsObject(io, ao)));
                                } else {
                                    outputs.add(JqBoolean.of(false));
                                }
                            }
                        }
                        break;
                    case "startswith":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString prefix) {
                                outputs.add(JqBoolean.of(s.value().startsWith(prefix.value())));
                            }
                        }
                        break;
                    case "endswith":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString suffix) {
                                outputs.add(JqBoolean.of(s.value().endsWith(suffix.value())));
                            }
                        }
                        break;
                    case "index":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            for (JqAstNode arg : n.args()) {
                                List<JqValue> argVals = execute(arg, input, env);
                                if (!argVals.isEmpty() && argVals.get(0) instanceof JqString search) {
                                    int idx = s.value().indexOf(search.value());
                                    outputs.add(idx >= 0 ? JqNumber.of(idx) : JqValue.NULL);
                                }
                            }
                        }
                        break;
                    case "rindex":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            for (JqAstNode arg : n.args()) {
                                List<JqValue> argVals = execute(arg, input, env);
                                if (!argVals.isEmpty() && argVals.get(0) instanceof JqString search) {
                                    int idx = s.value().lastIndexOf(search.value());
                                    outputs.add(idx >= 0 ? JqNumber.of(idx) : JqValue.NULL);
                                }
                            }
                        }
                        break;
                    case "split":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString delim) {
                                if (s.value().length() > JqEnvironment.MAX_SPLIT_LIMIT) {
                                    throw new JqRuntimeException("Input string too large for split (max=" + JqEnvironment.MAX_SPLIT_LIMIT + " chars)");
                                }
                                String[] parts = s.value().split(delim.value(), -1);
                                List<JqValue> result = new ArrayList<>();
                                for (String part : parts) {
                                    result.add(JqString.of(part));
                                }
                                outputs.add(new JqArray(result));
                            }
                        }
                        break;
                    case "join":
                        if (!n.args().isEmpty() && input instanceof JqArray arr) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString delim) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < arr.size(); i++) {
                                    if (i > 0) sb.append(delim.value());
                                    sb.append(arr.get(i).toString());
                                }
                                outputs.add(JqString.of(sb.toString()));
                            }
                        }
                        break;
                    case "to_entries":
                        if (input instanceof JqObject obj) {
                            List<JqValue> entries = new ArrayList<>();
                            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                                Map<String, JqValue> entry = new LinkedHashMap<>();
                                entry.put("key", JqString.of(e.getKey()));
                                entry.put("value", e.getValue());
                                entries.add(new JqObject(entry));
                            }
                            outputs.add(new JqArray(entries));
                        }
                        break;
                    case "from_entries":
                        if (input instanceof JqArray arr) {
                            Map<String, JqValue> map = new LinkedHashMap<>();
                            for (JqValue item : arr.items()) {
                                if (item instanceof JqObject entry) {
                                    JqValue key = entry.get("key");
                                    JqValue value = entry.get("value");
                                    if (key instanceof JqString s) {
                                        map.put(s.value(), value != null ? value : JqValue.NULL);
                                    }
                                }
                            }
                            outputs.add(new JqObject(map));
                        }
                        break;
                    case "sort":
                        if (input instanceof JqArray arr) {
                            List<JqValue> sorted = new ArrayList<>(arr.items());
                            sorted.sort((a, b) -> compare(a, b));
                            outputs.add(new JqArray(sorted));
                        }
                        break;
                    case "sort_by":
                        if (input instanceof JqArray arr && !n.args().isEmpty()) {
                            List<JqValue> sorted = new ArrayList<>(arr.items());
                            sorted.sort((a, b) -> {
                                List<JqValue> aVals = execute(n.args().get(0), a, env);
                                List<JqValue> bVals = execute(n.args().get(0), b, env);
                                JqValue aVal = aVals.isEmpty() ? JqValue.NULL : aVals.get(0);
                                JqValue bVal = bVals.isEmpty() ? JqValue.NULL : bVals.get(0);
                                return compare(aVal, bVal);
                            });
                            outputs.add(new JqArray(sorted));
                        }
                        break;
                    case "group_by":
                        if (input instanceof JqArray arr && !n.args().isEmpty()) {
                            // Simple group_by implementation
                            List<JqValue> groups = new ArrayList<>();
                            for (JqValue item : arr.items()) {
                                List<JqValue> keyVals = execute(n.args().get(0), item, env);
                                JqValue key = keyVals.isEmpty() ? JqValue.NULL : keyVals.get(0);
                                // Find existing group
                                boolean found = false;
                                for (JqValue group : groups) {
                                    if (group instanceof JqArray g && g.size() > 0) {
                                        JqValue firstItem = g.get(0);
                                        List<JqValue> groupKeyVals = execute(n.args().get(0), firstItem, env);
                                        JqValue groupKey = groupKeyVals.isEmpty() ? JqValue.NULL : groupKeyVals.get(0);
                                        if (compare(key, groupKey) == 0) {
                                            groups.set(groups.indexOf(group), g.add(item));
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                                if (!found) {
                                    groups.add(new JqArray(List.of(item)));
                                }
                            }
                            outputs.add(new JqArray(groups));
                        }
                        break;
                    case "unique":
                        if (input instanceof JqArray arr) {
                            List<JqValue> unique = new ArrayList<>();
                            for (JqValue item : arr.items()) {
                                boolean found = false;
                                for (JqValue u : unique) {
                                    if (compare(item, u) == 0) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    unique.add(item);
                                }
                            }
                            outputs.add(new JqArray(unique));
                        }
                        break;
                    case "reverse":
                        if (input instanceof JqArray arr) {
                            List<JqValue> reversed = new ArrayList<>(arr.items());
                            Collections.reverse(reversed);
                            outputs.add(new JqArray(reversed));
                        }
                        break;
                    case "flatten":
                        if (input instanceof JqArray arr) {
                            int depth = -1; // -1 means unlimited
                            if (!n.args().isEmpty()) {
                                List<JqValue> depthVals = execute(n.args().get(0), input, env);
                                if (!depthVals.isEmpty() && depthVals.get(0) instanceof JqNumber dn) {
                                    depth = dn.intValue();
                                }
                            }
                            List<JqValue> flattened = new ArrayList<>();
                            flattenArray(arr, flattened, depth, 0);
                            outputs.add(new JqArray(flattened));
                        }
                        break;
                    case "range":
                        if (!n.args().isEmpty()) {
                            List<List<JqValue>> allArgVals = new ArrayList<>();
                            for (JqAstNode arg : n.args()) {
                                allArgVals.add(execute(arg, input, env));
                            }
                            if (allArgVals.size() == 1) {
                                for (JqValue v : allArgVals.get(0)) {
                                    if (v instanceof JqNumber num) {
                                        int end = num.intValue();
                                        for (int i = 0; i < end; i++) {
                                            outputs.add(JqNumber.of(i));
                                        }
                                    } else if (v instanceof JqArray arr) {
                                        for (JqValue item : arr.items()) {
                                            outputs.add(item);
                                        }
                                    }
                                }
                            } else if (allArgVals.size() == 2) {
                                for (JqValue mVal : allArgVals.get(0)) {
                                    for (JqValue nVal : allArgVals.get(1)) {
                                        int m = toInt(mVal);
                                        int end = toInt(nVal);
                                        for (int i = m; i < end; i++) {
                                            outputs.add(JqNumber.of(i));
                                        }
                                    }
                                }
                            } else if (allArgVals.size() == 3) {
                                for (JqValue mVal : allArgVals.get(0)) {
                                    for (JqValue nVal : allArgVals.get(1)) {
                                        for (JqValue sVal : allArgVals.get(2)) {
                                            int m = toInt(mVal);
                                            int end = toInt(nVal);
                                            int s = toInt(sVal);
                                            if (s > 0) {
                                                for (int i = m; i < end; i += s) outputs.add(JqNumber.of(i));
                                            } else if (s < 0) {
                                                for (int i = m; i > end; i += s) outputs.add(JqNumber.of(i));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    case "error":
                        if (!n.args().isEmpty()) {
                            List<JqValue> msgVals = execute(n.args().get(0), input, env);
                            String msg = msgVals.isEmpty() ? "error" : msgVals.get(0).toString();
                            throw new JqRuntimeException(msg);
                        }
                        throw new JqRuntimeException("error");
                    case "debug":
                        System.err.println(input);
                        outputs.add(input);
                        break;
                    case "null":
                        outputs.add(JqValue.NULL);
                        break;
                    case "true":
                        outputs.add(JqBoolean.of(true));
                        break;
                    case "false":
                        outputs.add(JqBoolean.of(false));
                        break;
                    case "input":
                        outputs.add(input);
                        break;
                    case "first":
                        if (!n.args().isEmpty()) {
                            List<JqValue> results = execute(n.args().get(0), input, env);
                            if (!results.isEmpty()) {
                                outputs.add(results.get(0));
                            }
                        }
                        break;
                    case "last":
                        if (!n.args().isEmpty()) {
                            List<JqValue> results = execute(n.args().get(0), input, env);
                            if (!results.isEmpty()) {
                                outputs.add(results.get(results.size() - 1));
                            }
                        }
                        break;
                    case "nth":
                        if (n.args().size() >= 2) {
                            List<JqValue> idxVals = execute(n.args().get(0), input, env);
                            List<JqValue> results = execute(n.args().get(1), input, env);
                            for (JqValue idxVal : idxVals) {
                                int idx = toInt(idxVal);
                                if (idx >= 0 && idx < results.size()) {
                                    outputs.add(results.get(idx));
                                } else if (idx < 0) {
                                    throw new JqRuntimeException("nth doesn't support negative indices");
                                }
                            }
                        }
                        break;
                    case "limit":
                        if (n.args().size() >= 2) {
                            List<JqValue> countVals = execute(n.args().get(0), input, env);
                            // Check for negative count before evaluating the expression
                            for (JqValue countVal : countVals) {
                                int count = toInt(countVal);
                                if (count < 0) {
                                    throw new JqRuntimeException("limit doesn't support negative count");
                                }
                            }
                            List<JqValue> results = execute(n.args().get(1), input, env);
                            for (JqValue countVal : countVals) {
                                int count = toInt(countVal);
                                int limit = Math.min(count, results.size());
                                for (int i = 0; i < limit; i++) {
                                    outputs.add(results.get(i));
                                }
                            }
                        }
                        break;
                    case "skip":
                        if (n.args().size() >= 2) {
                            List<JqValue> countVals = execute(n.args().get(0), input, env);
                            // Check for negative count before evaluating the expression
                            for (JqValue countVal : countVals) {
                                int count = toInt(countVal);
                                if (count < 0) {
                                    throw new JqRuntimeException("skip doesn't support negative count");
                                }
                            }
                            List<JqValue> results = execute(n.args().get(1), input, env);
                            for (JqValue countVal : countVals) {
                                int count = toInt(countVal);
                                for (int i = count; i < results.size(); i++) {
                                    outputs.add(results.get(i));
                                }
                            }
                        }
                        break;
                    case "all":
                        if (n.args().isEmpty()) {
                            // all without args: check truthiness of input
                            if (input instanceof JqArray arr) {
                                boolean allTrue = true;
                                for (JqValue item : arr.items()) {
                                    if (!toBool(item)) { allTrue = false; break; }
                                }
                                outputs.add(JqBoolean.of(allTrue));
                            } else {
                                outputs.add(JqBoolean.of(toBool(input)));
                            }
                        } else {
                            boolean allTrue = true;
                            if (input instanceof JqArray arr) {
                                for (JqValue item : arr.items()) {
                                    List<JqValue> results = execute(n.args().get(0), item, env);
                                    if (results.isEmpty() || !toBool(results.get(0))) {
                                        allTrue = false;
                                        break;
                                    }
                                }
                                if (arr.isEmpty()) allTrue = true; // empty array: all() is true
                            } else {
                                List<JqValue> results = execute(n.args().get(0), input, env);
                                allTrue = !results.isEmpty() && toBool(results.get(0));
                            }
                            outputs.add(JqBoolean.of(allTrue));
                        }
                        break;
                    case "any":
                        if (n.args().isEmpty()) {
                            // any without args: check truthiness of input
                            if (input instanceof JqArray arr) {
                                boolean anyTrue = false;
                                for (JqValue item : arr.items()) {
                                    if (toBool(item)) { anyTrue = true; break; }
                                }
                                outputs.add(JqBoolean.of(anyTrue));
                            } else {
                                outputs.add(JqBoolean.of(toBool(input)));
                            }
                        } else {
                            boolean anyTrue = false;
                            if (input instanceof JqArray arr) {
                                for (JqValue item : arr.items()) {
                                    List<JqValue> results = execute(n.args().get(0), item, env);
                                    if (!results.isEmpty() && toBool(results.get(0))) {
                                        anyTrue = true;
                                        break;
                                    }
                                }
                            } else {
                                List<JqValue> results = execute(n.args().get(0), input, env);
                                anyTrue = !results.isEmpty() && toBool(results.get(0));
                            }
                            outputs.add(JqBoolean.of(anyTrue));
                        }
                        break;
                    case "not_empty":
                        outputs.add(JqBoolean.of(!input.toString().isEmpty()));
                        break;
                    case "env":
                        outputs.add(JqValue.NULL);
                        break;
                    case "now":
                        outputs.add(JqNumber.of(System.currentTimeMillis() / 1000.0));
                        break;
                    case "indices":
                        if (!n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty()) {
                                JqValue search = argVals.get(0);
                                if (input instanceof JqString s && search instanceof JqString searchStr) {
                                    List<JqValue> indices = new ArrayList<>();
                                    int idx = 0;
                                    while (idx < s.value().length()) {
                                        int found = s.value().indexOf(searchStr.value(), idx);
                                        if (found == -1) break;
                                        indices.add(JqNumber.of(found));
                                        idx = found + 1;
                                    }
                                    outputs.add(new JqArray(indices));
                                } else if (input instanceof JqArray arr) {
                                    List<JqValue> indices = new ArrayList<>();
                                    for (int i = 0; i < arr.size(); i++) {
                                        if (compare(arr.get(i), search) == 0) {
                                            indices.add(JqNumber.of(i));
                                        }
                                    }
                                    outputs.add(new JqArray(indices));
                                }
                            }
                        }
                        break;
                    case "path":
                        if (!n.args().isEmpty()) {
                            // path(expr) returns the paths to all values produced by expr
                            // We need to trace paths through the AST structure
                            List<JqValue> paths = new ArrayList<>();
                            tracePaths(n.args().get(0), input, env, new ArrayList<>(), paths);
                            for (JqValue path : paths) {
                                outputs.add(path);
                            }
                        }
                        break;
                    case "getpath":
                        if (!n.args().isEmpty()) {
                            List<JqValue> pathVals = execute(n.args().get(0), input, env);
                            if (!pathVals.isEmpty() && pathVals.get(0) instanceof JqArray pathArr) {
                                JqValue current = input;
                                for (JqValue pathElem : pathArr.items()) {
                                    if (pathElem instanceof JqString s) {
                                        if (current instanceof JqObject obj) {
                                            current = obj.get(s.value());
                                        } else {
                                            current = null;
                                            break;
                                        }
                                    } else if (pathElem instanceof JqNumber num) {
                                        if (current instanceof JqArray arr) {
                                            int idx = num.intValue();
                                            if (idx < 0) idx = arr.size() + idx;
                                            if (idx >= 0 && idx < arr.size()) {
                                                current = arr.get(idx);
                                            } else {
                                                current = null;
                                                break;
                                            }
                                        } else {
                                            current = null;
                                            break;
                                        }
                                    }
                                }
                                outputs.add(current != null ? current : JqValue.NULL);
                            }
                        }
                        break;
                    case "setpath":
                        if (n.args().size() >= 2) {
                            List<JqValue> pathVals = execute(n.args().get(0), input, env);
                            List<JqValue> valVals = execute(n.args().get(1), input, env);
                            JqValue newVal = valVals.isEmpty() ? JqValue.NULL : valVals.get(0);
                            if (!pathVals.isEmpty() && pathVals.get(0) instanceof JqArray pathArr) {
                                outputs.add(setPath(input, pathArr.items(), newVal));
                            }
                        }
                        break;
                    case "delpaths":
                        if (!n.args().isEmpty()) {
                            List<JqValue> pathsVals = execute(n.args().get(0), input, env);
                            if (!pathsVals.isEmpty() && pathsVals.get(0) instanceof JqArray pathsArr) {
                                JqValue result = input;
                                // Delete paths in reverse order to avoid index shifting
                                List<List<JqValue>> sortedPaths = new ArrayList<>();
                                for (JqValue p : pathsArr.items()) {
                                    if (p instanceof JqArray pa) {
                                        sortedPaths.add(pa.items());
                                    }
                                }
                                sortedPaths.sort((a, b) -> {
                                    for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                                        int cmp = comparePathElem(a.get(i), b.get(i));
                                        if (cmp != 0) return cmp;
                                    }
                                    return Integer.compare(b.size(), a.size());
                                });
                                for (List<JqValue> path : sortedPaths) {
                                    result = deletePath(result, path, 0);
                                }
                                outputs.add(result);
                            }
                        }
                        break;
                    case "del":
                        if (!n.args().isEmpty()) {
                            // del(f) - f is evaluated to produce paths to delete
                            List<JqValue> paths = collectPaths(n.args().get(0), input, env);
                            JqValue result = input;
                            // Sort paths in reverse depth order
                            paths.sort((a, b) -> {
                                if (a instanceof JqArray pa && b instanceof JqArray pb) {
                                    for (int i = 0; i < Math.min(pa.size(), pb.size()); i++) {
                                        int cmp = comparePathElem(pa.get(i), pb.get(i));
                                        if (cmp != 0) return cmp;
                                    }
                                    return Integer.compare(pb.size(), pa.size());
                                }
                                return 0;
                            });
                            for (JqValue p : paths) {
                                if (p instanceof JqArray pa) {
                                    result = deletePath(result, pa.items(), 0);
                                }
                            }
                            outputs.add(result);
                        }
                        break;
                    case "walk":
                        if (!n.args().isEmpty()) {
                            walkApply(n.args().get(0), input, env, outputs);
                        }
                        break;
                    case "test":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString pattern) {
                                outputs.add(JqBoolean.of(regexTest(s.value(), pattern.value())));
                            }
                        }
                        break;
                    case "match":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString pattern) {
                                outputs.add(regexMatch(s.value(), pattern.value()));
                            }
                        }
                        break;
                    case "capture":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString pattern) {
                                outputs.add(regexCapture(s.value(), pattern.value()));
                            }
                        }
                        break;
                    case "scan":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString pattern) {
                                outputs.add(regexScan(s.value(), pattern.value()));
                            }
                        }
                        break;
                    case "splits":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString pattern) {
                                java.util.regex.Matcher m = compileRegex(pattern.value()).matcher(s.value());
                                while (m.find()) {
                                    outputs.add(JqString.of(m.group()));
                                }
                            }
                        }
                        break;
                    case "sub":
                        if (n.args().size() >= 2 && input instanceof JqString s) {
                            List<JqValue> patternVals = execute(n.args().get(0), input, env);
                            List<JqValue> replacementVals = execute(n.args().get(1), input, env);
                            if (!patternVals.isEmpty() && patternVals.get(0) instanceof JqString pattern
                                    && !replacementVals.isEmpty() && replacementVals.get(0) instanceof JqString replacement) {
                                java.util.regex.Matcher m = compileRegex(pattern.value()).matcher(s.value());
                                outputs.add(JqString.of(m.replaceFirst(replacement.value())));
                            }
                        }
                        break;
                    case "gsub":
                        if (n.args().size() >= 2 && input instanceof JqString s) {
                            List<JqValue> patternVals = execute(n.args().get(0), input, env);
                            List<JqValue> replacementVals = execute(n.args().get(1), input, env);
                            if (!patternVals.isEmpty() && patternVals.get(0) instanceof JqString pattern
                                    && !replacementVals.isEmpty() && replacementVals.get(0) instanceof JqString replacement) {
                                java.util.regex.Matcher m = compileRegex(pattern.value()).matcher(s.value());
                                outputs.add(JqString.of(m.replaceAll(replacement.value())));
                            }
                        }
                        break;
                    case "with_entries":
                        if (!n.args().isEmpty() && input instanceof JqObject obj) {
                            List<JqValue> entryResults = new ArrayList<>();
                            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                                Map<String, JqValue> entry = new LinkedHashMap<>();
                                entry.put("key", JqString.of(e.getKey()));
                                entry.put("value", e.getValue());
                                JqValue entryVal = new JqObject(entry);
                                List<JqValue> transformed = execute(n.args().get(0), entryVal, env);
                                entryResults.addAll(transformed);
                            }
                            // Merge results back into object
                            Map<String, JqValue> resultProps = new LinkedHashMap<>();
                            for (JqValue r : entryResults) {
                                if (r instanceof JqObject entryObj) {
                                    JqValue key = entryObj.get("key");
                                    JqValue val = entryObj.get("value");
                                    if (key instanceof JqString ks) {
                                        resultProps.put(ks.value(), val != null ? val : JqValue.NULL);
                                    }
                                }
                            }
                            outputs.add(new JqObject(resultProps));
                        }
                        break;
                    case "unique_by":
                        if (!n.args().isEmpty() && input instanceof JqArray arr) {
                            List<JqValue> unique = new ArrayList<>();
                            List<JqValue> keys = new ArrayList<>();
                            for (JqValue item : arr.items()) {
                                List<JqValue> keyVals = execute(n.args().get(0), item, env);
                                JqValue key = keyVals.isEmpty() ? JqValue.NULL : keyVals.get(0);
                                boolean found = false;
                                for (JqValue k : keys) {
                                    if (compare(key, k) == 0) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    unique.add(item);
                                    keys.add(key);
                                }
                            }
                            outputs.add(new JqArray(unique));
                        }
                        break;
                    case "trimstr":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString prefix) {
                                String val = s.value();
                                String pfx = prefix.value();
                                if (val.startsWith(pfx)) {
                                    outputs.add(JqString.of(val.substring(pfx.length())));
                                } else {
                                    outputs.add(s);
                                }
                            }
                        }
                        break;
                    case "strptime":
                        // Simplified strptime - just return input
                        outputs.add(input);
                        break;
                    case "add":
                        if (input instanceof JqArray arr) {
                            if (arr.isEmpty()) {
                                outputs.add(JqValue.NULL);
                            } else {
                                JqValue result = arr.get(0);
                                for (int i = 1; i < arr.size(); i++) {
                                    result = evalMathOp(MathOpNode.Op.ADD, result, arr.get(i));
                                }
                                outputs.add(result);
                            }
                        }
                        break;
                    case "strflocaltime":
                        // Simplified strflocaltime - just return input as string
                        outputs.add(JqString.of(input.toString()));
                        break;
                    case "map_values":
                        if (!n.args().isEmpty() && input instanceof JqArray arr) {
                            List<JqValue> mapped = new ArrayList<>();
                            for (JqValue item : arr.items()) {
                                List<JqValue> vals = execute(n.args().get(0), item, env);
                                mapped.addAll(vals);
                            }
                            outputs.add(new JqArray(mapped));
                        }
                        break;
                    case "JOIN":
                        if (!n.args().isEmpty() && input instanceof JqArray arr) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString delim) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < arr.size(); i++) {
                                    if (i > 0) sb.append(delim.value());
                                    sb.append(arr.get(i).toString());
                                }
                                outputs.add(JqString.of(sb.toString()));
                            }
                        }
                        break;
                    case "INDEX":
                        if (!n.args().isEmpty() && input instanceof JqString s) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString search) {
                                int idx = s.value().indexOf(search.value());
                                outputs.add(idx >= 0 ? JqNumber.of(idx) : JqValue.NULL);
                            }
                        }
                        break;
                    case "bsearch":
                        if (!n.args().isEmpty() && input instanceof JqArray arr) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty()) {
                                JqValue target = argVals.get(0);
                                int lo = 0, hi = arr.size() - 1;
                                while (lo <= hi) {
                                    int mid = (lo + hi) / 2;
                                    int cmp = compare(arr.get(mid), target);
                                    if (cmp == 0) {
                                        outputs.add(JqNumber.of(mid));
                                        break;
                                    } else if (cmp < 0) {
                                        lo = mid + 1;
                                    } else {
                                        hi = mid - 1;
                                    }
                                }
                                if (outputs.isEmpty()) {
                                    // Not found - return insertion point (negated)
                                    outputs.add(JqNumber.of(-(lo + 1)));
                                }
                            }
                        }
                        break;
                    case "_strindices":
                        if (!n.args().isEmpty()) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString search) {
                                if (input instanceof JqString s) {
                                    List<JqValue> indices = new ArrayList<>();
                                    int idx = 0;
                                    while (idx < s.value().length()) {
                                        int found = s.value().indexOf(search.value(), idx);
                                        if (found == -1) break;
                                        indices.add(JqNumber.of(found));
                                        idx = found + 1;
                                    }
                                    outputs.add(new JqArray(indices));
                                } else {
                                    throw new JqRuntimeException(input.typeName() + " (" + input + ") cannot be searched, as it is not a string");
                                }
                            } else if (!argVals.isEmpty()) {
                                throw new JqRuntimeException(argVals.get(0).typeName() + " (" + argVals.get(0) + ") is not a string");
                            }
                        }
                        break;
                    case "IN":
                        if (!n.args().isEmpty()) {
                            // IN(a;b) - check if input is in a OR in b
                            boolean found = false;
                            for (JqAstNode argExpr : n.args()) {
                                List<JqValue> argVals = execute(argExpr, input, env);
                                for (JqValue arg : argVals) {
                                    if (arg instanceof JqObject obj) {
                                        if (input instanceof JqString s && obj.has(s.value())) {
                                            found = true;
                                            break;
                                        }
                                    } else if (arg instanceof JqArray arr) {
                                        for (JqValue item : arr.items()) {
                                            if (compare(input, item) == 0) {
                                                found = true;
                                                break;
                                            }
                                        }
                                    } else {
                                        if (compare(input, arg) == 0) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (found) break;
                                }
                                if (found) break;
                            }
                            outputs.add(JqBoolean.of(found));
                        }
                        break;
                    case "strftime":
                        if (!n.args().isEmpty() && input instanceof JqNumber num) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString format) {
                                // Simplified strftime - just return timestamp as string
                                outputs.add(JqString.of(String.valueOf((long) num.doubleValue())));
                            }
                        }
                        break;
                    case "pick":
                        if (!n.args().isEmpty() && input instanceof JqObject obj) {
                            List<JqValue> argVals = execute(n.args().get(0), input, env);
                            if (!argVals.isEmpty() && argVals.get(0) instanceof JqString key) {
                                JqValue val = obj.get(key.value());
                                outputs.add(val != null ? val : JqValue.NULL);
                            }
                        }
                        break;
                    case "isempty":
                        if (!n.args().isEmpty()) {
                            List<JqValue> results = execute(n.args().get(0), input, env);
                            outputs.add(JqBoolean.of(results.isEmpty()));
                        }
                        break;
                    case "repeat":
                        if (!n.args().isEmpty()) {
                            // Simplified repeat - apply function count times
                            List<JqValue> results = execute(n.args().get(0), input, env);
                            outputs.addAll(results);
                        }
                        break;
                    case "until":
                        if (!n.args().isEmpty()) {
                            // Simplified until - apply function until condition
                            List<JqValue> results = execute(n.args().get(0), input, env);
                            outputs.addAll(results);
                        }
                        break;
                    case "while":
                        if (!n.args().isEmpty()) {
                            // Simplified while - apply function while condition
                            List<JqValue> results = execute(n.args().get(0), input, env);
                            outputs.addAll(results);
                        }
                        break;
                    case "_floor":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqNumber.of(Math.floor(num.doubleValue())));
                        }
                        break;
                    case "ceil":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqNumber.of(Math.ceil(num.doubleValue())));
                        }
                        break;
                    case "round":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqNumber.of(Math.round(num.doubleValue())));
                        }
                        break;
                    case "sqrt":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqNumber.of(Math.sqrt(num.doubleValue())));
                        }
                        break;
                    case "fabs":
                        if (input instanceof JqNumber num) {
                            outputs.add(JqNumber.of(Math.abs(num.doubleValue())));
                        }
                        break;
                    default:
                        // Check for user-defined functions
                        if (env.hasFunction(n.name())) {
                            Object funcDef = env.getFunction(n.name());
                            if (funcDef instanceof FuncDefNode fd) {
                                env.pushScope();
                                try {
                                    // Bind arguments to parameters
                                    List<String> params = fd.params();
                                    for (int i = 0; i < params.size(); i++) {
                                        JqValue argVal = JqValue.NULL;
                                        if (i < n.args().size()) {
                                            List<JqValue> argResults = execute(n.args().get(i), input, env);
                                            argVal = argResults.isEmpty() ? JqValue.NULL : argResults.get(0);
                                        }
                                        env.bind(params.get(i), argVal);
                                    }
                                    // Execute function body
                                    List<JqValue> bodyResults = execute(fd.body(), input, env);
                                    outputs.addAll(bodyResults);
                                } finally {
                                    env.popScope();
                                }
                            }
                        } else {
                            throw new JqRuntimeException("Unknown function: " + n.name());
                        }
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitFuncDef(FuncDefNode n) {
                env.defineFunction(n.name(), n);
                // Don't add to outputs - the semicolon separator handles the rest
                return JqValue.NULL;
            }
            @Override public JqValue visitFormat(FormatNode n) {
                JqValue val = input;
                if (n.object() != null) {
                    List<JqValue> vals = execute(n.object(), input, env);
                    if (!vals.isEmpty()) val = vals.get(0);
                }
                String result = switch (n.format()) {
                    case "text" -> val.toString();
                    case "json" -> val.toString();
                    case "html" -> htmlEscape(val.toString());
                    case "uri" -> uriEncode(val.toString());
                    case "urid" -> uriDecode(val.toString());
                    case "csv" -> {
                        if (val instanceof JqArray arr) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < arr.size(); i++) {
                                if (i > 0) sb.append(",");
                                sb.append(arr.get(i).toString());
                            }
                            yield sb.toString();
                        }
                        yield val.toString();
                    }
                    case "tsv" -> {
                        if (val instanceof JqArray arr) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < arr.size(); i++) {
                                if (i > 0) sb.append("\t");
                                sb.append(arr.get(i).toString());
                            }
                            yield sb.toString();
                        }
                        yield val.toString();
                    }
                    case "sh" -> shEscape(val.toString());
                    case "base64" -> base64Encode(val.toString());
                    case "base64d" -> base64Decode(val.toString());
                    default -> throw new JqRuntimeException("Unknown format: @" + n.format());
                };
                outputs.add(JqString.of(result));
                return JqValue.NULL;
            }
        });
    }

    // Helper methods

    private JqValue getField(JqValue obj, String name) {
        if (obj instanceof JqObject map) {
            JqValue val = map.get(name);
            return val != null ? val : JqValue.NULL;
        }
        if (obj instanceof JqNull) {
            return JqValue.NULL;
        }
        // For non-object, non-null types: in jq, accessing a field on a non-object is an error
        // but only when not wrapped in try (?). The try-catch will handle this.
        throw new JqRuntimeException("Cannot index " + obj.typeName() + " with string (\"" + name + "\")");
    }

    private JqValue getIndex(JqValue obj, JqValue index) {
        if (obj instanceof JqArray arr && index instanceof JqNumber num) {
            int idx = num.intValue();
            if (idx < 0) idx = arr.size() + idx;
            if (idx >= 0 && idx < arr.size()) return arr.get(idx);
            return JqValue.NULL;
        }
        if (obj instanceof JqObject map && index instanceof JqString str) {
            JqValue val = map.get(str.value());
            return val != null ? val : JqValue.NULL;
        }
        return null;
    }

    private JqValue evalMathOp(MathOpNode.Op op, JqValue left, JqValue right) {
        if (left instanceof JqNumber ln && right instanceof JqNumber rn) {
            // Preserve integer types when both operands are integers
            boolean bothInt = isInteger(ln) && isInteger(rn);
            return switch (op) {
                case ADD -> bothInt ? JqNumber.of(ln.intValue() + rn.intValue()) : JqNumber.of(ln.doubleValue() + rn.doubleValue());
                case SUB -> bothInt ? JqNumber.of(ln.intValue() - rn.intValue()) : JqNumber.of(ln.doubleValue() - rn.doubleValue());
                case MUL -> bothInt ? JqNumber.of(ln.intValue() * rn.intValue()) : JqNumber.of(ln.doubleValue() * rn.doubleValue());
                case DIV -> {
                    if (bothInt && rn.intValue() != 0 && ln.intValue() % rn.intValue() == 0) {
                        yield JqNumber.of(ln.intValue() / rn.intValue());
                    }
                    yield JqNumber.of(ln.doubleValue() / rn.doubleValue());
                }
                case MOD -> bothInt ? JqNumber.of(ln.intValue() % rn.intValue()) : JqNumber.of(ln.doubleValue() % rn.doubleValue());
            };
        }
        // String repetition: "abc" * 3 = "abcabcabc"
        if (op == MathOpNode.Op.MUL) {
            if (left instanceof JqString ls && right instanceof JqNumber rn) {
                int count = rn.intValue();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    sb.append(ls.value());
                }
                return JqString.of(sb.toString());
            }
            if (left instanceof JqNumber ln && right instanceof JqString rs) {
                int count = ln.intValue();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    sb.append(rs.value());
                }
                return JqString.of(sb.toString());
            }
        }
        if (op == MathOpNode.Op.ADD) {
            if (left instanceof JqString ls && right instanceof JqString rs) {
                return JqString.of(ls.value() + rs.value());
            }
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
        throw new JqRuntimeException("Cannot apply " + op + " to " + left.typeName() + " and " + right.typeName());
    }

    private boolean evalComparison(ComparisonNode.Op op, JqValue left, JqValue right) {
        int cmp = compare(left, right);
        return switch (op) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
        };
    }

    private int compare(JqValue a, JqValue b) {
        if (a.equals(b)) return 0;
        if (a instanceof JqNumber && b instanceof JqNumber) {
            return Double.compare(((JqNumber) a).doubleValue(), ((JqNumber) b).doubleValue());
        }
        if (a instanceof JqString && b instanceof JqString) {
            return ((JqString) a).value().compareTo(((JqString) b).value());
        }
        // null < non-null
        if (a instanceof JqNull) return -1;
        if (b instanceof JqNull) return 1;
        return a.toString().compareTo(b.toString());
    }

    private boolean toBool(JqValue v) {
        if (v instanceof JqNull) return false;
        if (v instanceof JqBoolean b) return b.value();
        if (v instanceof JqNumber n) return n.doubleValue() != 0;
        if (v instanceof JqString s) return !s.value().isEmpty();
        if (v instanceof JqArray a) return !a.isEmpty();
        if (v instanceof JqObject o) return !o.isEmpty();
        return true;
    }

    private boolean isInteger(JqNumber n) {
        if (n.value() instanceof Integer || n.value() instanceof Long) return true;
        double d = n.doubleValue();
        return d == Math.floor(d) && !Double.isInfinite(d);
    }

    private boolean jqContainsArray(JqArray input, JqArray arg) {
        // In jq, [1,2,3] contains [1,2] is true (subset)
        // [1,2,3] contains [1,4] is false
        if (arg.size() > input.size()) return false;
        for (JqValue argItem : arg.items()) {
            boolean found = false;
            for (JqValue inputItem : input.items()) {
                if (jqContains(inputItem, argItem)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean jqContainsObject(JqObject input, JqObject arg) {
        // In jq, {a:1,b:2} contains {a:1} is true
        for (String key : arg.keySet()) {
            if (!input.has(key)) return false;
            if (!jqContains(input.get(key), arg.get(key))) return false;
        }
        return true;
    }

    private boolean jqContains(JqValue input, JqValue arg) {
        if (arg instanceof JqNull) return true; // null is contained in everything
        if (input instanceof JqString s && arg instanceof JqString a) {
            return s.value().contains(a.value());
        }
        if (input instanceof JqNumber && arg instanceof JqNumber) {
            return compare(input, arg) == 0;
        }
        if (input instanceof JqBoolean && arg instanceof JqBoolean) {
            return input.equals(arg);
        }
        if (input instanceof JqArray ia && arg instanceof JqArray aa) {
            return jqContainsArray(ia, aa);
        }
        if (input instanceof JqObject io && arg instanceof JqObject ao) {
            return jqContainsObject(io, ao);
        }
        return input.equals(arg);
    }

    private int floorToInt(JqValue v) {
        if (v instanceof JqNumber n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
            return (int) Math.floor(d);
        }
        throw new JqRuntimeException("Cannot convert " + v.typeName() + " to int");
    }

    private int ceilToInt(JqValue v) {
        if (v instanceof JqNumber n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
            return (int) Math.ceil(d);
        }
        throw new JqRuntimeException("Cannot convert " + v.typeName() + " to int");
    }

    private int toInt(JqValue v) {
        if (v instanceof JqNumber n) return n.intValue();
        throw new JqRuntimeException("Cannot convert " + v.typeName() + " to int");
    }

    private void flattenArray(JqArray arr, List<JqValue> result, int maxDepth, int currentDepth) {
        for (JqValue item : arr.items()) {
            if (item instanceof JqArray inner && (maxDepth < 0 || currentDepth < maxDepth)) {
                flattenArray(inner, result, maxDepth, currentDepth + 1);
            } else {
                result.add(item);
            }
        }
    }

    private void collectPaths(JqValue value, List<JqValue> currentPath, List<JqValue> results) {
        if (value instanceof JqObject obj) {
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<JqValue> newPath = new ArrayList<>(currentPath);
                newPath.add(JqString.of(e.getKey()));
                collectPaths(e.getValue(), newPath, results);
            }
        } else if (value instanceof JqArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                List<JqValue> newPath = new ArrayList<>(currentPath);
                newPath.add(JqNumber.of(i));
                collectPaths(arr.get(i), newPath, results);
            }
        } else {
            results.add(new JqArray(new ArrayList<>(currentPath)));
        }
    }

    private List<JqValue> findPath(JqValue tree, JqValue target, List<JqValue> currentPath) {
        if (compare(tree, target) == 0) {
            return new ArrayList<>(currentPath);
        }
        if (tree instanceof JqObject obj) {
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                List<JqValue> newPath = new ArrayList<>(currentPath);
                newPath.add(JqString.of(e.getKey()));
                List<JqValue> found = findPath(e.getValue(), target, newPath);
                if (found != null) return found;
            }
        } else if (tree instanceof JqArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                List<JqValue> newPath = new ArrayList<>(currentPath);
                newPath.add(JqNumber.of(i));
                List<JqValue> found = findPath(arr.get(i), target, newPath);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void tracePaths(JqAstNode expr, JqValue input, JqEnvironment env,
                            List<JqValue> currentPath, List<JqValue> results) {
        if (expr instanceof FieldAccessNode fan) {
            if (fan.hasExplicitObject()) {
                // First trace the object to get its path
                List<JqValue> objPaths = new ArrayList<>();
                tracePaths(fan.object(), input, env, currentPath, objPaths);
                // Then add the field name to each object path
                for (JqValue objPath : objPaths) {
                    List<JqValue> path = new ArrayList<>();
                    if (objPath instanceof JqArray arr) {
                        path.addAll(arr.items());
                    }
                    path.add(JqString.of(fan.fieldName()));
                    results.add(new JqArray(path));
                }
                if (objPaths.isEmpty()) {
                    // Object produces no paths, but field access still creates a path
                    List<JqValue> path = new ArrayList<>(currentPath);
                    path.add(JqString.of(fan.fieldName()));
                    results.add(new JqArray(path));
                }
            } else {
                List<JqValue> path = new ArrayList<>(currentPath);
                path.add(JqString.of(fan.fieldName()));
                results.add(new JqArray(path));
            }
        } else if (expr instanceof IndexAccessNode) {
            IndexAccessNode ian = (IndexAccessNode) expr;
            if (ian.hasExplicitObject()) {
                // First trace the object to get its path
                List<JqValue> objPaths = new ArrayList<>();
                tracePaths(ian.object(), input, env, currentPath, objPaths);
                // Then add the index to each object path
                List<JqValue> idxOutputs = execute(ian.index(), input, env);
                for (JqValue objPath : objPaths) {
                    for (JqValue idx : idxOutputs) {
                        List<JqValue> path = new ArrayList<>();
                        if (objPath instanceof JqArray arr) {
                            path.addAll(arr.items());
                        }
                        path.add(idx);
                        results.add(new JqArray(path));
                    }
                }
                if (objPaths.isEmpty()) {
                    List<JqValue> idxOutputs2 = execute(ian.index(), input, env);
                    for (JqValue idx : idxOutputs2) {
                        List<JqValue> path = new ArrayList<>(currentPath);
                        path.add(idx);
                        results.add(new JqArray(path));
                    }
                }
            } else {
                List<JqValue> idxOutputs = execute(ian.index(), input, env);
                for (JqValue idx : idxOutputs) {
                    List<JqValue> path = new ArrayList<>(currentPath);
                    path.add(idx);
                    results.add(new JqArray(path));
                }
            }
        } else if (expr instanceof PipeNode) {
            PipeNode pipe = (PipeNode) expr;
            List<JqValue> leftOutputs = execute(pipe.left(), input, env);
            for (JqValue leftOut : leftOutputs) {
                tracePaths(pipe.right(), leftOut, env, currentPath, results);
            }
        } else if (expr instanceof FuncCallNode) {
            // For built-in functions, fall back to evaluating
            List<JqValue> values = execute(expr, input, env);
            for (JqValue val : values) {
                List<JqValue> path = findPath(input, val, currentPath);
                if (path != null) {
                    results.add(new JqArray(path));
                }
            }
        } else {
            List<JqValue> values = execute(expr, input, env);
            for (JqValue val : values) {
                List<JqValue> path = findPath(input, val, currentPath);
                if (path != null) {
                    results.add(new JqArray(path));
                }
            }
        }
    }

    private String htmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;");
    }

    private String uriEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String uriDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String shEscape(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String base64Encode(String s) {
        return java.util.Base64.getEncoder().encodeToString(s.getBytes());
    }

    private String base64Decode(String s) {
        return new String(java.util.Base64.getDecoder().decode(s));
    }

    private int toLength(JqValue v) {
        if (v instanceof JqString s) return s.length();
        if (v instanceof JqArray a) return a.size();
        if (v instanceof JqObject o) return o.size();
        throw new JqRuntimeException("Cannot get length of " + v.typeName());
    }

    private void collectRecursive(JqValue obj, String fieldName, List<JqValue> results, JqEnvironment env, int depth) {
        if (depth > JqEnvironment.MAX_RECURSION_DEPTH) {
            throw new JqRuntimeException("Recursion depth limit exceeded in recursive descent (max=" + JqEnvironment.MAX_RECURSION_DEPTH + ")");
        }
        // Add the current object to results (for .. without field name)
        if (fieldName == null) {
            env.checkOutputLimit();
            results.add(obj);
        }
        if (obj instanceof JqObject map) {
            for (Map.Entry<String, JqValue> entry : map.properties().entrySet()) {
                env.checkOutputLimit();
                if (fieldName != null && entry.getKey().equals(fieldName)) {
                    results.add(entry.getValue());
                }
                collectRecursive(entry.getValue(), fieldName, results, env, depth + 1);
            }
        } else if (obj instanceof JqArray arr) {
            for (JqValue item : arr.items()) {
                env.checkOutputLimit();
                collectRecursive(item, fieldName, results, env, depth + 1);
            }
        }
    }

    // ===== Path operations =====

    private List<JqValue> collectPaths(JqAstNode expr, JqValue input, JqEnvironment env) {
        // For simple field access like .foo, return ["foo"]
        // For complex expressions, evaluate and return paths
        List<JqValue> results = new ArrayList<>();
        collectPathsRecursive(expr, input, env, new ArrayList<>(), results);
        return results;
    }

    private void collectPathsRecursive(JqAstNode node, JqValue input, JqEnvironment env,
                                       List<JqValue> currentPath, List<JqValue> results) {
        if (node instanceof FieldAccessNode fan) {
            List<JqValue> path = new ArrayList<>(currentPath);
            path.add(JqString.of(fan.fieldName()));
            if (fan.hasExplicitObject()) {
                List<JqValue> objOutputs = execute(fan.object(), input, env);
                for (JqValue obj : objOutputs) {
                    JqValue val = getField(obj, fan.fieldName());
                    if (val != null) {
                        results.add(new JqArray(new ArrayList<>(path)));
                    }
                }
            } else {
                JqValue val = getField(input, fan.fieldName());
                if (val != null) {
                    results.add(new JqArray(new ArrayList<>(path)));
                }
            }
        } else if (node instanceof IndexAccessNode) {
            IndexAccessNode ian = (IndexAccessNode) node;
            List<JqValue> idxOutputs = execute(ian.index(), input, env);
            for (JqValue idx : idxOutputs) {
                List<JqValue> path = new ArrayList<>(currentPath);
                path.add(idx);
                JqValue val = getIndex(input, idx);
                if (val != null) {
                    results.add(new JqArray(new ArrayList<>(path)));
                }
            }
        } else if (node instanceof CommaNode commaNode) {
            collectPathsRecursive(commaNode.left(), input, env, currentPath, results);
            collectPathsRecursive(commaNode.right(), input, env, currentPath, results);
        } else if (node instanceof SliceNode sliceNode) {
            // For slice, we need to evaluate the slice expression to get indices
            List<JqValue> sliceResults = execute(sliceNode, input, env);
            for (JqValue sliceVal : sliceResults) {
                if (sliceVal instanceof JqArray arr) {
                    for (int i = 0; i < arr.size(); i++) {
                        List<JqValue> path = new ArrayList<>(currentPath);
                        path.add(JqNumber.of(i));
                        results.add(new JqArray(new ArrayList<>(path)));
                    }
                }
            }
        } else {
            results.add(new JqArray(new ArrayList<>(currentPath)));
        }
    }

    private JqValue setPath(JqValue obj, List<JqValue> path, JqValue value) {
        if (path.isEmpty()) return value;
        JqValue head = path.get(0);
        List<JqValue> tail = path.subList(1, path.size());

        if (head instanceof JqString key) {
            if (obj instanceof JqObject map) {
                JqValue child = map.get(key.value());
                JqValue newChild = setPath(child != null ? child : JqValue.NULL, tail, value);
                return map.put(key.value(), newChild);
            }
        } else if (head instanceof JqNumber num) {
            if (obj instanceof JqArray arr) {
                int idx = num.intValue();
                if (idx < 0) idx = arr.size() + idx;
                if (idx >= 0 && idx < arr.size()) {
                    JqValue child = arr.get(idx);
                    JqValue newChild = setPath(child, tail, value);
                    return arr.set(idx, newChild);
                } else if (idx == arr.size() && tail.isEmpty()) {
                    return arr.add(value);
                }
            }
        }
        return obj;
    }

    private JqValue deletePath(JqValue obj, List<JqValue> path, int depth) {
        if (depth >= path.size()) return obj;
        JqValue head = path.get(depth);

        if (head instanceof JqString key && obj instanceof JqObject map) {
            if (depth == path.size() - 1) {
                return map.remove(key.value());
            }
            JqValue child = map.get(key.value());
            if (child != null) {
                JqValue newChild = deletePath(child, path, depth + 1);
                return map.put(key.value(), newChild);
            }
        } else if (head instanceof JqNumber num && obj instanceof JqArray arr) {
            int idx = num.intValue();
            if (idx < 0) idx = arr.size() + idx;
            if (idx >= 0 && idx < arr.size()) {
                if (depth == path.size() - 1) {
                    return arr.remove(idx);
                }
                JqValue child = arr.get(idx);
                JqValue newChild = deletePath(child, path, depth + 1);
                return arr.set(idx, newChild);
            }
        }
        return obj;
    }

    private int comparePathElem(JqValue a, JqValue b) {
        if (a instanceof JqString && b instanceof JqString) {
            return ((JqString) a).value().compareTo(((JqString) b).value());
        }
        if (a instanceof JqNumber && b instanceof JqNumber) {
            return Integer.compare(((JqNumber) a).intValue(), ((JqNumber) b).intValue());
        }
        return a.toString().compareTo(b.toString());
    }

    // ===== Walk =====

    private JqValue walkApply(JqAstNode expr, JqValue input, JqEnvironment env, List<JqValue> outputs) {
        env.enterRecursion();
        try {
            if (input instanceof JqArray arr) {
                List<JqValue> newItems = new ArrayList<>();
                for (JqValue item : arr.items()) {
                    walkApply(expr, item, env, newItems);
                }
                JqValue rebuilt = new JqArray(newItems);
                List<JqValue> results = execute(expr, rebuilt, env);
                if (results.isEmpty()) {
                    outputs.add(rebuilt);
                } else {
                    outputs.addAll(results);
                }
            } else if (input instanceof JqObject obj) {
                Map<String, JqValue> newProps = new LinkedHashMap<>();
                for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                    List<JqValue> childOutputs = new ArrayList<>();
                    walkApply(expr, e.getValue(), env, childOutputs);
                    if (!childOutputs.isEmpty()) {
                        newProps.put(e.getKey(), childOutputs.get(0));
                    }
                }
                JqValue rebuilt = new JqObject(newProps);
                List<JqValue> results = execute(expr, rebuilt, env);
                if (results.isEmpty()) {
                    outputs.add(rebuilt);
                } else {
                    outputs.addAll(results);
                }
            } else {
                List<JqValue> results = execute(expr, input, env);
                if (results.isEmpty()) {
                    outputs.add(input);
                } else {
                    outputs.addAll(results);
                }
            }
        } finally {
            env.exitRecursion();
        }
        return JqValue.NULL;
    }

    // ===== Regex operations =====

    private java.util.regex.Pattern compileRegex(String pattern) {
        try {
            return java.util.regex.Pattern.compile(pattern);
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new JqRuntimeException("Invalid regex pattern: " + e.getMessage());
        }
    }

    private boolean regexTest(String input, String pattern) {
        return compileRegex(pattern).matcher(input).find();
    }

    private JqValue regexMatch(String input, String pattern) {
        java.util.regex.Matcher m = compileRegex(pattern).matcher(input);
        if (m.find()) {
            Map<String, JqValue> result = new LinkedHashMap<>();
            result.put("offset", JqNumber.of(m.start()));
            result.put("length", JqNumber.of(m.end() - m.start()));
            result.put("string", JqString.of(m.group()));
            result.put("captures", buildCaptureGroups(m));
            return new JqObject(result);
        }
        return JqValue.NULL;
    }

    private JqValue regexCapture(String input, String pattern) {
        java.util.regex.Matcher m = compileRegex(pattern).matcher(input);
        if (m.find()) {
            return buildCaptureGroups(m);
        }
        return new JqObject(java.util.Map.of());
    }

    private JqValue regexScan(String input, String pattern) {
        java.util.regex.Matcher m = compileRegex(pattern).matcher(input);
        List<JqValue> results = new ArrayList<>();
        while (m.find()) {
            if (m.groupCount() > 0) {
                Map<String, JqValue> capture = new LinkedHashMap<>();
                for (int i = 1; i <= m.groupCount(); i++) {
                    String name = m.start(i) >= 0 ? m.group(i) : null;
                    capture.put(String.valueOf(i - 1), name != null ? JqString.of(name) : JqValue.NULL);
                }
                results.add(new JqObject(capture));
            } else {
                results.add(JqString.of(m.group()));
            }
        }
        return new JqArray(results);
    }

    private JqArray buildCaptureGroups(java.util.regex.Matcher m) {
        List<JqValue> captures = new ArrayList<>();
        for (int i = 1; i <= m.groupCount(); i++) {
            Map<String, JqValue> cap = new LinkedHashMap<>();
            cap.put("offset", JqNumber.of(m.start(i)));
            cap.put("length", JqNumber.of(m.end(i) - m.start(i)));
            cap.put("string", JqString.of(m.group(i)));
            captures.add(new JqObject(cap));
        }
        return new JqArray(captures);
    }
}
