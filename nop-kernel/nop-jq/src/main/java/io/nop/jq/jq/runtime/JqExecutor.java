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
                        if (val != null) outputs.add(val);
                    }
                } else {
                    JqValue val = getField(input, n.fieldName());
                    if (val != null) outputs.add(val);
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
                    if (!startVals.isEmpty()) start = toInt(startVals.get(0));
                }
                if (n.end() != null) {
                    List<JqValue> endVals = execute(n.end(), input, env);
                    if (!endVals.isEmpty()) end = toInt(endVals.get(0));
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
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitRecursiveDescent(RecursiveDescentNode n) {
                collectRecursive(input, n.fieldName(), outputs);
                return JqValue.NULL;
            }
            @Override public JqValue visitPipe(PipeNode n) {
                List<JqValue> leftOutputs = execute(n.left(), input, env);
                for (JqValue leftOut : leftOutputs) {
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
                        executeInto(n.catchExpr(), JqString.of(e.getMessage()), env, outputs);
                    }
                    // try without catch: silently ignore
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitLabel(LabelNode n) {
                throw new UnsupportedOperationException("label/break not yet implemented");
            }
            @Override public JqValue visitBreak(BreakNode n) {
                throw new UnsupportedOperationException("label/break not yet implemented");
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
                throw new UnsupportedOperationException("foreach not yet implemented");
            }
            @Override public JqValue visitLimit(LimitNode n) {
                List<JqValue> countVals = execute(n.count(), input, env);
                if (countVals.isEmpty()) return JqValue.NULL;
                int count = toInt(countVals.get(0));
                List<JqValue> results = execute(n.expr(), input, env);
                int limit = Math.min(count, results.size());
                for (int i = 0; i < limit; i++) {
                    outputs.add(results.get(i));
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
                    default:
                        throw new JqRuntimeException("Unknown function: " + n.name());
                }
                return JqValue.NULL;
            }
            @Override public JqValue visitFuncDef(FuncDefNode n) {
                env.defineFunction(n.name(), n);
                return JqValue.NULL;
            }
            @Override public JqValue visitFormat(FormatNode n) {
                throw new UnsupportedOperationException("Format not yet implemented: @" + n.format());
            }
        });
    }

    // Helper methods

    private JqValue getField(JqValue obj, String name) {
        if (obj instanceof JqObject map) {
            JqValue val = map.get(name);
            return val != null ? val : JqValue.NULL;
        }
        return null;
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
            return switch (op) {
                case ADD -> JqNumber.of(ln.doubleValue() + rn.doubleValue());
                case SUB -> JqNumber.of(ln.doubleValue() - rn.doubleValue());
                case MUL -> JqNumber.of(ln.doubleValue() * rn.doubleValue());
                case DIV -> JqNumber.of(ln.doubleValue() / rn.doubleValue());
                case MOD -> JqNumber.of(ln.doubleValue() % rn.doubleValue());
            };
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

    private int toInt(JqValue v) {
        if (v instanceof JqNumber n) return n.intValue();
        throw new JqRuntimeException("Cannot convert " + v.typeName() + " to int");
    }

    private int toLength(JqValue v) {
        if (v instanceof JqString s) return s.length();
        if (v instanceof JqArray a) return a.size();
        if (v instanceof JqObject o) return o.size();
        throw new JqRuntimeException("Cannot get length of " + v.typeName());
    }

    private void collectRecursive(JqValue obj, String fieldName, List<JqValue> results) {
        if (obj instanceof JqObject map) {
            for (Map.Entry<String, JqValue> entry : map.properties().entrySet()) {
                if (fieldName == null || entry.getKey().equals(fieldName)) {
                    results.add(entry.getValue());
                }
                collectRecursive(entry.getValue(), fieldName, results);
            }
        } else if (obj instanceof JqArray arr) {
            for (JqValue item : arr.items()) {
                collectRecursive(item, fieldName, results);
            }
        }
    }
}
