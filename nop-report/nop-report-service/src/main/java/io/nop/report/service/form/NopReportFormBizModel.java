/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.form;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.excel.model.XptParamModel;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.report.core.ReportParamErrors;
import io.nop.report.core.engine.IReportAuthChecker;
import io.nop.report.core.engine.IReportEngine;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelWorkbook;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表参数查询表单接口：从模板内嵌参数声明生成 AMIS form schema 与参数候选项。
 * 两个 action 均经 IReportEngine.getXptModel 加载模板（触发报表级授权）；
 * 声明了 querySpace 的 SQL 候选先过数据源授权，未声明走默认数据源（由报表级授权覆盖）。
 */
@BizModel("NopReportForm")
public class NopReportFormBizModel {

    @Inject
    IReportEngine reportEngine;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @BizQuery
    public Map<String, Object> getReportFormSchema(@Name("reportPath") String reportPath) {
        List<XptParamModel> params = requireParams(reportPath);

        List<Map<String, Object>> body = new ArrayList<>();
        for (XptParamModel param : params) {
            body.add(buildControl(param));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "form");
        schema.put("title", reportPath);
        schema.put("body", body);
        return schema;
    }

    @BizQuery
    public List<Map<String, Object>> getReportParamOptions(@Name("reportPath") String reportPath,
                                                           @Name("paramName") String paramName,
                                                           @Name("dependsValues") Map<String, Object> dependsValues) {
        List<XptParamModel> params = requireParams(reportPath);
        XptParamModel param = params.stream()
                .filter(p -> p.getName().equals(paramName))
                .findFirst()
                .orElseThrow(() -> new NopException(ReportParamErrors.ERR_REPORT_PARAM_UNKNOWN)
                        .param(ReportParamErrors.ARG_PARAM_NAME, paramName));

        if (param.getOptions() != null && !param.getOptions().isEmpty())
            return staticOptions(param);

        if (param.getDictName() != null)
            return dictOptions(param.getDictName());

        if (param.getOptionsSql() != null) {
            if (param.getQuerySpace() != null) {
                IReportAuthChecker authChecker = IReportAuthChecker.get();
                if (authChecker != null)
                    authChecker.checkDataSourceAccess(param.getQuerySpace());
            }
            return sqlOptions(param);
        }

        return staticOptions(param);
    }

    private List<XptParamModel> requireParams(String reportPath) {
        ExcelWorkbook workbook = reportEngine.getXptModel(reportPath);
        XptWorkbookModel model = workbook.getModel();
        if (model == null || model.getParams() == null || model.getParams().isEmpty())
            return new ArrayList<>();
        return model.getParams();
    }

    private Map<String, Object> buildControl(XptParamModel param) {
        Map<String, Object> control = new LinkedHashMap<>();
        control.put("name", param.getName());
        control.put("label", param.getLabel() == null ? param.getName() : param.getLabel());
        control.put("required", param.isRequired());
        control.put("multiple", param.isMultiple());
        if (param.getDefaultValue() != null)
            control.put("value", param.getDefaultValue());
        if (param.getDependsOn() != null) {
            control.put("dependsOn", param.getDependsOn());
        }

        boolean hasCandidates = (param.getOptions() != null && !param.getOptions().isEmpty())
                || param.getDictName() != null || param.getOptionsSql() != null;
        control.put("type", controlType(param, hasCandidates));
        if (hasCandidates) {
            control.put("optionsKind",
                    param.getOptions() != null && !param.getOptions().isEmpty() ? "static"
                            : param.getDictName() != null ? "dict" : "sql");
        }
        if (param.getQuerySpace() != null)
            control.put("querySpace", param.getQuerySpace());
        return control;
    }

    private String controlType(XptParamModel param, boolean hasCandidates) {
        if (hasCandidates)
            return "select";
        if (param.getControlType() != null)
            return param.getControlType();

        XptParamModel m = param;
        switch (m.getType() == null ? io.nop.commons.type.StdDataType.STRING : m.getType()) {
            case INT:
            case FLOAT:
            case DOUBLE:
                return "input-number";
            case BOOLEAN:
                return "switch";
            case DATE:
                return "input-date";
            case DATETIME:
                return "input-datetime";
            default:
                return "input-text";
        }
    }

    private List<Map<String, Object>> staticOptions(XptParamModel param) {
        List<Map<String, Object>> ret = new ArrayList<>();
        if (param.getOptions() == null)
            return ret;
        for (Object item : param.getOptions()) {
            Map<String, Object> option = toOptionMap(item);
            if (option != null)
                ret.add(option);
        }
        return ret;
    }

    private Map<String, Object> toOptionMap(Object item) {
        if (item instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) item;
            Object label = map.get("label");
            Object value = map.get("value");
            if (label != null || value != null) {
                Map<String, Object> ret = new LinkedHashMap<>();
                ret.put("label", label == null ? String.valueOf(value) : label);
                ret.put("value", value);
                return ret;
            }
        }
        if (item instanceof DynamicObject) {
            Map<String, Object> map = ((DynamicObject) item).toMap();
            return toOptionMap(map);
        }
        if (item instanceof XNode) {
            XNode node = (XNode) item;
            Object label = node.attrText("label");
            Object value = node.attrText("value");
            if (label != null || value != null) {
                Map<String, Object> ret = new LinkedHashMap<>();
                ret.put("label", label == null ? String.valueOf(value) : label);
                ret.put("value", value);
                return ret;
            }
        }
        return null;
    }

    private List<Map<String, Object>> dictOptions(String dictName) {
        String locale = ContextProvider.currentLocale();
        DictBean dict = DictProvider.instance().requireDict(locale, dictName, null, null);
        List<Map<String, Object>> ret = new ArrayList<>();
        if (dict == null || dict.getOptions() == null)
            return ret;
        for (DictOptionBean option : dict.getOptions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", option.getLabel());
            item.put("value", option.getValue());
            ret.add(item);
        }
        return ret;
    }

    private List<Map<String, Object>> sqlOptions(XptParamModel param) {
        SQL sql = new SQL(param.getOptionsSql());
        if (param.getQuerySpace() != null)
            sql = sql.extend().querySpace(param.getQuerySpace()).end();
        List<Map<String, Object>> rows = jdbcTemplate.findAll(sql);

        List<Map<String, Object>> ret = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object label = firstOf(row, "label", "name", "text");
            Object value = firstOf(row, "value", "option_value", "id", "code");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label == null ? String.valueOf(value) : label);
            item.put("value", value);
            ret.add(item);
        }
        return ret;
    }

    private Object firstOf(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key))
                return row.get(key);
        }
        return null;
    }
}
