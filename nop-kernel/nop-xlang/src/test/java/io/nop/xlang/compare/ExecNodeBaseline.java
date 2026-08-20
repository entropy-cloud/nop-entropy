package io.nop.xlang.compare;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * exec/ 节点类基线四分区 + 排除清单（I3 Phase 1 定稿，矩阵机制的共享口径单一事实源）。
 *
 * <p>基线源 = live 包扫描 {@code nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/}
 * （文件级，见 {@code TestExecNodeBaselineFreshness}：新增未分类文件即红灯）。
 * 本类随 test-jar 发布，供 java 侧（nop-xlang-java 矩阵断言测试）与 truffle 侧（I6 同基线同口径）消费。
 *
 * <p>四分区（live 全部 138 文件逐一归属，无无主残留；I4 移交三边缘类裁定后
 * ReturnScopeValuesExecutable/ExecutableFunctionEvalAction 改判排除、LocationFunction 留 B 族转译）：
 * <ol>
 * <li>{@link Partition#A_FAMILY}——覆盖 A 五族（I3 转译范围）；</li>
 * <li>{@link Partition#RESIDUAL_MERGED}——数据面残余算子（I2 子集起步时设计 §三两行的尾部，裁定并入 I3）；</li>
 * <li>{@link Partition#I2_SUBSET}——I2 表达式子集已注册节点（含结构性载体）；</li>
 * <li>{@link Partition#B_FAMILY}——函数/闭包/控制流/输出/节点生成族（I4 范围，矩阵显式 pending 不算通过）；</li>
 * <li>{@link Partition#EXCLUDED}——抽象基类/接口/非节点辅助类（不作为节点注册对象，逐类理由见
 * {@link #EXCLUDED_REASONS}）。</li>
 * </ol>
 *
 * <p><b>per-backend 目标集口径（I4 Phase 1 定稿）</b>：java 侧 = {@link #javaTargetSet()}（全量非排除），
 * truffle 侧 = {@link #truffleRegisteredTarget()}（87 直至 I7）；两侧矩阵各自锚定本后端目标集，
 * {@link #registeredTarget()} 语义冻结为 CorpusCoverageA 白名单锚。
 *
 * <p>扫描粒度裁定（I3 Phase 1）：<b>文件级</b>。文件内嵌套具体类（如
 * {@code ObjFunctionExecutable$NoArgExecutable}、{@code VarFunctionExecutable$OneArgExecutable}）
 * 均为顶层类的同族特化变体（extends 顶层类），转译器 instanceof 分派与矩阵支持集按顶层类注册即覆盖
 * 嵌套变体；矩阵不单独枚举嵌套类。
 */
public final class ExecNodeBaseline {

    public enum Partition {
        A_FAMILY, RESIDUAL_MERGED, I2_SUBSET, B_FAMILY, EXCLUDED
    }

    /** 覆盖 A 五族（I3 范围，44 类）。 */
    public static final Set<String> A_SCOPE_CHAIN = setOf(
            "ScopeIdentifierExecutable", "GlobalVarExecutable", "ScopeAssignExecutable",
            "ScopeSelfAssignExecutable", "ScopeSelfIncExecutable", "ScopeSelfDecExecutable",
            "ReferenceIdentifierExecutable", "ReferenceAssignExecutable", "ReferenceSelfAssignExecutable",
            "ReferenceSelfIncExecutable", "ReferenceSelfDecExecutable", "RenewReferenceExecutable");

    public static final Set<String> A_TYPE_OP = setOf(
            "CastExecutable", "ConvertExecutable", "ConvertWithDefaultExecutable",
            "InstanceOfExecutable", "TypeOfExecutable");

    public static final Set<String> A_OBJ_COLLECTION = setOf(
            "NewObjectExecutable", "NewListExecutable", "NewMapExecutable",
            "GetPropertyExecutable", "GetterGetPropertyExecutable", "StaticGetterGetPropertyExecutable",
            "SetPropertyExecutable", "SetterSetPropertyExecutable",
            "GetAttrExecutable", "SetAttrExecutable",
            "ListItemExecutable", "MapItemExecutable", "MakePropertyExecutable");

    public static final Set<String> A_BINDING_GUARD_DEBUG = setOf(
            "BindVarExecutable", "ArrayBindingAssignExecutable", "ObjectBindingAssignExecutable",
            "InitRefSlotExecutable", "EnhanceRefSlotExecutable",
            "GuardNotEmptyExecutable", "DebugExecutable", "DebugIdentifierExecutable",
            "VarStatusExecutable");

    public static final Set<String> A_SLOT_WRITE = setOf(
            "SelfAssignExecutable", "SelfAssignAttrExecutable", "SelfAssignPropertyExecutable",
            "SelfIncExecutable", "SelfDecExecutable");

    /** 数据面残余算子族（设计 §三"字面量/算术逻辑比较"两行残余 + 兜底/已解析变体，裁定并入 I3，15 类）。 */
    public static final Set<String> RESIDUAL_MERGED = setOf(
            "CloneLiteralExecutable", "NegExecutable", "BitNotExecutable", "NullCoalesceExecutable",
            "BetweenOpExecutable", "AssertOpExecutable", "ConcatExecutable", "RangeExecutable",
            "PropInExecutable", "EqNullExecutable", "NeNullExecutable",
            "StrictEqNullExecutable", "StrictNeNullExecutable",
            "BinaryExecutable", "ResolvedObjFunctionExecutable");

    /** I2 表达式子集已注册节点（含结构性载体，28 类）。 */
    public static final Set<String> I2_SUBSET = setOf(
            "LiteralExecutable", "NullExecutable", "SlotIdentifierExecutable", "SlotAssignExecutable",
            "PlusExecutable", "MinusExecutable", "MultiplyExecutable", "DivideExecutable",
            "AndExecutable", "OrExecutable", "NotExecutable",
            "EqExecutable", "NeExecutable", "GtExecutable", "GeExecutable", "LtExecutable", "LeExecutable",
            "StrictEqExecutable", "StrictNeExecutable", "CompareOpExecutable",
            "ObjFunctionExecutable", "FunctionExecutable", "StaticFunctionExecutable",
            "GuardNotNullExecutable", "SeqExecutable", "BlockExecutable", "ReturnNullExecutable",
            "CallFuncExecutable");

    /** B 族：函数/闭包/控制流/输出/节点生成（I4 范围，33 类——三边缘类中 LocationFunction 裁定转译并入，ReturnScopeValuesExecutable/ExecutableFunctionEvalAction 裁定改判排除）。 */
    public static final Set<String> B_FAMILY = setOf(
            // 函数/闭包族
            "VarFunctionExecutable", "VarExecutableFunction", "LazyCompiledExecutableFunction",
            "FunctionalAdapterExecutable", "CallFuncWithClosureExecutable", "BuildFuncRefExecutable",
            "BuildClosureBodyExecutable", "LocationFunction",
            // 控制流族
            "IfExecutable", "SwitchExecutable", "ForExecutable", "ForInExecutable", "ForOfExecutable",
            "WhileExecutable", "DoWhileExecutable", "BreakExecutable", "ContinueExecutable",
            "ReturnExecutable", "TryExecutable", "ThrowErrorCodeExecutable", "ThrowExceptionExecutable",
            // 输出/节点生成族
            "OutputTextExecutable", "OutputValueExecutable", "OutputXmlAttrExecutable",
            "OutputXmlExtAttrsExecutable", "GenNodeExecutable", "GenNodeAttrExecutable",
            "GenXJsonExecutable", "CollectJsonExecutable", "CollectNodeExecutable",
            "CollectSqlExecutable", "CollectTextExecutable", "EscapeOutputExecutable");

    /** 排除清单：抽象基类/接口/非节点辅助类（18 类，逐类理由见 {@link #EXCLUDED_REASONS}）。 */
    public static final Set<String> EXCLUDED = setOf(
            "AbstractBinaryExecutable", "AbstractExecutable", "AbstractMultiExecutable",
            "AbstractObjFunctionExecutable", "AbstractPropertyExecutable", "AbstractSelfAssignExecutable",
            "ISeqExecutable", "IMacroFunction",
            "AssignIdentifier", "PropBinding", "ScopeValues", "ObjFunctionHandle",
            "ExecutableHelper", "MakeScopeEvalFunction", "XLangSemantics", "ExecutableFunction",
            "ReturnScopeValuesExecutable", "ExecutableFunctionEvalAction");

    /** 排除清单逐类理由（I2 三类标注法同源纪律：无"整类划为辅助"的粗粒度划分）。 */
    public static final Map<String, String> EXCLUDED_REASONS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("AbstractBinaryExecutable", "抽象基类：二元算子族的公共父类，无自身 execute 语义");
        m.put("AbstractExecutable", "抽象基类：全部节点的公共父类（newError/eval 等工具方法宿主）");
        m.put("AbstractMultiExecutable", "抽象基类：多元子表达式节点（ConcatExecutable）的父类");
        m.put("AbstractObjFunctionExecutable", "抽象基类：宿主方法反射分派族的父类");
        m.put("AbstractPropertyExecutable", "抽象基类：属性读写族的父类（getter/setter 解析与异常包装宿主）");
        m.put("AbstractSelfAssignExecutable", "抽象基类：复合赋值族的父类（varName/operator/expr 载体）");
        m.put("ISeqExecutable", "接口：Seq/Block 的树契约（getExprs/isBlockStatement），非节点");
        m.put("IMacroFunction", "接口：编译期宏函数契约，不参与运行期 Executable 树");
        m.put("AssignIdentifier", "非节点值对象：数组/对象解构绑定的元素描述符（slot/varName/useRef/initializer）");
        m.put("PropBinding", "非节点值对象：对象解构的属性绑定描述符（extends AssignIdentifier，仍非节点）");
        m.put("ScopeValues", "非节点值对象：宏 script 单元返回值载体（ReturnScopeValuesExecutable 的产物）");
        m.put("ObjFunctionHandle", "非节点辅助类：按 funcName 分立的反射分派 handle 缓存（XLangSemantics 共享）");
        m.put("ExecutableHelper", "非节点辅助类：静态工具（seq 拼接/ prepend / append），不进入树");
        m.put("MakeScopeEvalFunction", "非节点辅助类：xdef domain 编译包装器（implements IEvalFunction，包装已编译函数）");
        m.put("XLangSemantics", "非节点辅助类：I2 落地的共享语义 helper 基座（生成代码与解释器同一实现来源）");
        m.put("ExecutableFunction", "非节点函数对象：implements IEvalFunction，树中仅作为 LiteralExecutable 载荷出现；"
                + "其函数体编译/闭包捕获归 B 族（I4）");
        m.put("ReturnScopeValuesExecutable", "I4 边缘裁定改判排除：宏 script 单元的编译期执行产物"
                + "（MacroScriptTagCompiler 在 parse 阶段执行取值注册宏变量），不进入运行期编译单元树；"
                + "返回值 ScopeValues 载荷为 List<LocalVarDeclaration> AST 声明列表，生成源码无自包含表示");
        m.put("ExecutableFunctionEvalAction", "I4 边缘裁定改判排除：宿主 API 构造"
                + "（XLang.getTagAction 从 xlib tag 的 IFunctionModel 包装为 EvalAction），非前端树编译产物、"
                + "不作为节点出现在编译单元树中；implements IExecutableExpression 为宿主互通形态（委托内部"
                + " ExecutableFunction），函数体语义随函数族载荷处置（生成私有方法）承载");
        EXCLUDED_REASONS = Collections.unmodifiableMap(m);
    }

    private static final Map<String, Partition> PARTITION_OF;

    static {
        Map<String, Partition> m = new LinkedHashMap<>();
        for (String s : A_SCOPE_CHAIN)
            m.put(s, Partition.A_FAMILY);
        for (String s : A_TYPE_OP)
            m.put(s, Partition.A_FAMILY);
        for (String s : A_OBJ_COLLECTION)
            m.put(s, Partition.A_FAMILY);
        for (String s : A_BINDING_GUARD_DEBUG)
            m.put(s, Partition.A_FAMILY);
        for (String s : A_SLOT_WRITE)
            m.put(s, Partition.A_FAMILY);
        for (String s : RESIDUAL_MERGED)
            m.put(s, Partition.RESIDUAL_MERGED);
        for (String s : I2_SUBSET)
            m.put(s, Partition.I2_SUBSET);
        for (String s : B_FAMILY)
            m.put(s, Partition.B_FAMILY);
        for (String s : EXCLUDED)
            m.put(s, Partition.EXCLUDED);
        PARTITION_OF = Collections.unmodifiableMap(m);
    }

    private ExecNodeBaseline() {
    }

    public static Partition partitionOf(String simpleName) {
        Partition p = PARTITION_OF.get(simpleName);
        return p == null ? Partition.EXCLUDED : p;
    }

    /** 未归属具体类名（红灯注入判据：矩阵新鲜度测试据此对未知类名报 FAIL）。 */
    public static boolean isClassified(String simpleName) {
        return PARTITION_OF.containsKey(simpleName);
    }

    /** 覆盖 A 五族全量（44 类）。 */
    public static Set<String> aFamily() {
        return union(A_SCOPE_CHAIN, A_TYPE_OP, A_OBJ_COLLECTION, A_BINDING_GUARD_DEBUG, A_SLOT_WRITE);
    }

    /** I3 转译范围 = A 族 + 并入残余（59 类）。 */
    public static Set<String> i3Scope() {
        return union(aFamily(), RESIDUAL_MERGED);
    }

    /** 矩阵全绿目标集 = I2 子集 + I3 范围（87 类）。 */
    public static Set<String> registeredTarget() {
        return union(i3Scope(), I2_SUBSET);
    }

    /**
     * java 侧矩阵目标集（I4 per-backend 口径）：全部非排除且未改判排除的具体类
     * = {@link #registeredTarget()} ∪ {@link #bFamily()}（87 + 33 = 120 类）。
     * I4 闭环时 java 侧矩阵锚定本口径（支持集 ↔ 本集合双向 set 相等）。
     */
    public static Set<String> javaTargetSet() {
        return union(registeredTarget(), B_FAMILY);
    }

    /**
     * truffle 侧矩阵目标集（I4 per-backend 口径）：维持既有 87 口径（= {@link #registeredTarget()} 内容）
     * 直至 I7 闭环收敛到全量。truffle 侧矩阵锚定本 accessor（I4 Phase 1 行为中性切换）；
     * {@link #registeredTarget()} 的语义保持 = CorpusCoverageA 防越界白名单锚（不随 I4 扩量放宽）。
     */
    public static Set<String> truffleRegisteredTarget() {
        return union(i3Scope(), I2_SUBSET);
    }

    /** 矩阵显式 pending 集 = B 族（33 类，可观测、不算通过）。 */
    public static Set<String> bFamily() {
        return B_FAMILY;
    }

    public static Set<String> excluded() {
        return EXCLUDED;
    }

    public static Map<String, Partition> allPartitions() {
        return PARTITION_OF;
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> ret = new LinkedHashSet<>();
        for (Set<String> s : sets)
            ret.addAll(s);
        return Collections.unmodifiableSet(ret);
    }

    private static Set<String> setOf(String... names) {
        List<String> list = Arrays.asList(names);
        if (list.size() != new LinkedHashSet<>(list).size())
            throw new IllegalStateException("duplicate entry in baseline set");
        return Collections.unmodifiableSet(new LinkedHashSet<>(list));
    }
}
