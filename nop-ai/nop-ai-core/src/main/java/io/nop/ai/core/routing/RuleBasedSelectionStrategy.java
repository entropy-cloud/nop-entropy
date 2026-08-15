package io.nop.ai.core.routing;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.rule.core.IRuleManager;
import io.nop.rule.core.IRuleRuntime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则选择策略（plan 2026-08-15-1116-1，设计 §3.3，Q10）：用平台既有 XLang 规则 DSL（
 * {@code rule.xdef}，nop-rule）表达候选选择逻辑（成本/权重/时段/请求属性等），经 IoC bean 绑定，
 * 作为默认策略（健康度 + 并发感知 + 声明序）的可插拔替换。
 *
 * <p><b>规则输入契约</b>（策略固定设置，规则文件必须逐一在 {@code <input>} 中声明——平台硬约束
 * {@code NormalizeInputExecutableRule}：未声明输入被设置抛 {@code ERR_RULE_UNKNOWN_INPUT_VAR}，
 * 规则文件必须声明 ≥1 个 input；除 computed 派生输入外，下列输入均<b>不得</b>声明 {@code mandatory}，
 * 否则空值路径抛 {@code ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY}）：
 * <ul>
 *   <li>{@code model}（string）——{@code request.options==null} 时为 null（接口契约允许）；</li>
 *   <li>{@code provider}（string）——同上可为 null；</li>
 *   <li>{@code candidates}（List&lt;Map&gt;）——候选集声明序投影，每项
 *       {@code {index:int, provider, model, accountBaseUrl, concurrencyLimit}}；
 *       <b>不含 {@code accountKey}</b>（备用账号 apiKey 为机密，禁止进规则输入——安全裁定）；</li>
 *   <li>{@code health}（Map&lt;Integer, Map&gt;）——候选健康快照，键 = 候选 index（Integer，
 *       与 {@code candidates} 的 index 一致）；值
 *       {@code {circuitState:String(CircuitState 枚举名), currentConcurrency:int,
 *       concurrencyLimit:int|null, available:boolean}}；</li>
 *   <li>{@code attempted}（List&lt;Integer&gt;）——本轮已尝试候选的 index 集（可为空）。</li>
 * </ul>
 *
 * <p><b>规则输出契约</b>：输出变量名 = <b>{@code selectedIndex}</b>（int，候选列表 0-based index）。
 * 规则文件必须声明 {@code <output name="selectedIndex" type="int"/>} 且<b>不得</b>声明
 * {@code mandatory}——平台硬约束 {@code NormalizeOutputExecutableRule} 在规则<b>未命中时也</b>
 * 校验 declared outputs，mandatory 声明会让未命中路径先抛 {@code ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY}，
 * 使语义分支①（未命中 → null）错误码漂移。非整数输出值由引擎 cast（{@code type="int"}）抛错 =
 * 引擎域快速失败，策略不做二次解析。
 *
 * <p><b>谓词编写模式</b>（平台硬约束）：filter-bean 的 {@code name} 属性经
 * {@code XLangASTBuilder.buildPropExpr} 编译，只按 {@code .} 切分构造标识符/成员表达式，
 * <b>不支持 {@code [index]} 下标</b>。**执行期实证（2026-08-15）**：{@code <expr>} filter op
 * 在 XML 规则文件中<b>不可用</b>——{@code FilterBeanToPredicateTransformer.visitUnknown}
 * 从 {@code value} attr 读取已编译的 {@code Expression}，而原始 XML 节点的 body 文本不会编译进
 * 该 attr（编译期 NPE）；{@code <expr>} op 仅适用于编程式构造的 filter bean
 * （{@code ExpressionToFilterBeanTransformer} 路径）。因此 XML 规则文件访问列表/映射元素
 * 必须用 <b>computed 输入</b>（{@code computed="true"} + {@code <defaultExpr>}）派生辅助变量；
 * 禁止 filter-bean {@code name} 下标写法。computed 输入声明顺序必须在其依赖输入之后
 * （{@code NormalizeInputExecutableRule} 按声明顺序把值写入 eval scope）。
 *
 * <p><b>语义分支</b>（三个可区分分支）：
 * <ol>
 *   <li>规则未命中（{@code isRuleMatch()==false}）→ 返回 null（接口契约：调用方 fail-loud；
 *       {@code ModelClassRouter} 主动路径最终抛 {@code ERR_AI_MODEL_CLASS_SATURATED}——
 *       非静默降级）；</li>
 *   <li>命中但输出 map 无 {@code selectedIndex} → 返回 null（同①）；</li>
 *   <li>命中且 {@code selectedIndex} 越界 / 命中已尝试候选 → <b>fail-loud</b>
 *       （{@code ERR_AI_AGENT_INVALID_ARG} + {@code ARG_MSG}，规则配置错误，不静默回退默认策略）。</li>
 * </ol>
 *
 * <p><b>index 稳定性前提</b>：分支③与已尝试判定隐含"同一游走执行内多次 {@code select} 的 index
 * 含义一致"——{@code ModelClassRouter.currentPool()} 扩展后重建 list 但内容/顺序稳定（equals 语义
 * 可换算），成立。规则作者据此按 index 引用候选。
 *
 * <p><b>单例 stateless 契约</b>：本类为单例 bean，{@code select()} 每调用新建 {@link IRuleRuntime}
 * （per-call 有状态对象），不得缓存为字段——W6/W7 并发消费前提。
 *
 * <p><b>IoC 绑定</b>：bean 属性 = {@code ruleManager}（{@link IRuleManager}，setter 注入；ref 经
 * {@code ioc:optional} 注入，未部署 nop-rule 时容器可启动）+ {@code ruleName}（必填）+
 * {@code ruleVersion}（可选，null = 最新版）。{@code ruleManager} 为 null 时本策略首用显式

 * fail-fast（{@code ERR_AI_AGENT_INVALID_ARG}），不静默回退默认策略。
 */
