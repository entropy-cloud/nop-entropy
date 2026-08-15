package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.ai.core.model.LlmFailoverConfig;
import io.nop.ai.core.model.LlmFailoverProviderModel;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.ModelClassCandidateModel;
import io.nop.ai.core.model.ModelClassConfig;
import io.nop.ai.core.model.ModelClassModel;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_DEFAULT_LLM;
import static io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_API_KEY;
import static io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ARG_MODEL_CLASS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_MSG;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_DEFAULT_LLMS;

/**
 * LLM配置帮助类。
 * 负责配置加载、API Key解析、模型配置查找等。
 */
public class LlmConfigHelper {

    private static final ICache<String, String> secretCache = new MapCache<>("ai-secret-cache", true);
    private static File secretDir;

    /**
     * 设置secret目录
     */
    public static void setSecretDir(File dir) {
        secretDir = dir;
    }

    public static void clearSecretCache() {
        secretCache.clear();
    }

    /**
     * 复位全部静态可变状态（MA6.1-AR-6）：清空 {@code secretCache} **并**重置
     * {@code secretDir} 为 null。与 {@link #clearSecretCache()}（只清缓存）不同，
     * 本方法保证测试间/调用间无静态状态泄漏——测试应在 {@code @BeforeEach} 调用，
     * 或需要在运行时切换 secret 目录且不想被缓存污染时调用。
     */
    public static void reset() {
        secretCache.clear();
        secretDir = null;
    }

    /**
     * 加载LLM配置
     */
    public static LlmModel loadConfig(String provider) {
        String path = "/nop/ai/llm/" + provider + ".llm.xml";
        return (LlmModel) ResourceComponentManager.instance().loadComponentModel(path);
    }

    /**
     * 获取Provider名称
     */
    public static String getProvider(ChatOptions options) {
        String provider = options != null ? options.getProvider() : null;
        if (StringHelper.isEmpty(provider)) {
            provider = CFG_AI_SERVICE_DEFAULT_LLM.get();
        }
        if (StringHelper.isEmpty(provider)) {
            throw new NopException(ERR_AI_SERVICE_NO_DEFAULT_LLMS);
        }
        return provider;
    }

    /**
     * 解析模型名称
     */
    public static String resolveModel(LlmModel config, ChatOptions options) {
        String model = options != null ? options.getModel() : null;

        if (StringHelper.isEmpty(model)) {
            model = config.getDefaultModel();
        }

        if (StringHelper.isEmpty(model)) {
            throw new NopException(ERR_AI_SERVICE_NO_DEFAULT_LLMS)
                    .param(ARG_LLM_NAME, config.getLocation());
        }

        // 处理别名
        if (config.getAliasMap() != null && config.getAliasMap().containsKey(model)) {
            model = config.getAliasMap().get(model);
        }

        return model;
    }

    /**
     * 获取模型配置
     */
    public static LlmModelModel getModelConfig(LlmModel config, String modelName) {
        if (config.getModels() == null || modelName == null) {
            return null;
        }

        LlmModelModel model = config.getModel(modelName);
        if (model == null) {
            // 尝试基础名称（如 qwen3:14b -> qwen3）
            String baseModel = StringHelper.firstPart(modelName, ':');
            if (!baseModel.equals(modelName)) {
                model = config.getModel(baseModel);
            }
        }

        return model;
    }

    /**
     * 解析API Key
     */
    public static String resolveApiKey(String provider) {
        String apiKeyName = StringHelper.replace(CONFIG_VAR_LLM_API_KEY, PLACE_HOLDER_LLM_NAME, provider);
        String apiKey = (String) AppConfig.var(apiKeyName);

        if (StringHelper.isEmpty(apiKey) && secretDir != null) {
            apiKey = secretCache.computeIfAbsent(provider, k -> {
                File secretFile = new File(secretDir, provider + ".txt");
                if (secretFile.exists()) {
                    String secret = StringHelper.strip(FileHelper.readText(secretFile, null));
                    if (secret != null) {
                        AppConfig.getConfigProvider().assignConfigValue(apiKeyName, secret);
                        return secret;
                    }
                }
                return "";
            });
        }

        return apiKey;
    }

