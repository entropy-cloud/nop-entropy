/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
            // 起始引号后的第一个换行不属于内容（与文本块语义一致）
            if (sc.cur == '\r') {
                sc.next();
                if (sc.cur == '\n')
                    sc.next();
            } else if (sc.cur == '\n') {
                sc.next();
            }
            SourceLocation loc = sc.location();
            String text = sc.nextUntil("\n\"\"\"", false)
                    .trimTrailing('\r').toString();
            // nextUntil停在分隔符之前且不消费它，这里必须消费掉\n"""，否则外层循环
            // 会把残留的"""当作下一个变量名而抛ERR_SCAN_INVALID_XML_NAME
            sc.next(4);
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
