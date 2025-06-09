package io.nop.ai.core.response;

import io.nop.ai.core.AiCoreConstants;
import io.nop.core.lang.json.JsonTool;
import io.nop.markdown.simple.MarkdownCodeBlock;

import java.util.Map;

public class YamlResponseParser {
    static YamlResponseParser _instance = new YamlResponseParser();

    public static YamlResponseParser instance() {
        return _instance;
    }

    public static void registerInstance(YamlResponseParser instance) {
        _instance = instance;
    }

    public Map<String, Object> parseResponse(String content) {
        MarkdownCodeBlock codeBlock = CodeResponseParser.instance().parseResponse(content, AiCoreConstants.CODE_LANG_YAML);
        if (codeBlock == null)
            return null;
        return JsonTool.parseMap(codeBlock.getSource());
    }
}
