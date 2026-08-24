/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_EXCLUDE_MAX;
import static io.nop.xlang.XLangErrors.ARG_MAX_VALUE;
import static io.nop.xlang.XLangErrors.ARG_MIN_VALUE;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_VALUE_TOO_LARGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestSimpleSchemaValidator {

    static List<ErrorBean> validateWith(SchemaImpl schema, Object value) {
        List<ErrorBean> errors = new ArrayList<>();
        IValidationErrorCollector collector = errors::add;
        SimpleSchemaValidator.INSTANCE.validate(schema, null, null, "value", value, null, collector);
        return errors;
    }

    /**
     * "值过大"错误的错误参数必须是 max/excludeMax，而不是 min 分支的参数
     */
    @Test
    public void testValueTooLargeErrorParams() {
        SchemaImpl schema = new SchemaImpl();
        schema.setMin(0.0);
        schema.setMax(10.0);
        schema.setExcludeMax(false);

        List<ErrorBean> errors = validateWith(schema, 11);
        assertEquals(1, errors.size());
        ErrorBean error = errors.get(0);
        assertEquals(ERR_SCHEMA_PROP_VALUE_TOO_LARGE.getErrorCode(), error.getErrorCode());
        assertEquals(10.0, error.getParams().get(ARG_MAX_VALUE));
        assertFalse((Boolean) error.getParams().get(ARG_EXCLUDE_MAX));
        assertFalse(error.getParams().containsKey(ARG_MIN_VALUE));
    }

    /**
     * excludeMax=true 时上界为开区间，10 本身即违规，且错误参数 excludeMax 必须为 true
     */
    @Test
    public void testExcludeMaxErrorParams() {
        SchemaImpl schema = new SchemaImpl();
        schema.setMax(10.0);
        schema.setExcludeMax(true);

        List<ErrorBean> errors = validateWith(schema, 10);
        assertEquals(1, errors.size());
        ErrorBean error = errors.get(0);
        assertEquals(ERR_SCHEMA_PROP_VALUE_TOO_LARGE.getErrorCode(), error.getErrorCode());
        assertEquals(10.0, error.getParams().get(ARG_MAX_VALUE));
        assertEquals(Boolean.TRUE, error.getParams().get(ARG_EXCLUDE_MAX));
    }
}
