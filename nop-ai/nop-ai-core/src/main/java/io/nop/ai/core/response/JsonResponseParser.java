package io.nop.ai.core.response;

import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.parse.JsonParser;
import io.nop.core.lang.json.utils.JsonTransformHelper;

import java.util.Map;

public class JsonResponseParser {

    public Map<String, Object> parseResponse(String response) {
        if (StringHelper.isEmpty(response))
            return null;

        TextScanner sc = TextScanner.fromString(null, response);
        sc.skipUntil('{', true);
        if (sc.isEnd())
            return null;

        JsonParser parser = new JsonParser();
        parser.checkEndAfterParse(true).strictMode(false);

        Map<String, Object> map = (Map<String, Object>) parser.parseJsonDoc(sc);

        JsonTransformHelper.transformMap(map, value -> {
            if (value instanceof String)
                return ((String) value).trim();
            return value;
        }, null);
        return map;
    }
}