    /**
     * 解析 provider 的有序备用账号链（plan 2026-08-01-1505-1，设计 §3.6）。
     * <p>
     * 返回 {@code <accounts>} 声明的有序备用账号清单（不含主账号）。每个 {@link LlmAccountModel}
     * 携带 {@code apiKey}（直配值；生产经 Nop config 变量替换/secret 注入）+ 可选 {@code baseUrl}
     * （per-account 覆盖）+ 可选额度元数据（诊断用，不做主动熔断）。
     * <p>
     * 链语义：{@code <accounts>} 是备用账号链，主账号 = {@link #resolveApiKey(String)}（与未配置时完全一致，
     * 零回归）。未配置 {@code <accounts>} 时返回<b>空列表</b>（非 null）——调用方据此判"无链"。
     * <p>
     * 返回的是不可变视图（基于 {@code config.getAccounts()} 的防御性拷贝），调用方可安全持有。
     */
    public static List<LlmAccountModel> resolveAccountChain(String provider) {
        LlmModel config = loadConfig(provider);
        List<LlmAccountModel> accounts = config.getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            return Collections.emptyList();
        }
        // Defensive copy preserving declaration order. The config list is a KeyedList
        // (by id) that retains insertion order; copy to a plain ArrayList so the caller
        // gets an ordered, snapshot view independent of later config mutation.
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }

    /**
     * 解析 provider 级并发上限缺省值（plan 2026-08-15-0604-3，设计 §3.4）。
     * <p>
     * 返回 {@code {provider}.llm.xml} 根元素 {@code concurrencyLimit} 的值（in-flight 并发上限，
     * 跳过语义——达到上限时请求发出前切换账号，与 rateLimit 的排队语义并存不互斥）：
     * <ul>
     *   <li>未配置（null）= 不限制（零回归缺省）。</li>
     *   <li>显式配置 0/负数 = 显式不限制（返回非 null，调用方按 ≤0 判"不限制"，不回退）。</li>
     * </ul>
     * 主账号（无 {@link LlmAccountModel} 实例）的限流值取本值（
     * {@link #resolveConcurrencyLimit(String, LlmAccountModel)} 传 null account 同义）。
     * 本方法 + {@link #resolveConcurrencyLimit(String, LlmAccountModel)} 是并发限流配置面的读取入口，
     * 供并发限流运行时（W5/W6/W7）消费。
     */
    public static Integer resolveConcurrencyLimit(String provider) {
        LlmModel config = loadConfig(provider);
        return config.getConcurrencyLimit();
    }

    /**
     * 解析账号的并发上限（plan 2026-08-15-0604-3，设计 §3.4），层级语义：
     * <ul>
     *   <li>账号级显式配置（{@code <account concurrencyLimit="...">}，含 0/负数）→ 返回账号值（不回退）。</li>
     *   <li>账号未配置（null）→ 回退 provider 级缺省（{@link #resolveConcurrencyLimit(String)}）。</li>
     *   <li>provider 级也未配置 → 返回 null = 不限制。</li>
     * </ul>
     * 主账号路径：{@code account} 传 null（主账号无 {@link LlmAccountModel} 实例）→ 取 provider 级缺省。
     * 返回 null = 不限制；返回非 null（含 0/负数）= 显式配置（≤0 为显式不限制）。
     */
    public static Integer resolveConcurrencyLimit(String provider, LlmAccountModel account) {
        if (account != null && account.getConcurrencyLimit() != null) {
            return account.getConcurrencyLimit();
        }
        return resolveConcurrencyLimit(provider);
    }

    /**
     * 跨 provider 有序故障转移声明的默认配置路径（plan 2026-08-01-1905-3，设计 §13.4 裁定 A）。
     * opt-in：该文件缺省（不存在）= 无 provider 链 = 零回归 fail-loud（账号链耗尽仍按今日行为）。
     */
    public static final String FAILOVER_CONFIG_PATH = "/nop/ai/llm/_default.llm-failover.xml";

    /**
     * 解析 {@code primaryProvider} 的跨 provider 有序故障转移目标链（plan 2026-08-01-1905-3，
     * 设计 §13.4 裁定 A）。
     * <p>
     * 加载单全局有序 provider 优先级表（{@code _default.llm-failover.xml}），找到
     * {@code primaryProvider} 的位置，返回其<b>之后</b>的有序子表（failover 目标 P2→P3…）。
     * failover 恒向优先级更低的方向游走（只取 primary 之后），故环不可能由声明构造。
     * <p>
     * 零回归语义：
     * <ul>
     *   <li>配置文件缺省（不存在）→ 返回<b>空列表</b>（无 failover，账号链耗尽 fail-loud）。</li>
     *   <li>{@code primaryProvider} 不在表中 → 返回<b>空列表</b>（未知 primary 无 failover）。</li>
     *   <li>primary 是表尾 → 返回<b>空列表</b>（无更低优先级 provider 可切）。</li>
     * </ul>
     * 返回的是不可变视图（防御性拷贝），调用方可安全持有。
     */
    public static List<LlmFailoverProviderModel> resolveFailoverChain(String primaryProvider) {
        if (StringHelper.isEmpty(primaryProvider)) {
            return Collections.emptyList();
        }
        // 零回归守卫：VFS 未初始化（如单元测试直构造 coordinator 未经引擎初始化）→ 无 failover（空表），
        // 账号链耗尽退回今日 fail-loud 行为。opt-in 配置子系统不可用不阻断调用。
        if (!VirtualFileSystem.isInitialized()) {
            return Collections.emptyList();
        }
        // opt-in：文件缺省 = 无 provider 链 = 零回归 fail-loud（非异常——合法状态）。
        if (!VirtualFileSystem.instance().getResource(FAILOVER_CONFIG_PATH).exists()) {
            return Collections.emptyList();
        }
        LlmFailoverConfig config = (LlmFailoverConfig) ResourceComponentManager.instance()
                .loadComponentModel(FAILOVER_CONFIG_PATH);
        List<LlmFailoverProviderModel> providers = config.getProviders();
        if (providers == null || providers.isEmpty()) {
            return Collections.emptyList();
        }
        // 找 primary 的位置，取其后有序子表。
        int idx = -1;
        for (int i = 0; i < providers.size(); i++) {
            if (primaryProvider.equals(providers.get(i).getProvider())) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= providers.size() - 1) {
            // primary 不在表，或是表尾（无更低优先级 provider）→ 无 failover。
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(providers.subList(idx + 1, providers.size())));
    }

    /**
     * 模型类路由组声明的默认配置路径（plan 2026-08-15-0849-2，设计 §3.3，Q8 裁定：新建配置面）。
     * opt-in：该文件缺省（不存在）= 无路由组 = 零回归（沿用既有单 provider 行为）。
     */
    public static final String MODEL_CLASS_CONFIG_PATH = "/nop/ai/llm/_default.model-class.xml";

    /**
     * 解析请求 {@code model} 所属的模型类（plan 2026-08-15-0849-2，设计 §3.3）。
     * <p>
     * 归属机制 = 显式成员声明（每个模型类的 {@code members} 清单，model 名全局匹配）；
     * 首个声明命中（模型类声明顺序）胜出——同 model 名跨 provider 的歧义由声明顺序消解。
     * <p>
     * 零回归语义：
     * <ul>
     *   <li>配置路径不存在（无 model-class 文件）→ 返回 <b>null</b>（无路由组，调用方沿用既有行为）。</li>
     *   <li>model 为空 / 未归属任何类 → 返回 <b>null</b>（无路由组）。</li>
     *   <li>VFS 未初始化（如单元测试直构造未经引擎初始化）→ 返回 <b>null</b>（同
     *       {@link #resolveFailoverChain(String)} 零回归守卫模式）。</li>
     * </ul>
     */
    public static ModelClassModel resolveModelClass(String modelName) {
        if (StringHelper.isEmpty(modelName)) {
            return null;
        }
        if (!VirtualFileSystem.isInitialized()) {
            return null;
        }
        // opt-in：文件缺省 = 无路由组 = 零回归（非异常——合法状态）。
        if (!VirtualFileSystem.instance().getResource(MODEL_CLASS_CONFIG_PATH).exists()) {
            return null;
        }
        ModelClassConfig config = (ModelClassConfig) ResourceComponentManager.instance()
                .loadComponentModel(MODEL_CLASS_CONFIG_PATH);
        List<ModelClassModel> classes = config.getModelClasses();
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        // 首个声明命中（声明顺序 = KeyedList 保持的插入序）。
        for (ModelClassModel modelClass : classes) {
            if (modelClass.getMembers() != null && modelClass.getMembers().contains(modelName)) {
                return modelClass;
            }
        }
        return null;
    }

    /**
     * 解析模型类的候选集（展开，plan 2026-08-15-0849-2，设计 §3.3 数据结构裁定）。
     * <p>
     * 把模型类声明的候选条目（{@code provider} + 可选 {@code model} + 可选 {@code accountRef}）
     * 展开为具体候选 {@link ModelClassCandidate}：
     * <ul>
     *   <li>{@code accountRef} 缺省 → 展开为"主账号 + 该 provider 有序账号链"（主账号在前，
     *       与账号链语义一致：首次调用用主账号，QUOTA/AUTH 触发后按声明序切换）。</li>
     *   <li>{@code accountRef} 配置 → 只展开为该账号（未知 id <b>解析期 fail-fast</b>，
     *       {@code ERR_AI_AGENT_INVALID_ARG}——不静默吞，Minimum Rules #24）。</li>
     *   <li>{@code model} 缺省 → 取 provider 的 defaultModel（无 defaultModel 解析期 fail-fast）。</li>
     * </ul>
     * 候选声明顺序保留（默认策略的声明序基础）。
     * <p>
     * 返回的是不可变视图（防御性拷贝），调用方可安全持有。
     *
     * @param modelClass 已解析的模型类（来自 {@link #resolveModelClass(String)}）；null 返回空列表
     */
    public static List<ModelClassCandidate> resolveModelClassCandidates(ModelClassModel modelClass) {
        if (modelClass == null || modelClass.getCandidates() == null || modelClass.getCandidates().isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelClassCandidate> result = new ArrayList<>();
        for (ModelClassCandidateModel declared : modelClass.getCandidates()) {
            String provider = declared.getProvider();
            assertProviderExists(provider, modelClass.getId());

            LlmModel config = loadConfig(provider);
            String model = declared.getModel();
            if (StringHelper.isEmpty(model)) {
                model = config.getDefaultModel();
            }
            if (StringHelper.isEmpty(model)) {
                throw new NopException(ERR_AI_SERVICE_NO_DEFAULT_LLMS)
                        .param(ARG_LLM_NAME, provider)
                        .param(ARG_MODEL_CLASS, modelClass.getId());
            }

            if (!StringHelper.isEmpty(declared.getAccountRef())) {
                // 单账号展开：引用 {provider}.llm.xml <accounts> 中账号 id；未知 id 解析期 fail-fast。
                LlmAccountModel account = resolveAccount(provider, modelClass.getId(), declared.getAccountRef());
                result.add(candidateFor(provider, model, account));
            } else {
                // 主账号 + 该 provider 有序账号链（主账号在前）。
                result.add(candidateFor(provider, model, null));
                for (LlmAccountModel account : resolveAccountChain(provider)) {
                    result.add(candidateFor(provider, model, account));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 解析 {@code primaryProvider} 的跨 provider failover 链候选（plan 2026-08-15-0849-2，
     * Phase 1 裁定：primary 键 = 请求目标 provider）。
     * <p>
     * 对 {@link #resolveFailoverChain(String)} 返回的每个 failover provider 展开为
     * "主账号 + 该 provider 有序账号链"（model = provider 声明覆盖或 defaultModel，
     * 并发上限按 W3 层级语义解析）。无 failover 链（无配置/未知 primary/表尾）= 空列表
     * （零回归 fail-loud）。
     * <p>
     * 返回的是不可变视图。
     */
    public static List<ModelClassCandidate> resolveProviderChainCandidates(String primaryProvider) {
        List<LlmFailoverProviderModel> chain = resolveFailoverChain(primaryProvider);
        if (chain.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelClassCandidate> result = new ArrayList<>();
        for (LlmFailoverProviderModel failover : chain) {
            String provider = failover.getProvider();
            assertProviderExists(provider, "failover-chain(" + primaryProvider + ")");
            LlmModel config = loadConfig(provider);
            String model = failover.getModel();
            if (StringHelper.isEmpty(model)) {
                model = config.getDefaultModel();
            }
            if (StringHelper.isEmpty(model)) {
                throw new NopException(ERR_AI_SERVICE_NO_DEFAULT_LLMS)
                        .param(ARG_LLM_NAME, provider)
                        .param(ARG_MODEL_CLASS, "failover-chain(" + primaryProvider + ")");
            }
            result.add(candidateFor(provider, model, null));
            for (LlmAccountModel account : resolveAccountChain(provider)) {
                result.add(candidateFor(provider, model, account));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static ModelClassCandidate candidateFor(String provider, String model, LlmAccountModel account) {
        return new ModelClassCandidate(provider, model,
                account != null ? account.getApiKey() : null,
                account != null ? account.getBaseUrl() : null,
                resolveConcurrencyLimit(provider, account));
    }

    private static void assertProviderExists(String provider, String modelClassId) {
        if (!VirtualFileSystem.isInitialized()) {
            throw new NopException(ERR_AI_AGENT_INVALID_ARG)
                    .param(ARG_MSG, "model class " + modelClassId + " references provider but VFS is not initialized: "
                            + provider);
        }
        String path = "/nop/ai/llm/" + provider + ".llm.xml";
        if (!VirtualFileSystem.instance().getResource(path).exists()) {
            throw new NopException(ERR_AI_AGENT_INVALID_ARG)
                    .param(ARG_MSG, "model class " + modelClassId + " references unknown provider: " + provider);
        }
    }

    private static LlmAccountModel resolveAccount(String provider, String modelClassId, String accountRef) {
        for (LlmAccountModel account : resolveAccountChain(provider)) {
            if (accountRef.equals(account.getId())) {
                return account;
            }
        }
        throw new NopException(ERR_AI_AGENT_INVALID_ARG)
                .param(ARG_MSG, "model class " + modelClassId + " references unknown accountRef: "
                        + accountRef + " (provider " + provider + ")");
    }
}