public final class RuleBasedSelectionStrategy implements ISelectionStrategy {

    public static final String INPUT_MODEL = "model";
    public static final String INPUT_PROVIDER = "provider";
    public static final String INPUT_CANDIDATES = "candidates";
    public static final String INPUT_HEALTH = "health";
    public static final String INPUT_ATTEMPTED = "attempted";

    public static final String OUTPUT_SELECTED_INDEX = "selectedIndex";

    private IRuleManager ruleManager;
    private String ruleName;
    private Long ruleVersion;

    /**
     * @param ruleManager 规则引擎（setter 注入；ref 应为 {@code ioc:optional}——未部署 nop-rule 的
     *                    容器可启动，本策略首用 fail-fast）
     */
    public void setRuleManager(IRuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    /**
     * @param ruleName 规则名（必填；经 {@code resolve-rule:{ruleName}[/v{version}]} 解析）
     */
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    /**
     * @param ruleVersion 规则版本（可选；null = 最新版）
     */
    public void setRuleVersion(Long ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    @Override
    public ModelClassCandidate select(ChatRequest request, List<ModelClassCandidate> candidates,
                                      IModelClassHealth health, Set<ModelClassCandidate> attempted) {
        // 健康视图缺失 = 调用方编程错误：显式 fail-fast，不隐式假设（与默认策略契约一致）。
        if (health == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "RuleBasedSelectionStrategy: health view must not be null");
        }
        // ruleManager 未注入（容器未部署 nop-rule）：显式 fail-fast，不静默回退默认策略。
        if (ruleManager == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "RuleBasedSelectionStrategy: ruleManager bean is not wired (ioc:optional ref resolved to null; deploy nop-rule or do not use this strategy)");
        }
        if (ruleName == null || ruleName.isEmpty()) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "RuleBasedSelectionStrategy: ruleName must be configured");
        }
        if (candidates == null) {
            // 候选集 null = 调用方编程错误（接口契约非 null）；空列表是合法状态（空候选池）。
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "RuleBasedSelectionStrategy: candidates must not be null");
        }

        IRuleRuntime ruleRt = ruleManager.newRuleRuntime();
        ruleRt.setInputs(buildInputs(request, candidates, health, attempted));

        Map<String, Object> outputs = ruleManager.getRule(ruleName, ruleVersion).executeForOutputs(ruleRt);

        // 分支①：规则未命中（无任何分支匹配）→ null（调用方 fail-loud；非静默吞错）。
        if (!ruleRt.isRuleMatch()) {
            return null;
        }

        Object selected = outputs.get(OUTPUT_SELECTED_INDEX);
        // 分支②：命中但无输出变量 → null（同①）。
        if (selected == null) {
            return null;
        }

        // 分支③：输出值无效（越界 / 命中已尝试候选）→ fail-loud（规则配置错误，不静默）。
        // 非整数已在引擎侧经 type="int" cast 抛错（引擎域快速失败），此处只处理整数值。
        if (!(selected instanceof Integer)) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "RuleBasedSelectionStrategy: rule " + ruleName + " output selectedIndex must be an int, but got: " + selected);
        }
        int index = (Integer) selected;
        if (index < 0 || index >= candidates.size()) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "RuleBasedSelectionStrategy: rule " + ruleName + " selectedIndex out of bounds: " + index
                                    + " (candidates size=" + candidates.size() + ")");
        }
        ModelClassCandidate candidate = candidates.get(index);
        if (attempted != null && attempted.contains(candidate)) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "RuleBasedSelectionStrategy: rule " + ruleName + " selectedIndex " + index
                                    + " refers to an already-attempted candidate; the rule must consume the attempted input");
        }
        return candidate;
    }

    private Map<String, Object> buildInputs(ChatRequest request, List<ModelClassCandidate> candidates,
                                            IModelClassHealth health, Set<ModelClassCandidate> attempted) {
        Map<String, Object> inputs = new HashMap<>();
        ChatOptions options = request != null ? request.getOptions() : null;
        inputs.put(INPUT_MODEL, options != null ? options.getModel() : null);
        inputs.put(INPUT_PROVIDER, options != null ? options.getProvider() : null);

        List<Map<String, Object>> candidateList = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ModelClassCandidate c = candidates.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("index", i);
            item.put("provider", c.getProvider());
            item.put("model", c.getModel());
            // 安全裁定：accountKey（备用账号 apiKey 明文）禁止进入规则输入。
            item.put("accountBaseUrl", c.getAccountBaseUrl());
            item.put("concurrencyLimit", c.getConcurrencyLimit());
            candidateList.add(item);
        }
        inputs.put(INPUT_CANDIDATES, candidateList);

        Map<Integer, Map<String, Object>> healthView = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateHealth ch = health.healthOf(candidates.get(i));
            Map<String, Object> item = new HashMap<>();
            item.put("circuitState", ch.getCircuitState().name());
            item.put("currentConcurrency", ch.getCurrentConcurrency());
            item.put("concurrencyLimit", ch.getConcurrencyLimit());
            item.put("available", ch.isAvailable());
            healthView.put(i, item);
        }
        inputs.put(INPUT_HEALTH, healthView);

        List<Integer> attemptedIndexes = new ArrayList<>();
        if (attempted != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (attempted.contains(candidates.get(i))) {
                    attemptedIndexes.add(i);
                }
            }
        }
        inputs.put(INPUT_ATTEMPTED, attemptedIndexes);
        return inputs;
    }
}
