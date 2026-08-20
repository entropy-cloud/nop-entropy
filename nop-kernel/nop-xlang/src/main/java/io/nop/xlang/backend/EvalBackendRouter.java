/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_DEPLOYMENT_FORM;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_FORCE_INTERPRETER;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED;

/**
 * 统一后端选择决策树（全仓唯一，设计 xlang-execution 01 §三伪代码的行为规格落地）。
 *
 * <p>判定输入四项：(1) 产生时机 = 扫描清单成员资格（经静态后端 SPI 查询，清单产物归构建集成）；
 * (2) 后端可用性 = 注册表查询；(3) 部署形态 = 配置式标记（native-image 下 truffle 结构性不适用，
 * 无 GraalVM import）；(4) 配置开关 = {@code nop.xlang.execution.*}。
 *
 * <p>降级链单跳：java→解释器、truffle→解释器，无跨跳（静态分支永不咨询动态后端）。
 * 每次降级记 WARN + 指标（{@link EvalBackendObservation} 命名契约）；force-interpreter 诊断模式
 * 与 native 结构性排除为静默（不记降级事件）；后端未注册（classpath 缺席）为安静缺省。
 *
 * <p>第三分支（动态路径单元级翻译失败）：动态后端经 {@link EvalBackendDynamicOutcome#isFallback()}
 * 返回单元级降级标记 → 本路由改走解释器并记观测；真实求值错误由后端原样重抛（fail-fast 保持）。
 */
public class EvalBackendRouter {

    private static final Logger LOG = LoggerFactory.getLogger(EvalBackendRouter.class);

    /** native image 的标准标记系统属性（字符串探测，不引入 GraalVM 类型依赖） */
    static final String NATIVE_IMAGE_KIND_PROPERTY = "org.graalvm.nativeimage.kind";

    static final String DEPLOYMENT_JVM = "jvm";

    static final String DEPLOYMENT_NATIVE_IMAGE = "native-image";

    static final String DEPLOYMENT_AUTO = "auto";

    private static final int DECISION_RING_MAX = 128;

    private static final EvalBackendRouter _instance = new EvalBackendRouter();

    public static EvalBackendRouter instance() {
        return _instance;
    }

    private final EvalBackendRegistry registry = EvalBackendRegistry.instance();

    private final Deque<EvalBackendDecision> recentDecisions = new ArrayDeque<>();

    private EvalBackendRouter() {
    }

    /**
     * 路由是否激活：注册表非空。注册表空（后端不在 classpath）时所有出口行为与无路由现状一致
     * （{@code XLang.execute} fast-path 直通全局执行器）。
     */
    public boolean isActive() {
        return !registry.isEmpty();
    }

    /**
     * 运行时求值期统一裁决入口（由 {@code XLang.execute} choke point 调用；全部"运行时字符串→
     * Executable 树"出口的求值经该 choke point 流入）。
     *
     * <p>加载期已裁定执行体直通（I10，D3 组合语义）：{@link EvalStaticBoundExecutable} 直通绑定体
     * （无重复指纹计算、无降级观测）；{@link EvalStaticDegradedExecutable} 直通解释器（观测已在
     * 加载期完成）。未标记树走决策树原语义。
     */
    public Object executeAdjudicated(IExecutableExpression expr, EvalRuntime rt) {
        if (expr instanceof EvalStaticBoundExecutable) {
            return executeBoundUnit((EvalStaticBoundExecutable) expr, rt);
        }
        if (expr instanceof EvalStaticDegradedExecutable) {
            return executeDegradedUnit((EvalStaticDegradedExecutable) expr, rt);
        }

        String resourcePath = resourcePathOf(expr);
        EvalBackendDecision decision = decide(resourcePath, expr);
        Object result;

        switch (decision.getKind()) {
            case INTERPRETER:
                result = EvalExprProvider.getGlobalExecutor().execute(expr, rt);
                decision.setArtifact(expr);
                break;
            case STATIC:
                result = decision.getStaticBinding().execute(rt);
                decision.setArtifact(decision.getStaticBinding().getBindingArtifact());
                break;
            case DYNAMIC:
                IEvalDynamicBackend dynamicBackend = (IEvalDynamicBackend) decision.getBackend();
                EvalBackendDynamicOutcome outcome = dynamicBackend.executeDynamic(
                        new EvalBackendDynamicRequest(expr, rt, resourcePath));
                if (outcome.isFallback()) {
                    String reason = outcome.getFallbackReason();
                    EvalBackendObservation.onDegradation(decision.getBackendId(), reason,
                            sourceKeyOf(resourcePath, expr), expr.getLocation());
                    decision = EvalBackendDecision.interpreter(reason, true);
                    result = EvalExprProvider.getGlobalExecutor().execute(expr, rt);
                    decision.setArtifact(expr);
                } else {
                    result = outcome.getValue();
                    decision.setArtifact(outcome.getArtifact());
                }
                break;
            default:
                throw new IllegalStateException("unreachable route kind: " + decision.getKind());
        }

        decision.setSourceKey(sourceKeyOf(resourcePath, expr));
        recordDecision(decision);
        return result;
    }

