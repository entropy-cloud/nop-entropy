/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core;

public interface AiCoreConstants {
    String ROLE_USER = "user";

    String ROLE_SYSTEM = "system";

    String ROLE_ASSISTANT = "assistant";

    String ROLE_TOOL = "tool";

    String POSTFIX_PROMPT_XML = ".prompt.xml";

    String POSTFIX_PROMPT_YAML = ".prompt.yaml";

    String PROMPT_NAME_TRANSLATE = "translate";

    String CONFIG_VAR_LLM_BASE_URL = "nop.ai.llm.{llmName}.base-url";

    String CONFIG_VAR_LLM_API_KEY = "nop.ai.llm.{llmName}.api-key";

    String PLACE_HOLDER_LLM_NAME = "{llmName}";

    String VAR_PROMPT_MODEL = "promptModel";
}
