/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;

import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_DEF_TYPE;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_CP_EXPR;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_DEPRECATED;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_INTERNAL;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_MANDATORY;
import static io.nop.xlang.xdef.XDefConstants.XDEF_TYPE_PREFIX_OPTIONS;

public class XDefTypeDeclParser {
    public XDefTypeDecl parseFromText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        sc.skipBlank();

        boolean deprecated = false;
        boolean mandatory = false;
        boolean allowCpExpr = false;
        boolean internal = false;

        do {
            if (sc.tryConsume(XDEF_TYPE_PREFIX_DEPRECATED)) {
                deprecated = true;
            } else if (sc.tryConsume(XDEF_TYPE_PREFIX_MANDATORY)) {
                mandatory = true;
            } else if (sc.tryConsume(XDEF_TYPE_PREFIX_CP_EXPR)) {
                allowCpExpr = true;
            } else if (sc.tryConsume(XDEF_TYPE_PREFIX_INTERNAL)) {
                internal = true;
            } else {
                break;
            }
        } while (true);

        String stdDomain = intern(sc.nextXmlNamespace());
        String domain = null;
        String options = null;
        List<String> defaultAttrNames = null;
        Object defaultValue = null;

        if (sc.tryConsume('[')) {
            domain = intern(sc.nextXmlNamespace());
            sc.match(']');
        }

        if (sc.tryConsume(XDEF_TYPE_PREFIX_OPTIONS)) {
            options = sc.nextUntil(s -> s.cur == '=' && sc.peek() != '>', true, "=").trim().toString();
            sc.skipBlank();
        }

        if (sc.tryConsume('=')) {
            if (sc.tryConsume(XDefConstants.XDEF_TYPE_ATTR_PREFIX)) {
                String value = sc.nextUntilEnd().trim().toString();
                if (!value.isEmpty()) {
                    defaultAttrNames = StringHelper.stripedSplit(value, ',');
                }
            } else {
                String value = sc.nextUntilEnd().trim().toString();
                if (!value.isEmpty()) {
                    try {
                        defaultValue = JsonTool.parseSimpleJsonValue(value, StdDataType.ANY);
                    } catch (NopException e) {
                        e.loc(loc).param(ARG_STD_DOMAIN, stdDomain).param(ARG_DEF_TYPE, text);
                        throw e;
                    }
                }
            }
        }
        sc.skipBlank();
        sc.checkEnd();
        return new XDefTypeDecl(deprecated, internal, mandatory, allowCpExpr, stdDomain, domain, options,
                defaultAttrNames, defaultValue);
    }

    protected String intern(String str) {
        return str != null ? str.intern() : null;
    }
}
