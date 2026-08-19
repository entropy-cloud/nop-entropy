/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.translator;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.AbstractBinaryExecutable;
import io.nop.xlang.exec.AbstractObjFunctionExecutable;
import io.nop.xlang.exec.AndExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.DivideExecutable;
import io.nop.xlang.exec.EqExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GeExecutable;
import io.nop.xlang.exec.GtExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.LeExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.LtExecutable;
import io.nop.xlang.exec.MinusExecutable;
import io.nop.xlang.exec.MultiplyExecutable;
import io.nop.xlang.exec.NeExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjFunctionExecutable;
import io.nop.xlang.exec.OrExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StrictEqExecutable;
import io.nop.xlang.exec.StrictNeExecutable;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.java.gen.EvalMethodConvention;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_LOCATION;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;

/**
 * Executable 树到 Java 源码的纯函数转译器（设计 xlang-java 01 §二/§三/§四/§七）。
 *
 * <p>子集（与 I1 corpus v1 对齐）：字面量 / slot 标识符 / 算术 / 逻辑 / 比较 / 简单方法调用
 * （= 宿主方法反射分派族：ObjFunctionExecutable / StaticFunctionExecutable /
 * FunctionExecutable 系，不含函数字面量、闭包捕获、CallFunc 族局部函数调用）+ 结构性载体
 * （CallFunc 程序入口 / Block / Seq / SlotAssign / ReturnNull / GuardNotNull / Null）。
 *
 * <p>硬保证：
 * <ul>
 * <li>fail-fast——树中出现子集外节点即转译失败（报节点类名 + SourceLocation），
 * 禁止部分生成与"剩余解释"混合产物；</li>
 * <li>SourceLocation 保真——可抛错点内嵌静态 SourceLocation 常量（转译期从节点固化），
 * 抛 {@code NopException} 时携带对应常量；</li>
 * <li>语义一致——语义敏感操作统一调用 {@link XLangSemantics} 共享 helper，
 * 禁止为生成代码重写语义等价实现；</li>
 * <li>EvalMethod 约定——生成入口方法 static + 首参 {@code IEvalScope $scope}。</li>
 * </ul>
 */
public final class ExecToJavaTranslator {

    public GeneratedJavaSource translate(String resourcePath, IExecutableExpression tree) {
        Guard.notEmpty(resourcePath, "resourcePath");
        Guard.notNull(tree, "tree");
        GenContext ctx = new GenContext(resourcePath);
        if (tree instanceof CallFuncExecutable) {
            translateProgramEntry(ctx, (CallFuncExecutable) tree);
        } else {
            translatePureExpression(ctx, tree);
        }
        String className = EvalMethodConvention.GENERATED_PACKAGE + '.'
                + EvalMethodConvention.generatedClassName(resourcePath);
        return new GeneratedJavaSource(resourcePath, className,
                ctx.buildClass(EvalMethodConvention.generatedClassName(resourcePath)));
    }

    // ------------------------------------------------------------------
    // 编译单元入口形态
    // ------------------------------------------------------------------

    /**
     * 程序入口模式：树根为 CallFuncExecutable 程序入口包装（编译前端对 script 单元的固有产物，
     * slotNames 承载帧布局），入口包装翻译为生成方法本体（与其 EvalMethod 生成方法约定同构），
     * 与"用户级 CallFunc 族局部函数调用被子集排除"不冲突。
     */
    private void translateProgramEntry(GenContext ctx, CallFuncExecutable entry) {
        IExecutableExpression[] argExprs = entry.getArgExprs();
        if (argExprs != null && argExprs.length > 0)
            throw unsupported(entry, "program entry with declared arguments");
        String[] slotNames = entry.getSlotNames() == null ? new String[0] : entry.getSlotNames();
        ctx.slotCount = slotNames.length;
        for (int i = 0; i < slotNames.length; i++) {
            ctx.line("Object $v" + i + " = null; // slot " + i + ": " + slotNames[i]);
        }
        emitRootValue(ctx, entry.getBodyExpr());
    }

    /**
     * 纯表达式模式：树根为任意子集表达式（无入口帧），单表达式返回。
     */
    private void translatePureExpression(GenContext ctx, IExecutableExpression tree) {
        emitRootValue(ctx, tree);
    }

