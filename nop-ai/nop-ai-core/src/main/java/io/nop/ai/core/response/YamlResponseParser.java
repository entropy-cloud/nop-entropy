package io.nop.ai.core.response;

import io.nop.ai.core.AiCoreConstants;
import io.nop.commons.text.SourceCodeBlock;
import io.nop.core.lang.json.JsonTool;

import java.util.Map;

public class YamlResponseParser {
    // 3.1-MA-12（plan 2555 裁定 B / C1-003）：volatile 保证换装可见 + 安全发布
    static volatile YamlResponseParser _instance = new YamlResponseParser();

    public static YamlResponseParser instance() {
        return _instance;
    }

    public static void registerInstance(YamlResponseParser instance) {
        _instance = instance;
    }

    public Map<String, Object> parseResponse(String content) {
        SourceCodeBlock codeBlock = CodeResponseParser.instance().parseResponse(content, AiCoreConstants.CODE_LANG_YAML);
        if (codeBlock == null)
            return null;
        return JsonTool.parseMap(codeBlock.getSource());
    }
}
