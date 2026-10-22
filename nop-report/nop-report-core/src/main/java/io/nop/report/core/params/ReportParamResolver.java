/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.params;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.commons.type.StdDataType;
import io.nop.excel.model.XptParamModel;
import io.nop.excel.model.XptWorkbookModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.report.core.ReportParamErrors.ARG_PARAM_NAME;
import static io.nop.report.core.ReportParamErrors.ERR_REPORT_PARAM_MISSING_REQUIRED;
import static io.nop.report.core.ReportParamErrors.ERR_REPORT_PARAM_TYPE_ERROR;

/**
 * 解析报表参数声明（XptWorkbookModel.params）：取 scope 输入 {@code param} Map，
 * 做 required 校验、类型转换、默认值回填，把解析后的规范化 Map 写回 scope 的 {@code param} 变量，
 * 模板内以 ${param.xxx} 取值。幂等：覆盖式写回，同一 renderer 多次 generate 语义稳定。
 * <p>
 * 裁定（见 ai-dev/design/nop-report/parameter-model.md）：未声明的输入忽略；
 * 模板内未声明的 ${param.x} 引用运行时取 null（XPL 动态取值无法静态识别，不做展开期报错）；
 * 候选集可动态（SQL/字典），不做 options 枚举闭合校验。
 */
public class ReportParamResolver {
    private ReportParamResolver() {
    }

    public static final String VAR_PARAM = "param";

    @SuppressWarnings("unchecked")
    public static void resolveAndInject(XptWorkbookModel workbookModel, io.nop.core.lang.eval.IEvalScope scope) {
        if (workbookModel == null)
            return;
        List<XptParamModel> params = workbookModel.getParams();
        if (params == null || params.isEmpty())
            return;

        Object input = scope.getValue(VAR_PARAM);
        Map<String, Object> inputMap = input instanceof Map ? (Map<String, Object>) input : null;

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (XptParamModel param : params) {
            Object value = inputMap == null ? null : inputMap.get(param.getName());
            if (value == null || (value instanceof String && StringHelper.isEmpty((String) value)))
                value = parseDefault(param);

            if (value == null) {
                if (param.isRequired())
                    throw new NopException(ERR_REPORT_PARAM_MISSING_REQUIRED).param(ARG_PARAM_NAME, param.getName());
                resolved.put(param.getName(), null);
                continue;
            }

            resolved.put(param.getName(), convert(param, value));
        }

        // 未声明的输入原样保留，便于模板引用应用侧扩展变量
        if (inputMap != null) {
            for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
                resolved.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        scope.setLocalValue(null, VAR_PARAM, resolved);
    }

    private static Object parseDefault(XptParamModel param) {
        String defaultValue = param.getDefaultValue();
        if (StringHelper.isEmpty(defaultValue))
            return null;
        return convert(param, defaultValue);
    }

    private static Object convert(XptParamModel param, Object value) {
        StdDataType type = param.getType();
        if (type == null)
            return value;

        try {
            switch (type) {
                case STRING:
                    return value instanceof Map || value instanceof List ? value : ConvertHelper.toString(value);
                case INT:
                    return ConvertHelper.toPrimitiveInt(value, err -> typeError(param, value));
                case FLOAT:
                    return ConvertHelper.toPrimitiveDouble(value, err -> typeError(param, value));
                case BOOLEAN:
                    return ConvertHelper.toPrimitiveBoolean(value, err -> typeError(param, value));
                case DATE:
                    return value instanceof LocalDate ? value
                            : ConvertHelper.toLocalDate(value, err -> typeError(param, value));
                case DATETIME:
                    return value instanceof LocalDateTime ? value
                            : ConvertHelper.toLocalDateTime(value, err -> typeError(param, value));
                default:
                    return value;
            }
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw typeError(param, value);
        }
    }

    private static NopException typeError(XptParamModel param, Object value) {
        return new NopException(ERR_REPORT_PARAM_TYPE_ERROR)
                .param(ARG_PARAM_NAME, param.getName())
                .param("value", value)
                .param("expectType", param.getType() == null ? null : param.getType().name());
    }
}