    private void emitRootValue(GenContext ctx, IExecutableExpression body) {
        if (body == null) {
            ctx.line("return null;");
            return;
        }
        if (body instanceof ISeqExecutable) {
            IExecutableExpression[] exprs = ((ISeqExecutable) body).getExprs();
            boolean block = ((ISeqExecutable) body).isBlockStatement();
            if (exprs.length == 0) {
                ctx.line("return null;");
                return;
            }
            int last = block ? exprs.length : exprs.length - 1;
            for (int i = 0; i < last; i++) {
                genStatement(ctx, exprs[i]);
            }
            if (block) {
                ctx.line("return null;");
            } else {
                ctx.line("return " + genExpr(ctx, exprs[exprs.length - 1]) + ";");
            }
        } else {
            ctx.line("return " + genExpr(ctx, body) + ";");
        }
    }

    // ------------------------------------------------------------------
    // 语句与表达式发射
    // ------------------------------------------------------------------

    private void genStatement(GenContext ctx, IExecutableExpression node) {
        if (node instanceof ISeqExecutable) {
            for (IExecutableExpression child : ((ISeqExecutable) node).getExprs()) {
                genStatement(ctx, child);
            }
            return;
        }
        if (node instanceof ReturnNullExecutable) {
            genStatement(ctx, ((ReturnNullExecutable) node).getExecutable());
            return;
        }
        if (node instanceof SlotAssignExecutable) {
            genExpr(ctx, node);
            return;
        }
        // 一般表达式在语句位置：求值（含副作用）后弃值
        String ref = genExpr(ctx, node);
        if (isInvocationRef(ref)) {
            ctx.line(ref + ";");
        }
    }

    private boolean isInvocationRef(String ref) {
        return ref.startsWith("XLangSemantics.") || ref.startsWith("io.nop.");
    }

    private String genExpr(GenContext ctx, IExecutableExpression node) {
        // ---- 字面量 / null ----
        if (node instanceof LiteralExecutable) {
            return literal(ctx, (LiteralExecutable) node);
        }
        if (node instanceof NullExecutable) {
            return "null";
        }

        // ---- slot 读写 ----
        if (node instanceof SlotIdentifierExecutable) {
            SlotIdentifierExecutable slot = (SlotIdentifierExecutable) node;
            checkSlot(ctx, node, slot.getSlot());
            return "$v" + slot.getSlot();
        }
        if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            checkSlot(ctx, node, assign.getSlot());
            String value = genExpr(ctx, assign.getExpr());
            ctx.line("$v" + assign.getSlot() + " = " + value + ";");
            return "$v" + assign.getSlot();
        }

        // ---- 算术（Plus 完整语义 = String 拼接 + 数值，统一共享 helper） ----
        if (node instanceof PlusExecutable) {
            return invoke2(ctx, "plus", (AbstractBinaryExecutable) node);
        }
        if (node instanceof MinusExecutable) {
            return invoke2(ctx, "minus", (AbstractBinaryExecutable) node);
        }
        if (node instanceof MultiplyExecutable) {
            return invoke2(ctx, "multiply", (AbstractBinaryExecutable) node);
        }
        if (node instanceof DivideExecutable) {
            return invoke2(ctx, "divide", (AbstractBinaryExecutable) node);
        }

        // ---- 比较（宽松与 Strict 变体 live 同实现，均走 xlangEq 族共享 helper） ----
        if (node instanceof EqExecutable || node instanceof StrictEqExecutable) {
            return invoke2(ctx, "eq", (AbstractBinaryExecutable) node);
        }
        if (node instanceof NeExecutable || node instanceof StrictNeExecutable) {
            return invoke2(ctx, "ne", (AbstractBinaryExecutable) node);
        }
        if (node instanceof GtExecutable) {
            return invoke2(ctx, "gt", (AbstractBinaryExecutable) node);
        }
        if (node instanceof GeExecutable) {
            return invoke2(ctx, "ge", (AbstractBinaryExecutable) node);
        }
        if (node instanceof LtExecutable) {
            return invoke2(ctx, "lt", (AbstractBinaryExecutable) node);
        }
        if (node instanceof LeExecutable) {
            return invoke2(ctx, "le", (AbstractBinaryExecutable) node);
        }

