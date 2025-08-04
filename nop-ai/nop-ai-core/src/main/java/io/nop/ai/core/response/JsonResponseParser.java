package io.nop.ai.core.response;

import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.parse.JsonParser;
import io.nop.core.lang.json.utils.JsonTransformHelper;

public class JsonResponseParser {
    private static JsonResponseParser s_instance = new JsonResponseParser();

    public static JsonResponseParser instance() {
        return s_instance;
    }

    public static void registerInstance(JsonResponseParser parser) {
        s_instance = parser;
    }

    protected JsonResponseParser() {
    }

    public Object parseResponse(String response) {
        if (StringHelper.isEmpty(response))
            return null;

        TextScanner sc = null;
        int pos = response.startsWith("```") ? 0 : response.indexOf("\n```");
        if (pos >= 0) {
            int pos2 = response.indexOf('\n');
            int lastPos = response.lastIndexOf("\n```");
            if (lastPos > pos && pos2 > pos) {
                response = response.substring(pos2 + 1, lastPos);
                sc = TextScanner.fromString(null, response);
            }
        }

        if (sc == null) {
            sc = TextScanner.fromString(null, response);
            sc.skipUntil(s -> s.cur == '{' || s.cur == '[', true, "{");
        }
        if (sc.isEnd())
            return null;

        JsonParser parser = new JsonParser();
        parser.looseSyntax(true).checkEndAfterParse(false).strictMode(false);

        Object map = parser.parseJsonDoc(sc);

        map = JsonTransformHelper.trim(map, true, true);
        return map;
    }
}