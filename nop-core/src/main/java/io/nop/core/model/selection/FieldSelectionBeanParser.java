/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.selection;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.Symbol;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.commons.CommonErrors.ERR_SCAN_UNEXPECTED_CHAR;
import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ERR_SELECTION_INVALID_ARG_NAME;

public class FieldSelectionBeanParser {
    public static FieldSelectionBean fromText(SourceLocation loc, String text) {
        if (StringHelper.isEmpty(text))
            return null;
        return new FieldSelectionBeanParser().parseFromText(loc, text);
    }

    public FieldSelectionBean parseFromText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        sc.skipBlank();
        FieldSelectionBean selection = selectionSet(sc);
        sc.checkEnd();
        return selection;
    }

    private FieldSelectionBean selectionSet(TextScanner sc) {
        FieldSelectionBean selectionSet = new FieldSelectionBean();
        // 允许 {a,b,c} 形式或者 a,b,c这种简化形式
        if (sc.tryMatch('{')) {
            Map<String, FieldSelectionBean> selections = selections(sc);
            sc.match('}');
            selectionSet.setFields(selections);
        } else {
            Map<String, FieldSelectionBean> selections = selections(sc);
            selectionSet.setFields(selections);
        }

        return selectionSet;
    }

    private Map<String, FieldSelectionBean> selections(TextScanner sc) {
        if (sc.cur == '}' || sc.isEnd())
            return Collections.emptyMap();

        Map<String, FieldSelectionBean> ret = new LinkedHashMap<>();
        do {
            selection(sc, ret);
            if (sc.cur == '}' || sc.isEnd())
                break;
        } while (sc.tryMatch(',') || sc.blankSkipped);
        return ret;
    }

    private void selection(TextScanner sc, Map<String, FieldSelectionBean> ret) {
        FieldSelectionBean sel = new FieldSelectionBean();
        if (sc.cur == '.') {
            sc.match("...");
            String name = sc.nextJavaVar();
            sc.skipBlank();
            ret.put("..." + name, FieldSelectionBean.DEFAULT_SELECTION);
            return;
        }

        String alias = null;
        String name = sc.nextJavaVar();
        sc.skipBlank();
        if (sc.tryMatch(':')) {
            alias = name;
            name = sc.nextJavaVar();
            sc.skipBlank();
        }
        sel.setName(name);

        Map<String, Object> args = arguments(sc);
        sel.setArgs(args);

        sel.setDirectives(directives(sc));

        if (sc.cur == '{') {
            sc.match('{');
            Map<String, FieldSelectionBean> selections = selections(sc);
            sc.match('}');
            sel.setFields(selections);
        }

        if (alias == null)
            alias = name;

        ret.put(alias, sel);
    }

    private Map<String, Map<String, Object>> directives(TextScanner sc) {
        if (sc.cur != '@')
            return Collections.emptyMap();

        Map<String, Map<String, Object>> ret = new LinkedHashMap<>();
        do {
            directive(sc, ret);
        } while (sc.cur == '@');
        return ret;
    }

    private void directive(TextScanner sc, Map<String, Map<String, Object>> ret) {
        sc.consume('@');
        String name = sc.nextJavaVar();
        Map<String, Object> arguments = arguments(sc);
        ret.put(name, arguments);
    }

    private Map<String, Object> arguments(TextScanner sc) {
        if (!sc.tryMatch('('))
            return Collections.emptyMap();
        if (sc.tryMatch(')'))
            return Collections.emptyMap();

        Map<String, Object> ret = new LinkedHashMap<>();
        do {
            argument(sc, ret);
        } while (sc.tryMatch(','));

        sc.match(')');
        return ret;
    }

    private void argument(TextScanner sc, Map<String, Object> ret) {
        String name = sc.nextJavaVar();
        if (name.charAt(0) == '$')
            throw sc.newError(ERR_SELECTION_INVALID_ARG_NAME).param(ARG_NAME, name);
        sc.skipBlankInLine();
        sc.match(':');
        Object value = value(sc);
        ret.put(name, value);
        sc.skipBlank();
    }

    private Object value(TextScanner sc) {
        if (sc.cur == '$') {
            String name = sc.nextJavaVar();
            sc.skipBlank();
            // 包装为Symbol，用于和值区分
            return Symbol.of(name);
        }
        if (sc.tryMatchToken("null")) {
            return null;
        }
        if (sc.tryMatchToken("true"))
            return true;
        if (sc.tryMatchToken("false"))
            return false;
        if (sc.cur == '"') {
            String str = sc.nextJavaString();
            sc.skipBlank();
            return str;
        }
        if (sc.maybeNumber()) {
            Number num = sc.nextNumber();
            sc.skipBlank();
            return num;
        }
        throw sc.newError(ERR_SCAN_UNEXPECTED_CHAR);
    }

}
