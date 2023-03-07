/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.sql;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.RawText;
import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.MaskedValue;
import io.nop.core.lang.eval.IEvalOutput;

import java.util.Collection;

/**
 * 收集xpl模板语言在XLangOutputMode=sql时的输出。 作为value输出的变量将被作为sql变量保存，而不是直接作为sql字符串拼接，从而避免sql注入攻击。
 */
public class CollectSqlOutput implements IEvalOutput {
    private SQL.SqlBuilder sb = new SQL.SqlBuilder();

    @Override
    public void comment(String comment) {
        if (comment != null) {
            comment = StringHelper.replace(comment, "*/", "_/");
            sb.append("/*").append(comment).append("*/\n");
        }
    }

    public SQL.SqlBuilder getResult() {
        return sb;
    }

    @Override
    public void value(SourceLocation loc, Object value) {
        if (value != null) {
            if (value instanceof IMarkedString) {
                sb.append(((IMarkedString) value));
            } else if (value instanceof RawText) {
                // 明确标记为RawText的文本可以被安全的作为sql文本拼接
                sb.append(((RawText) value).getText());
            } else if (value instanceof MaskedValue) {
                sb.markValue("?", ((MaskedValue) value).getValue(), true);
            } else if (value instanceof Collection) {
                sb.spreadParams((Collection<?>) value);
            } else if (value instanceof ISqlExpr) {
                ((ISqlExpr) value).appendTo(sb);
            } else {
                sb.markValue("?", value, false);
            }
        }
    }

    @Override
    public void text(SourceLocation loc, String text) {
        if (sb.isEmpty()) {
            text = StringHelper.trimLeft(text);
        }
        sb.append(text);
    }
}
