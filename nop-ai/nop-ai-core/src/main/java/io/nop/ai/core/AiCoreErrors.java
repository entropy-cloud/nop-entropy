package io.nop.ai.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AiCoreErrors {
    String ARG_LLM_NAME = "llmName";
    String ARG_OPTION_NAME = "optionName";
    String ARG_HTTP_STATUS = "httpStatus";

    String ARG_PROP_PATH = "propPath";

    ErrorCode ERR_AI_SERVICE_NO_DEFAULT_LLMS =
            define("nop.err.ai.service.no-default-llms", "没有指定调用的大语言模型，也没有配置nop.ai.service.default-llm来指定缺省的大语言模型");

    ErrorCode ERR_AI_SERVICE_NO_BASE_URL =
            define("nop.err.ai.service.no-base-url", "大语言模型{llmName}没有指定baseUrl配置", ARG_LLM_NAME);

    ErrorCode ERR_AI_SERVICE_OPTION_NOT_SET =
            define("nop.err.ai.service.option-not-set", "大语言模型{llmName}没有设置选项{optionName}", ARG_LLM_NAME, ARG_OPTION_NAME);

    ErrorCode ERR_AI_SERVICE_HTTP_ERROR =
            define("nop.err.ai.service.http-error", "大语言模型{llmName}调用失败，HTTP状态码={httpStatus}", ARG_LLM_NAME, ARG_HTTP_STATUS);
}
