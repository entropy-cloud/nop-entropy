/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.util.MathHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;

import static io.nop.api.core.ApiErrors.ARG_TARGET_TYPE;
import static io.nop.xlang.XLangErrors.ARG_EXCLUDE_MIN;
import static io.nop.xlang.XLangErrors.ARG_MAX_LENGTH;
import static io.nop.xlang.XLangErrors.ARG_MIN_VALUE;
import static io.nop.xlang.XLangErrors.ARG_PATTERN;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_CONVERT_TO_TYPE_FAIL;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_LENGTH_GREATER_THAN_MAX_LENGTH;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_LENGTH_LESS_THAN_MIN_LENGTH;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_NOT_MATCH_PATTERN;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_VALUE_TOO_LARGE;
import static io.nop.xlang.XLangErrors.ERR_SCHEMA_PROP_VALUE_TOO_SMALL;
import static io.nop.xlang.XLangErrors.ERR_XDEF_UNKNOWN_STD_DOMAIN;

public class SimpleSchemaValidator {
    public static final SimpleSchemaValidator INSTANCE = new SimpleSchemaValidator();

    public void validate(ISchema schema, SourceLocation loc, String propName, Object value,
                         IEvalScope scope, IServiceContext ctx,
                         IValidationErrorCollector collector) {
        if (value == null)
            return;

        if (schema.getStdDomain() != null) {
            IStdDomainHandler domainHandler = StdDomainRegistry.instance().getStdDomainHandler(schema.getStdDomain());
            if (domainHandler == null)
                throw new NopException(ERR_XDEF_UNKNOWN_STD_DOMAIN)
                        .source(schema).param(ARG_STD_DOMAIN, schema.getStdDomain());
            domainHandler.validate(null, propName, value, collector);
        } else if (schema.getStdDataType() != null) {
            schema.getStdDataType().convert(value, err -> {
                collector.buildError(ERR_SCHEMA_PROP_CONVERT_TO_TYPE_FAIL)
                        .param(ARG_TARGET_TYPE, schema.getStdDataType().getSimpleClassName())
                        .param(ARG_VALUE, value).param(ARG_PROP_NAME, propName)
                        .addToCollector(collector);
                return null;
            });
        }

        if (schema.getPattern() != null) {
            if (!schema.matchPattern(value.toString())) {
                collector.buildError(ERR_SCHEMA_PROP_NOT_MATCH_PATTERN)
                        .param(ARG_PATTERN, schema.getPattern())
                        .param(ARG_PROP_NAME, propName)
                        .param(ARG_VALUE, value)
                        .addToCollector(collector);
            }
        }

        checkRange(schema, loc, propName, value, collector);
        checkLength(schema, loc, propName, value, collector);

        if (schema.getValidator() != null) {
            try {
                schema.getValidator().invoke(scope);
            } catch (Exception e) {
                collector.addException(e);
            }
        }
    }

    void checkRange(ISchema schema, SourceLocation loc,
                    String propName, Object value, IValidationErrorCollector collector) {
        if (schema.getMax() != null || schema.getMin() != null) {
            Number v = ConvertHelper.toNumber(value, err -> {
                collector.buildError(ERR_SCHEMA_PROP_CONVERT_TO_TYPE_FAIL)
                        .loc(loc)
                        .param(ARG_TARGET_TYPE, schema.getStdDataType().getSimpleClassName())
                        .param(ARG_VALUE, value).param(ARG_PROP_NAME, propName)
                        .addToCollector(collector);
                return null;
            });

            if (schema.getMin() != null) {
                int cmp = MathHelper.compareWithConversion(v, schema.getMin());
                boolean less = Boolean.TRUE.equals(schema.getExcludeMin()) ? cmp <= 0 : cmp < 0;
                if (less) {
                    collector.buildError(ERR_SCHEMA_PROP_VALUE_TOO_SMALL)
                            .loc(loc).param(ARG_PROP_NAME, propName)
                            .param(ARG_MIN_VALUE, schema.getMin())
                            .param(ARG_VALUE, value)
                            .param(ARG_EXCLUDE_MIN, Boolean.TRUE.equals(schema.getExcludeMin()))
                            .addToCollector(collector);
                }
            }

            if (schema.getMax() != null) {
                int cmp = MathHelper.compareWithConversion(v, schema.getMax());
                boolean greater = Boolean.TRUE.equals(schema.getExcludeMax()) ? cmp >= 0 : cmp > 0;
                if (greater) {
                    collector.buildError(ERR_SCHEMA_PROP_VALUE_TOO_LARGE)
                            .loc(loc).param(ARG_PROP_NAME, propName)
                            .param(ARG_MIN_VALUE, schema.getMin())
                            .param(ARG_VALUE, value)
                            .param(ARG_EXCLUDE_MIN, Boolean.TRUE.equals(schema.getExcludeMin()))
                            .addToCollector(collector);
                }
            }
        }
    }

    void checkLength(ISchema schema, SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
        if (schema.getMaxLength() != null) {
            String str = value.toString();
            if (str.length() > schema.getMaxLength()) {
                collector.buildError(ERR_SCHEMA_PROP_LENGTH_GREATER_THAN_MAX_LENGTH)
                        .loc(loc).param(ARG_PROP_NAME, propName)
                        .param(ARG_MAX_LENGTH, schema.getMaxLength())
                        .param(ARG_VALUE, value)
                        .addToCollector(collector);
            }
        }

        if (schema.getMinLength() != null && schema.getMinLength() > 0) {
            String str = value.toString();
            if (str.length() < schema.getMinLength()) {
                collector.buildError(ERR_SCHEMA_PROP_LENGTH_LESS_THAN_MIN_LENGTH)
                        .loc(loc).param(ARG_PROP_NAME, propName)
                        .param(ARG_MAX_LENGTH, schema.getMaxLength())
                        .param(ARG_VALUE, value)
                        .addToCollector(collector);
            }
        }
    }
}