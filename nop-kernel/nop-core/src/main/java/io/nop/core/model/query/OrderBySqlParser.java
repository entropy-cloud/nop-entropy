/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.query;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

import static io.nop.commons.CommonErrors.ERR_SCAN_TOKEN_INCOMPLETE;
import static io.nop.core.CoreErrors.ERR_QUERY_INVALID_ORDER_BY_SQL;

public class OrderBySqlParser {
    public static final OrderBySqlParser INSTANCE = new OrderBySqlParser();

    public List<OrderFieldBean> parseFromText(SourceLocation loc, String text) {
        if(StringHelper.isBlank(text))
            return null;

        TextScanner sc = TextScanner.fromString(loc,text);
        List<OrderFieldBean> list = parseOrderBy(sc);
        if (!sc.isEnd())
            throw sc.newError(ERR_QUERY_INVALID_ORDER_BY_SQL);
        return list;
    }

    public List<OrderFieldBean> parseOrderBy(TextScanner sc) {
        List<OrderFieldBean> ret = new ArrayList<>();
        OrderFieldBean order = parseOrderField(sc);
        if (order == null)
            return ret;
        ret.add(order);
        while (sc.tryConsume(',')) {
            OrderFieldBean field = parseOrderField(sc);
            if (field == null) {
                break;
            }
            ret.add(field);
        }
        return ret;
    }

    public OrderFieldBean parseOrderField(TextScanner sc) {
        sc.skipBlank();
        OrderFieldBean field = new OrderFieldBean();
        String name = sc.nextJavaPropPath();
        sc.skipBlank();

        field.setName(name);
        if (sc.tryMatchIgnoreCase("desc")) {
            sc.checkTokenEnd();
            field.setDesc(true);
        } else if (sc.tryMatchIgnoreCase("asc")) {
            sc.checkTokenEnd();
            field.setDesc(false);
        } else {
            field.setDesc(false);
        }

        if (sc.tryMatchIgnoreCase("nulls")) {
            sc.checkTokenEnd();
            if (sc.tryMatchIgnoreCase("first")) {
                sc.checkTokenEnd();
                field.setNullsFirst(true);
            } else if (sc.tryMatchIgnoreCase("last")) {
                sc.checkTokenEnd();
                field.setNullsFirst(false);
            } else {
                throw sc.newError(ERR_SCAN_TOKEN_INCOMPLETE);
            }
        }
        return field;
    }
}
