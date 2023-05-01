/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.excel.util;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MultiLineConfigParser {
    public static MultiLineConfigParser INSTANCE = new MultiLineConfigParser();

    public Map<String, ValueWithLocation> parseConfig(SourceLocation loc, String text) {
        if (StringHelper.isEmpty(text))
            return Collections.emptyMap();

        TextScanner sc = TextScanner.fromString(loc, text);
        Map<String, ValueWithLocation> ret = new LinkedHashMap<>();
        sc.skipBlank();

        while (!sc.isEnd()) {
            String varName = sc.nextXmlName();
            sc.skipBlankInLine();
            sc.consume('=');
            sc.skipBlankInLine();

            ValueWithLocation value = parseValue(sc);
            ret.put(varName, value);
            sc.skipBlank();
        }
        return ret;
    }

    private ValueWithLocation parseValue(TextScanner sc) {
        if (sc.cur == '\r' || sc.cur == '\n') {
            return ValueWithLocation.of(sc.location(), "");
        }

        if (sc.startsWith("\"\"\"")) {
            sc.next(3);
            SourceLocation loc = sc.location();
            String text = sc.nextUntil("\n\"\"\"", false)
                    .trimTrailing('\r').toString();
            return ValueWithLocation.of(loc, text);
        }

        if (sc.cur == '\"' || sc.cur == '\'') {
            SourceLocation loc = sc.location();
            String text = sc.nextJavaString();
            return ValueWithLocation.of(loc, text);
        }

        if (sc.cur == '`') {
            SourceLocation loc = sc.location();
            String text = sc.nextDoubleEscapeString();
            return ValueWithLocation.of(loc, text);
        }

        SourceLocation loc = sc.location();
        String text = sc.nextUntilEndOfLine().trim().toString();
        return ValueWithLocation.of(loc, text);
    }
}
