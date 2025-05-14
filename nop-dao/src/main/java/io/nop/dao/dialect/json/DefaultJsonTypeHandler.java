/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.json;

import io.nop.commons.text.RawText;
import io.nop.commons.type.StdSqlType;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.dialect.IDataTypeHandler;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.binder.IDataParameters;

public class DefaultJsonTypeHandler implements IDataTypeHandler {
    @Override
    public String toLiteral(Object value, IDialect dialect) {
        if (value == null)
            return "NULL";

        return "JSON '" + toJsonText(value) + "'";
    }

    private String toJsonText(Object value) {
        if (value instanceof String)
            return (String) value;
        return JsonTool.stringify(value);
    }

    @Override
    public Object fromLiteral(String text, IDialect dialect) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isJavaType(Object value) {
        if (value instanceof RawText)
            return true;
        return false;
    }

    @Override
    public StdSqlType getStdSqlType() {
        return StdSqlType.JSON;
    }

    @Override
    public Object getValue(IDataParameters params, int index) {
        return params.getString(index);
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setString(index, null);
        } else {
            String str = value.toString();
            params.setString(index, str);
        }
    }
}
