/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.dict.DictProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.ExcelCellRef;
import io.nop.report.core.XptConstants;
import io.nop.report.core.dataset.DynamicReportDataSet;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;

import java.util.ArrayList;
import java.util.List;

public interface IXptRuntime extends IEvalContext {
    static IXptRuntime fromScope(IEvalScope scope) {
        return (IXptRuntime) scope.getValue(XptConstants.VAR_XPT_RT);
    }

    ExpandedCell getCell();

    void setCell(ExpandedCell cell);

    ExpandedRow getRow();

    void setRow(ExpandedRow row);

    ExcelImage getImage();

    void setImage(ExcelImage image);

    ExpandedTable getTable();

    ExpandedSheet getSheet();

    void setSheet(ExpandedSheet sheet);

    ExcelWorkbook getWorkbook();

    void setWorkbook(ExcelWorkbook workbook);

    ICache<Object, Object> getCache();

    default DictBean getDict(String dictName) {
        String locale = ContextProvider.currentLocale();
        return DictProvider.instance().requireDict(locale, dictName, getCache(), this);
    }

    default String getLabelByValue(String dictName, Object value) {
        return getDict(dictName).getLabelByValue(value);
    }

    /**
     * i18n 消息解析（WI11）：当前 locale 下取 key 对应消息，缺失返回 default。
     */
    default String i18n(String key, String defaultValue) {
        return io.nop.core.i18n.I18nMessageManager.instance().getMessage(
                io.nop.api.core.context.ContextProvider.currentLocale(), key, defaultValue);
    }

    /**
     * 平台 @i18n: 约定解析（多 key 逗号回退 + |缺省值），见 I18nMessageManager.resolveI18nVar。
     */
    default String i18nVar(String message) {
        return io.nop.core.i18n.I18nMessageManager.instance().resolveI18nVar(
                io.nop.api.core.context.ContextProvider.currentLocale(), message);
    }

    /**
     * 校验当前用户可访问指定数据源（xpt-rt 取数标签在取数前调用）。
     * checker 未注册（嵌入式库使用）时直接放行；未授权抛 nop.err.report.auth.* 错误。
     */
    default void checkDataSourceAccess(String querySpace) {
        IReportAuthChecker authChecker = IReportAuthChecker.get();
        if (authChecker != null)
            authChecker.checkDataSourceAccess(querySpace);
    }

    /**
     * 按 provider 返回的行级条件包装 SQL：select * from ( 原SQL ) _rpt_sec where <条件>，
     * 保留原 SQL 语句级属性；provider 未注册或无条件时原样返回。
     */
    default io.nop.core.lang.sql.SQL applyRowCondition(io.nop.core.lang.sql.SQL sql, String dsName) {
        IReportDataSecurityProvider provider = IReportDataSecurityProvider.get();
        if (provider == null)
            return sql;
        IReportDataSecurityProvider.RowCondition condition = provider.getRowCondition(getReportPath(), dsName);
        if (condition == null)
            return sql;

        sql = trimTrailingSemicolon(sql);
        io.nop.core.lang.sql.SQL.SqlBuilder sb = io.nop.core.lang.sql.SQL.begin()
                .name(sql.getName()).querySpace(sql.getQuerySpace()).fetchSize(sql.getFetchSize())
                .timeout(sql.getTimeout()).cacheRef(sql.getCacheRef())
                .disableLogicalDelete(sql.isDisableLogicalDelete()).readOnly(sql.isReadOnly())
                .append("select * from (")
                .append(sql)
                .append(") _rpt_sec where ")
                .sqlWithParams(condition.getConditionSql(), condition.getParams());
        return sb.end();
    }

    /**
     * 子查询包装要求原 SQL 无尾部分号；仅裁剪文本尾部（';'+空白），已有参数 marker 均位于裁剪区之前，偏移不变。
     */
    static io.nop.core.lang.sql.SQL trimTrailingSemicolon(io.nop.core.lang.sql.SQL sql) {
        String text = sql.getText();
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ';' || Character.isWhitespace(text.charAt(end - 1))))
            end--;
        if (end == text.length())
            return sql;
        return new io.nop.core.lang.sql.SQL(sql.getName(), text.substring(0, end), sql.getMarkers(),
                sql.getTimeout(), sql.getCacheRef(), sql.getFetchSize(), sql.getQuerySpace(),
                sql.isDisableLogicalDelete(), sql.isAllowUnderscoreName(), sql.isEnableFilter(),
                sql.isReadOnly(), sql.getLocation());
    }

    /**
     * 展示层列脱敏（ ExpandedSheetEvaluator 字段取值路径调用）；provider 未注册时原样返回。
     */
    default Object maskValue(String dsName, String field, Object value) {
        IReportDataSecurityProvider provider = IReportDataSecurityProvider.get();
        if (provider == null)
            return value;
        return provider.maskValue(dsName, field, value);
    }

    /**
     * 当前报表模板路径；无法取得时返回 null（规则按 dsName/字段匹配）。
     */
    default String getReportPath() {
        io.nop.excel.model.ExcelWorkbook workbook = getWorkbook();
        return workbook == null ? null : workbook.resourcePath();
    }

    Object evaluateCell(ExpandedCell cell);

    Object field(String field);

    ExpandedCellSet getNamedCellSet(String cellName);

    ExpandedCell getNamedCell(String cellName);

    DynamicReportDataSet ds(String dsName);

    DynamicReportDataSet makeDs(String dsName, Object value);

    /**
     * 解析层次坐标返回对应的单元格集合。这个函数仅作为示例使用，用于演示不使用支持层次坐标的表达式引擎时如何处理
     */
    ExpandedCellSet cells(String cellExpr);

    default CellRange getExpandedCellRange(CellRange cellRange) {
        ExpandedTable table = getTable();
        if (table == null)
            return null;
        return table.getExpandedCellRange(cellRange);
    }

    default List<CellRange> getExpandedCellRanges(List<CellRange> cellRanges) {
        List<CellRange> ret = new ArrayList<>(cellRanges.size());
        for (CellRange cellRange : cellRanges) {
            CellRange expanded = getExpandedCellRange(cellRange);
            if (expanded != null)
                ret.add(expanded);
        }
        return ret;
    }

    /**
     * 递增计数器并返回递增前的值
     *
     * @param name 变量名
     * @return 递增前的值
     */
    default int incAndGet(String name) {
        return incAndGet(name, 1);
    }

    default int getAndInc(String name) {
        return getAndInc(name, 1);
    }

    int incAndGet(String name, int delta);

    int getAndInc(String name, int delta);

    long seq(String name);

    ExcelImage makeImage();

    void addSheetCleanup(Runnable cleanup);

    void addWorkbookCleanup(Runnable cleanup);

    void addSheetLoopCleanup(Runnable cleanup);

    void runSheetCleanup();

    void runWorkbookCleanup();

    void runSheetLoopCleanup();

    Object evalExcelFormula(String formula);

    /**
     * 解析Excel公式，将其中的单元格定义展开，生成展开后的Excel公式
     */
    String expandExcelFormula(String formula);

    default ExcelCellRef buildCellRef(String cellRefTpl, int rowOffset, int colOffset, int rowSize, int colSize) {
        if (StringHelper.isEmpty(cellRefTpl))
            return null;
        ExcelCellRef cellRefBean = ExcelCellRef.parse(cellRefTpl);
        CellRange range = cellRefBean.getCellRange().offset(rowOffset, colOffset).changeSize(rowSize, colSize);
        return new ExcelCellRef(cellRefBean.getSheetName(), range);
    }
}