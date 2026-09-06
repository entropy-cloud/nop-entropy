/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.excel;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICell;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.rule.core.model.RuleDecisionMatrixModel;
import io.nop.rule.core.model.RuleModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import io.nop.commons.util.objects.ValueWithLocation;

import static io.nop.rule.core.RuleErrors.ERR_RULE_INVALID_OUTPUT_CELL;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_CONFIG_VAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRuleTableModelParser {

    @Test
    public void testUnknownCommentVarRejected() {
        RuleTableModelParser parser = new RuleTableModelParser();
        ExcelCell cell = new ExcelCell();
        // 拼写错误的配置名必须报错，不允许静默忽略
        cell.setComment("valueExpre=abc");

        NopException e = assertThrows(NopException.class,
                () -> parser.getCommentVars(cell, "Rule", 3, 1));
        assertEquals(ERR_RULE_UNKNOWN_CONFIG_VAR.getErrorCode(), e.getErrorCode());
        assertEquals("valueExpre", e.getParam("varName"));
        assertEquals("B4", e.getParam("cellPos"));
    }

    @Test
    public void testKnownCommentVarsAccepted() {
        RuleTableModelParser parser = new RuleTableModelParser();
        ExcelCell cell = new ExcelCell();
        cell.setComment("var=season\nmultiMatch=true");

        Map<String, ValueWithLocation> vars = parser.getCommentVars(cell, "Rule", 0, 0);
        assertNotNull(vars);
        assertEquals("season", vars.get("var").asString());
        assertEquals("true", vars.get("multiMatch").asString());
    }

    @Test
    public void testEmptyCommentAccepted() {
        RuleTableModelParser parser = new RuleTableModelParser();
        ExcelCell cell = new ExcelCell();

        Map<String, ValueWithLocation> vars = parser.getCommentVars(cell, "Rule", 0, 0);
        assertNotNull(vars);
        assertTrue(vars.isEmpty(), "vars should be empty");
    }

    @Test
    public void testParseMatrixOutputsNullTopCell() {
        RuleTableModelParser parser = new RuleTableModelParser();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("Rule");
        ExcelTable table = new ExcelTable();
        sheet.setTable(table);

        // 左侧条件列有单元格，但输出区上方的表头单元格缺失
        ICell leftCell = table.makeCell(2, 1);
        leftCell.setValue("left");

        NopException e = assertThrows(NopException.class,
                () -> parser.parseMatrixOutputs(new RuleDecisionMatrixModel(), sheet, 2, 2, 3, 3, new RuleModel()));
        assertEquals(ERR_RULE_INVALID_OUTPUT_CELL.getErrorCode(), e.getErrorCode());
        assertEquals(CellPosition.toABString(1, 2), e.getParam("cellPos"));
    }
}
