/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.KeyValue;
import io.nop.commons.text.tokenizer.TextScanner;

import java.util.ArrayList;
import java.util.List;

import static io.nop.core.CoreErrors.ERR_REFLECT_PARSE_PROP_PATH_FAIL;

public class PropPathParser {
    public List<Object> parseFromText(SourceLocation loc, String text) {
        return parsePropPath(TextScanner.fromString(loc, text));
    }

    private List<Object> parsePropPath(TextScanner sc) {
        List<Object> ret = new ArrayList<>();

        ret.add(parseProp(sc));

        do {
            if (sc.isEnd())
                break;
            if (sc.cur == '.') {
                sc.consume('.');
                Object propName = parseProp(sc);
                ret.add(propName);
            } else if (sc.cur == '[') {
                Object index = parseIndex(sc);
                ret.add(index);
            } else {
                throw sc.newError(ERR_REFLECT_PARSE_PROP_PATH_FAIL);
            }
        } while (true);
        return ret;
    }

    Object parseProp(TextScanner sc) {
        if (sc.cur == '[') {
            Object index = parseIndex(sc);
            return index;
        } else if (sc.cur == '\'' || sc.cur == '"') {
            String str = sc.nextJavaString();
            return str;
        } else {
            String propName = sc.nextJavaVar();
            return propName;
        }
    }

    Object parseIndex(TextScanner sc) {
        Object index;
        sc.consume('[');
        sc.skipBlankInLine();
        if (sc.maybeNumber()) {
            index = sc.nextInt();
            sc.skipBlankInLine();
        } else {
            String key = sc.nextJavaVar();
            if (sc.cur == '=') {
                sc.next();
                String value = parseNext(sc);
                index = new KeyValue(key, value);
            } else {
                index = key;
            }
        }
        sc.consume(']');
        return index;
    }

    String parseNext(TextScanner sc) {
        if (sc.cur == '.' || sc.cur == ':') {
            sc.next();
            String str = sc.nextJavaVar();
            return str;
        }
        return sc.nextJavaVar();
    }
}
