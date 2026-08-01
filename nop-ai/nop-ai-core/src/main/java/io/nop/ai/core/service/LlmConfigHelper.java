package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.ai.core.model.LlmFailoverConfig;
import io.nop.ai.core.model.LlmFailoverProviderModel;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
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
}
