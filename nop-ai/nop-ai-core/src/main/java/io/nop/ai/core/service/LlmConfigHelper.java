package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.ResourceComponentManager;

import java.io.File;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_DEFAULT_LLM;
import static io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_API_KEY;
import static io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_NO_DEFAULT_LLMS;

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
}