        // ---- 逻辑（短路语义以 if 块保真：右侧仅在需要时求值） ----
        if (node instanceof AndExecutable) {
            return genShortCircuit(ctx, (AbstractBinaryExecutable) node, true);
        }
        if (node instanceof OrExecutable) {
            return genShortCircuit(ctx, (AbstractBinaryExecutable) node, false);
        }
        if (node instanceof NotExecutable) {
            String expr = genExpr(ctx, ((NotExecutable) node).getExpr());
            return "!XLangSemantics.truthy(" + expr + ")";
        }

        // ---- 过滤比较谓词（FilterOp 枚举统一谓词源，直引枚举常量） ----
        if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            String left = genExpr(ctx, cmp.getLeft());
            String right = genExpr(ctx, cmp.getRight());
            return "io.nop.core.model.query.FilterOp." + cmp.getFilterOp().name()
                    + ".getBiPredicate().test(" + left + ", " + right + ")";
        }

        // ---- 简单方法调用：宿主方法反射分派族 ----
        if (node instanceof ObjFunctionExecutable) {
            return genObjFunction(ctx, (ObjFunctionExecutable) node);
        }
        if (node instanceof FunctionExecutable) {
            return genGlobalFunction(ctx, (FunctionExecutable) node);
        }
        if (node instanceof StaticFunctionExecutable) {
            return genStaticFunction(ctx, (StaticFunctionExecutable) node);
        }

        // ---- null 守卫 ----
        if (node instanceof GuardNotNullExecutable) {
            GuardNotNullExecutable guard = (GuardNotNullExecutable) node;
            String expr = genExpr(ctx, guard.getExpr());
            return "XLangSemantics.guardNotNull(" + ctx.locRef(guard) + ", " + displayOf(guard)
                    + ", " + expr + ")";
        }

        // ---- 序列（表达式位置） ----
        if (node instanceof ISeqExecutable) {
            return genSeqAsExpr(ctx, (ISeqExecutable) node);
        }
        if (node instanceof ReturnNullExecutable) {
            genStatement(ctx, ((ReturnNullExecutable) node).getExecutable());
            return "null";
        }

        // CallFunc 族在非根位置 = 局部函数调用，子集排除（fail-fast）
        throw unsupported(node, null);
    }

    private String genSeqAsExpr(GenContext ctx, ISeqExecutable seq) {
        IExecutableExpression[] exprs = seq.getExprs();
        if (seq.isBlockStatement()) {
            for (IExecutableExpression child : exprs) {
                genStatement(ctx, child);
            }
            return "null";
        }
        if (exprs.length == 0)
            return "null";
        for (int i = 0, n = exprs.length - 1; i < n; i++) {
            genStatement(ctx, exprs[i]);
        }
        String last = genExpr(ctx, exprs[exprs.length - 1]);
        if (isInvocationRef(last)) {
            String temp = ctx.temp();
            ctx.line("Object " + temp + " = " + last + ";");
            return temp;
        }
        return last;
    }

    private String invoke2(GenContext ctx, String helper, AbstractBinaryExecutable node) {
        String left = genExpr(ctx, node.getLeft());
        String right = genExpr(ctx, node.getRight());
        return "XLangSemantics." + helper + "(" + left + ", " + right + ")";
    }

    /**
     * And/Or 短路：左侧求值后按真值决定右侧是否求值（右侧的副作用语句收在 if 分支内）。
     * And: truthy(left) ? right : left；Or: !truthy(left) ? right : left。
     */
    private String genShortCircuit(GenContext ctx, AbstractBinaryExecutable node, boolean and) {
        String left = hoist(ctx, genExpr(ctx, node.getLeft()));
        String temp = ctx.temp();
        ctx.line("Object " + temp + ";");
        ctx.line("if (" + (and ? "" : "!") + "XLangSemantics.truthy(" + left + ")) {");
        ctx.indent();
        String right = genExpr(ctx, node.getRight());
        ctx.line(temp + " = " + right + ";");
        ctx.unindent();
        ctx.line("} else {");
        ctx.indent();
        ctx.line(temp + " = " + left + ";");
        ctx.unindent();
        ctx.line("}");
        return temp;
    }

    /**
     * 调用形态的引用在会被多次使用或进入条件分支前提升为临时变量，保证只求值一次。
     */
    private String hoist(GenContext ctx, String ref) {
        if (!isInvocationRef(ref))
            return ref;
        String temp = ctx.temp();
        ctx.line("Object " + temp + " = " + ref + ";");
        return temp;
    }

    /**
     * 实例方法分派（ObjFunctionExecutable 族）：接收者先求值；null 时短路返回 null
     * （右侧实参不求值，与解释器逐分支求值顺序一致）；否则实参求值后经共享 helper 分派调用。
     */
    private String genObjFunction(GenContext ctx, ObjFunctionExecutable node) {
        AbstractObjFunctionExecutable fn = (AbstractObjFunctionExecutable) node;
        String obj = hoist(ctx, genExpr(ctx, fn.getObjExpr()));
        String temp = ctx.temp();
        ctx.line("Object " + temp + ";");
        ctx.line("if (" + obj + " == null) {");
        ctx.indent();
        ctx.line(temp + " = null;");
        ctx.unindent();
        ctx.line("} else {");
        ctx.indent();
        String args = genArgs(ctx, fn.getArgs());
        ctx.line(temp + " = XLangSemantics.invokeObjMethod(" + ctx.locRef(node) + ", "
                + displayOf(node) + ", " + obj + ", \"" + escape(fn.getFuncName())
                + "\", new Object[]{" + args + "}, $scope);");
        ctx.unindent();
        ctx.line("}");
        return temp;
    }

    /**
     * 注册全局函数分派（FunctionExecutable 族）：运行时经同一全局注册表解析后统一调用。
     */
    private String genGlobalFunction(GenContext ctx, FunctionExecutable node) {
        String args = genArgs(ctx, node.getArgs());
        return "XLangSemantics.invokeGlobalFunction(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", \"" + escape(node.getFuncName()) + "\", new Object[]{" + args + "}, $scope)";
    }

    /**
     * 类静态方法分派（StaticFunctionExecutable 族）：按 className/funcName 运行时解析
     * 方法集合后统一调用。
     */
    private String genStaticFunction(GenContext ctx, StaticFunctionExecutable node) {
        String args = genArgs(ctx, node.getArgExprs());
        return "XLangSemantics.invokeStaticMethodResolved(" + ctx.locRef(node) + ", " + displayOf(node)
                + ", \"" + escape(node.getClassName()) + "\", \"" + escape(node.getFuncName())
                + "\", " + node.isOptional() + ", new Object[]{" + args + "}, $scope)";
    }

    private String genArgs(GenContext ctx, IExecutableExpression[] argExprs) {
        if (argExprs == null || argExprs.length == 0)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argExprs.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(genExpr(ctx, argExprs[i]));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 字面量 / 位置常量 / fail-fast
    // ------------------------------------------------------------------

    private String literal(GenContext ctx, LiteralExecutable node) {
        Object value = node.getValue();
        if (value == null)
            return "null";
        if (value instanceof String)
            return "\"" + escape((String) value) + "\"";
        if (value instanceof Boolean)
            return ((Boolean) value) ? "Boolean.TRUE" : "Boolean.FALSE";
        if (value instanceof Integer)
            return "Integer.valueOf(" + value + ")";
        if (value instanceof Long)
            return "Long.valueOf(" + value + "L)";
        if (value instanceof Double) {
            double d = (Double) value;
            if (Double.isNaN(d))
                return "Double.NaN";
            if (d == Double.POSITIVE_INFINITY)
                return "Double.POSITIVE_INFINITY";
            if (d == Double.NEGATIVE_INFINITY)
                return "Double.NEGATIVE_INFINITY";
            return "Double.valueOf(" + d + ")";
        }
        if (value instanceof Float)
            return "Float.valueOf(" + value + "F)";
        if (value instanceof BigDecimal)
            return "new java.math.BigDecimal(\"" + value + "\")";
        if (value instanceof BigInteger)
            return "new java.math.BigInteger(\"" + value + "\")";
        if (value instanceof Character) {
            return "Character.valueOf('" + escapeChar((Character) value) + "')";
        }
        if (value instanceof Enum) {
            Enum<?> e = (Enum<?>) value;
            return e.getDeclaringClass().getName() + "." + e.name();
        }
        throw unsupported(ctx, node, "unsupported literal type: " + value.getClass().getName());
    }

    private void checkSlot(GenContext ctx, IExecutableExpression node, int slot) {
        if (slot < 0 || slot >= ctx.slotCount)
            throw unsupported(ctx, node, "slot read/write outside program entry frame: slot=" + slot);
    }

    private NopEvalException unsupported(IExecutableExpression node, String detail) {
        return unsupported(null, node, detail);
    }

    private NopEvalException unsupported(GenContext ctx, IExecutableExpression node, String detail) {
        SourceLocation loc = node.getLocation();
        String className = node.getClass().getName();
        NopEvalException err = new NopEvalException(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE, null);
        err.param(ARG_CLASS_NAME, className).param(ARG_LOCATION, String.valueOf(loc));
        if (detail != null)
            err.param("detail", detail);
        if (loc != null)
            err.loc(loc);
        return err;
    }

    private static String displayOf(IExecutableExpression node) {
        StringBuilder sb = new StringBuilder();
        node.display(sb);
        return "\"" + escape(sb.toString()) + "\"";
    }

    static String escape(String str) {
        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0, n = str.length(); i < n; i++) {
            escapeChar(str.charAt(i), sb);
        }
        return sb.toString();
    }

    private static String escapeChar(char c) {
        StringBuilder sb = new StringBuilder();
        escapeChar(c, sb);
        return sb.toString();
    }

    private static void escapeChar(char c, StringBuilder sb) {
        switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\'':
                sb.append("\\'");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            default:
                if (c < 0x20 || c > 0x7e) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
        }
    }

    // ------------------------------------------------------------------
    // 生成上下文
    // ------------------------------------------------------------------

    private static final class GenContext {
        final String resourcePath;
        final List<String> lines = new ArrayList<>();
        final Map<String, String> locConstants = new LinkedHashMap<>();
        final List<SourceLocation> locDecls = new ArrayList<>();
        int tempCounter;
        int slotCount;
        int indent = 1;

        GenContext(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        void line(String text) {
            StringBuilder sb = new StringBuilder(text.length() + 8);
            for (int i = 0; i < indent; i++)
                sb.append("    ");
            sb.append(text);
            lines.add(sb.toString());
        }

        void indent() {
            indent++;
        }

        void unindent() {
            indent--;
        }

        String temp() {
            return "$t" + (tempCounter++);
        }

        /**
         * 可抛错点的 SourceLocation 常量引用（按 path:line:col 去重注册）。
         */
        String locRef(IExecutableExpression node) {
            SourceLocation loc = node.getLocation();
            if (loc == null)
                return "null";
            String key = loc.getPath() + ":" + loc.getLine() + ":" + loc.getCol();
            String name = locConstants.get(key);
            if (name == null) {
                name = "LOC_" + locConstants.size();
                locConstants.put(key, name);
                locDecls.add(loc);
            }
            return name;
        }

        String buildClass(String className) {
            StringBuilder sb = new StringBuilder(1024);
            sb.append("// source: ").append(resourcePath).append('\n');
            sb.append("package ").append(EvalMethodConvention.GENERATED_PACKAGE).append(";\n\n");
            sb.append("import ").append(SourceLocation.class.getName()).append(";\n");
            sb.append("import ").append(IEvalScope.class.getName()).append(";\n");
            sb.append("import ").append(XLangSemantics.class.getName()).append(";\n\n");
            sb.append("public final class ").append(className).append(" {\n");
            for (int i = 0, n = locDecls.size(); i < n; i++) {
                SourceLocation loc = locDecls.get(i);
                String name = "LOC_" + i;
                sb.append("    private static final SourceLocation ").append(name)
                        .append(" = SourceLocation.fromLine(\"").append(escape(loc.getPath()))
                        .append("\", ").append(loc.getLine()).append(", ").append(loc.getCol())
                        .append(");\n");
            }
            if (!locDecls.isEmpty())
                sb.append('\n');
            sb.append("    public static Object ").append(EvalMethodConvention.ENTRY_METHOD_NAME)
                    .append("(").append(IEvalScope.class.getSimpleName()).append(' ')
                    .append(EvalMethodConvention.SCOPE_PARAM).append(") {\n");
            for (String text : lines)
                sb.append("    ").append(text).append('\n');
            sb.append("    }\n");
            sb.append("}\n");
            return sb.toString();
        }
    }
}
