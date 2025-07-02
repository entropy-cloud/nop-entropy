package io.nop.ai.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AiCoreErrors {
    String ARG_LLM_NAME = "llmName";
    String ARG_OPTION_NAME = "optionName";
    String ARG_HTTP_STATUS = "httpStatus";

    String ARG_PROP_PATH = "propPath";

    String ARG_EXPECTED = "expected";
    String ARG_LINE = "line";

    String ARG_NAME = "name";
    String ARG_VALUE = "value";

    String ARG_CONTENT = "content";

    String ARG_INPUT_NAME = "inputName";
    String ARG_OUTPUT_NAME = "outputName";

    String ARG_BLOCK_BEGIN = "blockBegin";
    String ARG_BLOCK_END = "blockEnd";

    String ARG_CONFIG_VAR = "configVar";

    String ARG_PROMPT_NAME = "promptName";
    String ARG_VAR_NAME = "varName";

    ErrorCode ERR_AI_SERVICE_NO_DEFAULT_LLMS =
            define("nop.err.ai.service.no-default-llms", "没有指定调用的大语言模型，也没有配置nop.ai.service.default-llm来指定缺省的大语言模型");

    ErrorCode ERR_AI_SERVICE_NO_BASE_URL =
            define("nop.err.ai.service.no-base-url", "大语言模型{llmName}没有指定baseUrl配置", ARG_LLM_NAME);

    ErrorCode ERR_AI_SERVICE_OPTION_NOT_SET =
            define("nop.err.ai.service.option-not-set", "大语言模型{llmName}没有设置选项{optionName}", ARG_LLM_NAME, ARG_OPTION_NAME);

    ErrorCode ERR_AI_SERVICE_HTTP_ERROR =
            define("nop.err.ai.service.http-error", "大语言模型{llmName}调用失败，HTTP状态码={httpStatus}", ARG_LLM_NAME, ARG_HTTP_STATUS);

    ErrorCode ERR_AI_RESULT_IS_EMPTY =
            define("nop.err.ai.service.result-is-empty", "大语言模型返回的结果为空");

    ErrorCode ERR_AI_RESULT_INVALID_END_LINE =
            define("nop.err.ai.service.result-invalid-end-line", "大语言模型返回的结果行没有符合预期模式", ARG_EXPECTED, ARG_LINE);

    ErrorCode ERR_AI_RESULT_NO_EXPECTED_PART =
            define("nop.err.ai.service.result-no-expected-part", "大语言模型返回的结果没有符合预期模式, 缺少内容：{expected}", ARG_EXPECTED);

    ErrorCode ERR_AI_RESULT_INVALID_NUMBER =
            define("nop.err.ai.service.result-invalid-number", "大语言模型返回的结果不是数字:name={name},value={name}", ARG_NAME, ARG_VALUE);

    ErrorCode ERR_AI_INVALID_RESPONSE =
            define("nop.err.ai.service.invalid-response", "大语言模型返回的结果不正确");

    ErrorCode ERR_AI_MANDATORY_INPUT_IS_EMPTY = define("nop.err.ai.mandatory-input-is-empty", "输入参数{inputName}不能为空", ARG_INPUT_NAME);

    ErrorCode ERR_AI_MANDATORY_OUTPUT_IS_EMPTY = define("nop.err.ai.mandatory-output-is-empty", "输出参数{outputName}不能为空", ARG_OUTPUT_NAME);

    ErrorCode ERR_AI_PROMPT_USE_UNDEFINED_VAR = define("nop.err.ai.prompt-var-not-defined", "提示词使用了未定义的变量{varName}",
            ARG_PROMPT_NAME, ARG_VAR_NAME);
}
