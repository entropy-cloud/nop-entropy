package io.nop.rule.core.excel;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.model.RuleDecisionTableModel;
import io.nop.rule.core.model.RuleModel;

import static io.nop.rule.core.RuleErrors.ERR_RULE_WORKBOOK_NO_CONFIG_SHEET;
import static io.nop.rule.core.RuleErrors.ERR_RULE_WORKBOOK_NO_RULE_SHEET;

public class RuleExcelModelParser extends AbstractResourceParser<RuleModel> {

    @Override
    protected RuleModel doParseResource(IResource resource) {
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        RuleModel model = new RuleModel();
        parseRuleConfig(model, wk);
        RuleDecisionTableModel decisionTable = parseDecisionTable(model, wk);
        return model;
    }

    private void parseRuleConfig(RuleModel model, ExcelWorkbook wk) {
        ExcelSheet configSheet = wk.getSheet(RuleConstants.SHEET_NAME_CONFIG);
        if (configSheet == null)
            throw new NopException(ERR_RULE_WORKBOOK_NO_CONFIG_SHEET)
                    .source(wk);


    }

    private RuleDecisionTableModel parseDecisionTable(RuleModel model, ExcelWorkbook wk) {
        ExcelSheet ruleSheet = wk.getSheet(RuleConstants.SHEET_NAME_RULE);
        if (ruleSheet == null)
            throw new NopException(ERR_RULE_WORKBOOK_NO_RULE_SHEET)
                    .source(wk);

        return null;
    }
}