    /**
     * 模型加载期绑定（I10，设计 java §五绑定决策树的行为规格落地；由 {@code XLang.parseXpl}
     * 编译单元装载完成后调用）。逐分支：
     * <ul>
     * <li>注册表空 / force-interpreter / java 开关关 / 清单外 / resourcePath 不可得 → 返回原树
     * （静默；执行期按既有决策树裁决——开关关/force 走 I9 分支语义，清单外走动态路径不记降级）；</li>
     * <li>清单成员 + 后端不可用 → 降级观测（reason=unavailable）+ {@link EvalStaticDegradedExecutable}；</li>
     * <li>清单成员 + 绑定命中 → {@link EvalStaticBoundExecutable}（生成类优先，随缓存条目复用）；</li>
     * <li>清单成员 + 绑定缺失（binder 返回 null）→ {@link EvalStaticDegradedExecutable}
     * （分级观测由生产 binder 在返回 null 前自记——D5 观测记录方裁定；此处不补记，防双记）。</li>
     * </ul>
     * 返回原树实例（未包装）时调用方原样缓存。
     */
    public IExecutableExpression bindLoadedUnit(String resourcePath, IExecutableExpression tree) {
        if (tree == null || resourcePath == null)
            return tree;
        IEvalStaticBackend staticBackend = registry.findStaticBackend();
        if (staticBackend == null)
            return tree;
        // §五条件1：java 后端未启用（配置开关/强制解释器模式）——静默返回原树，执行期按 I9 分支裁决
        if (isForceInterpreter() || !isStaticSlotEnabled())
            return tree;
        // §五条件2：清单外资源——不适用 java 绑定，动态路径（不记降级事件）
        if (!staticBackend.isStaticCandidate(resourcePath))
            return tree;
        if (!staticBackend.isAvailable()) {
            EvalBackendObservation.onDegradation(staticBackend.getBackendId(),
                    EvalBackendObservation.REASON_UNAVAILABLE + ":" + staticBackend.getUnavailableReason(),
                    resourcePath, tree.getLocation());
            return new EvalStaticDegradedExecutable(tree, EvalBackendObservation.REASON_UNAVAILABLE);
        }
        IEvalStaticBinding binding = staticBackend.findStaticBinding(resourcePath, tree);
        if (binding == null) {
            // 清单内资源应有生成类而缺失：生产 binder 已按 D5 分级自记观测（stale/稳态）；
            // 此处包装降级标记，执行期直通解释器（不再重复观测/咨询/指纹计算）
            return new EvalStaticDegradedExecutable(tree,
                    "load-time-" + EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        }
        return new EvalStaticBoundExecutable(tree, binding, staticBackend.getBackendId());
    }

    private Object executeBoundUnit(EvalStaticBoundExecutable bound, EvalRuntime rt) {
        Object result = bound.getBinding().execute(rt);
        EvalBackendDecision decision = EvalBackendDecision.boundUnit(bound.getBackendId(), bound.getBinding());
        decision.setArtifact(bound.getBinding().getBindingArtifact());
        decision.setSourceKey(sourceKeyOf(resourcePathOf(bound), bound));
        recordDecision(decision);
        return result;
    }

    private Object executeDegradedUnit(EvalStaticDegradedExecutable degraded, EvalRuntime rt) {
        Object result = degraded.execute(EvalExprProvider.getGlobalExecutor(), rt);
        EvalBackendDecision decision = EvalBackendDecision.interpreter(degraded.getReason(), true);
        decision.setArtifact(degraded.getSourceTree());
        decision.setSourceKey(sourceKeyOf(resourcePathOf(degraded), degraded));
        recordDecision(decision);
        return result;
    }

    /**
     * 纯裁决（不执行）：决策树四输入 → 裁决结果。供测试与静态路径 java 绑定集成消费。
     */
    public EvalBackendDecision decide(String resourcePath, IExecutableExpression tree) {
        // 输入4：强制解释器诊断模式——全路由短路，静默（显式隔离意图，不记降级事件）
        if (isForceInterpreter())
            return EvalBackendDecision.interpreter("force-interpreter", false);

        // 输入1：产生时机 = 扫描清单成员资格（静态后端 SPI 查询；清单空=全动态）
        IEvalStaticBackend staticBackend = registry.findStaticBackend();
        if (staticBackend != null && resourcePath != null && staticBackend.isStaticCandidate(resourcePath)) {
            // 静态路径：单跳 java→解释器，永不咨询动态后端（判据互斥，无跨跳）
            if (!isStaticSlotEnabled()) {
                EvalBackendObservation.onDegradation(staticBackend.getBackendId(),
                        EvalBackendObservation.REASON_CONFIG_DISABLED, resourcePath,
                        tree == null ? null : tree.getLocation());
                return EvalBackendDecision.interpreter(EvalBackendObservation.REASON_CONFIG_DISABLED, true);
            }
            if (!staticBackend.isAvailable()) {
                EvalBackendObservation.onDegradation(staticBackend.getBackendId(),
                        EvalBackendObservation.REASON_UNAVAILABLE, resourcePath,
                        tree == null ? null : tree.getLocation());
                return EvalBackendDecision.interpreter(
                        EvalBackendObservation.REASON_UNAVAILABLE + ":" + staticBackend.getUnavailableReason(), true);
            }
            IEvalStaticBinding binding = tree == null ? null : staticBackend.findStaticBinding(resourcePath, tree);
            if (binding == null) {
                // 清单内资源必有生成类：应有而缺失即降级观测（codegen 漏跑/stale 的可观测哨兵）
                EvalBackendObservation.onDegradation(staticBackend.getBackendId(),
                        EvalBackendObservation.REASON_GENERATED_BINDING_MISSING, resourcePath,
                        tree == null ? null : tree.getLocation());
                return EvalBackendDecision.interpreter(EvalBackendObservation.REASON_GENERATED_BINDING_MISSING, true);
            }
            return EvalBackendDecision.staticBackend(staticBackend, binding);
        }

        // 动态路径（含经 RCM 加载的清单外资源）
        IEvalDynamicBackend dynamicBackend = registry.findDynamicBackend();
        if (dynamicBackend == null)
            return EvalBackendDecision.interpreter("dynamic-backend-not-registered", false);
        // 输入3：部署形态——native 下 truffle 结构性不适用（镜像排除机制归构建集成），静默
        if (isNativeDeployment())
            return EvalBackendDecision.interpreter("native-deployment", false);
        if (!isDynamicSlotEnabled()) {
            EvalBackendObservation.onDegradation(dynamicBackend.getBackendId(),
                    EvalBackendObservation.REASON_CONFIG_DISABLED, resourcePath,
                    tree == null ? null : tree.getLocation());
            return EvalBackendDecision.interpreter(EvalBackendObservation.REASON_CONFIG_DISABLED, true);
        }
        if (!dynamicBackend.isAvailable()) {
            EvalBackendObservation.onDegradation(dynamicBackend.getBackendId(),
                    EvalBackendObservation.REASON_UNAVAILABLE, resourcePath,
                    tree == null ? null : tree.getLocation());
            return EvalBackendDecision.interpreter(
                    EvalBackendObservation.REASON_UNAVAILABLE + ":" + dynamicBackend.getUnavailableReason(), true);
        }
        return EvalBackendDecision.dynamicBackend(dynamicBackend);
    }

    /** 近期裁决诊断环（有界）：生产诊断与测试身份断言（artifact = 执行体身份证据） */
    public synchronized List<EvalBackendDecision> getRecentDecisions() {
        List<EvalBackendDecision> list = new ArrayList<>(recentDecisions);
        Collections.reverse(list);
        return list;
    }

    /** 清空诊断环（测试隔离用） */
    public synchronized void clearRecentDecisions() {
        recentDecisions.clear();
    }

    private synchronized void recordDecision(EvalBackendDecision decision) {
        recentDecisions.addFirst(decision);
        while (recentDecisions.size() > DECISION_RING_MAX)
            recentDecisions.removeLast();
    }

    private boolean isForceInterpreter() {
        return CFG_XLANG_EXECUTION_FORCE_INTERPRETER.get();
    }

    private boolean isStaticSlotEnabled() {
        return CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED.get();
    }

    private boolean isDynamicSlotEnabled() {
        return CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED.get();
    }

    /**
     * 部署形态判定：配置式标记（auto = 探测 native image 标准系统属性，字符串探测无 GraalVM
     * import——依赖方向纪律）。
     */
    boolean isNativeDeployment() {
        String form = CFG_XLANG_EXECUTION_DEPLOYMENT_FORM.get();
        if (form == null || DEPLOYMENT_AUTO.equalsIgnoreCase(form.trim()))
            return System.getProperty(NATIVE_IMAGE_KIND_PROPERTY) != null;
        return DEPLOYMENT_NATIVE_IMAGE.equalsIgnoreCase(form.trim());
    }

    static String resourcePathOf(IExecutableExpression expr) {
        if (expr == null)
            return null;
        SourceLocation loc = expr.getLocation();
        return loc == null ? null : loc.getPath();
    }

    static String sourceKeyOf(String resourcePath, IExecutableExpression expr) {
        if (resourcePath != null)
            return resourcePath;
        if (expr == null)
            return null;
        return "xl-route-dyn/" + Integer.toHexString(System.identityHashCode(expr));
    }
}
