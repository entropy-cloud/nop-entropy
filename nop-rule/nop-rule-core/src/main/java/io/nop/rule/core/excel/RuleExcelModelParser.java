package io.nop.rule.core.excel;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.core.model.table.tree.TreeCell;
import io.nop.core.model.table.tree.TreeTableHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.excel.imp.ImportModelHelper;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.MultiLineConfigParser;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.rule.core.RuleConstants;
import io.nop.rule.core.expr.RuleExprParser;
import io.nop.rule.core.model.RuleDecisionTreeModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.filter.ExpressionToFilterBeanTransformer;
import io.nop.xlang.xmeta.ObjVarDefineModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.rule.core.RuleErrors.ARG_CELL_POS;
import static io.nop.rule.core.RuleErrors.ARG_TEXT;
import static io.nop.rule.core.RuleErrors.ARG_VAR_NAME;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INVALID_DECISION_TREE_TABLE;
import static io.nop.rule.core.RuleErrors.ERR_RULE_INVALID_INPUT_VAR;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR;
import static io.nop.rule.core.RuleErrors.ERR_RULE_VAR_CELL_SPAN_MUST_BE_ONE;
import static io.nop.rule.core.RuleErrors.ERR_RULE_VAR_CELL_TEXT_IS_EMPTY;
import static io.nop.rule.core.RuleErrors.ERR_RULE_WORKBOOK_NO_CONFIG_SHEET;
import static io.nop.rule.core.RuleErrors.ERR_RULE_WORKBOOK_NO_RULE_SHEET;

public class RuleExcelModelParser extends AbstractResourceParser<RuleModel> {
    private final XLangCompileTool compileTool;

    private final Map<String, RuleDecisionTreeModel> nodeIdMap = new HashMap<>();

    public RuleExcelModelParser(XLangCompileTool compileTool) {
        this.compileTool = compileTool;
    }

    public RuleExcelModelParser() {
        this(XLang.newCompileTool().allowUnregisteredScopeVar(true));
    }

    @Override
    protected RuleModel doParseResource(IResource resource) {
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);

        RuleModel model = parseRuleConfig(wk);

        ExcelSheet ruleSheet = wk.getSheet(RuleConstants.SHEET_NAME_RULE);
        if (ruleSheet == null)
            throw new NopException(ERR_RULE_WORKBOOK_NO_RULE_SHEET)
                    .source(wk);

        RuleDecisionTreeModel decisionTable = parseDecisionTree(model, ruleSheet);
        model.setDecisionTree(decisionTable);
        return model;
    }

    private RuleModel parseRuleConfig(ExcelWorkbook wk) {
        ExcelSheet configSheet = wk.getSheet(RuleConstants.SHEET_NAME_CONFIG);
        if (configSheet == null)
            throw new NopException(ERR_RULE_WORKBOOK_NO_CONFIG_SHEET)
                    .source(wk);

        ImportModel importModel = ImportModelHelper.getImportModel(RuleConstants.IMP_PATH_RULE);
        ImportSheetModel sheetModel = importModel.getSheet(RuleConstants.SHEET_NAME_CONFIG);
        RuleModel rule = ImportModelHelper.parseSheet(sheetModel, configSheet, compileTool, RuleModel.class);
        rule.initVarMap();
        return rule;
    }

    private RuleDecisionTreeModel parseDecisionTree(RuleModel model, ExcelSheet ruleSheet) {
        ExcelCell cell0 = (ExcelCell) ruleSheet.getTable().getCell(0, 0);
        if (cell0.getColSpan() != 1 && cell0.getRowSpan() <= 1)
            throw new NopException(ERR_RULE_INVALID_DECISION_TREE_TABLE)
                    .source(ruleSheet);

        ExcelCell inputCell = (ExcelCell) ruleSheet.getTable().getCell(0, 1);
        if (inputCell == null)
            throw new NopException(ERR_RULE_INVALID_DECISION_TREE_TABLE)
                    .source(ruleSheet);

        ExcelCell outputCell = (ExcelCell) ruleSheet.getTable().getCell(0, inputCell.getRowSpan() + 1);
        if (outputCell == null)
            throw new NopException(ERR_RULE_INVALID_DECISION_TREE_TABLE)
                    .source(ruleSheet);

        int rowBound = getNonEmptyRowBound(ruleSheet.getTable());

        RuleDecisionTreeModel ret = parseInputs(model, ruleSheet,
                cell0.getRowSpan(), cell0.getColSpan(), rowBound,
                inputCell.getColSpan() + cell0.getColSpan());
        return ret;
    }

    private int getNonEmptyRowBound(ExcelTable table) {
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ICell cell = table.getCell(i, 0);
            if (cell == null)
                return i;
            String text = cell.getText();
            if (StringHelper.isEmpty(text))
                return i;
            i += cell.getMergeDown();
        }
        return table.getRowCount();
    }

    private RuleDecisionTreeModel parseInputs(RuleModel ruleModel,
                                              ExcelSheet sheet, int beginRow, int beginCol, int endRow, int endCol) {
        BaseTable tree = TreeTableHelper.buildTreeTable(sheet.getTable(), beginRow, beginCol, endRow, endCol, false);
        List<String> varNames = new ArrayList<>(endCol - beginCol);
        for (int i = beginCol; i < endCol; i++) {
            String varName = getInputVarName(ruleModel, sheet, beginRow - 1, i);
            varNames.add(varName);
        }

        RuleDecisionTreeModel ret = new RuleDecisionTreeModel();
        List<RuleDecisionTreeModel> rules = new ArrayList<>();

        for (int i = 0, n = tree.getRowCount(); i < n; i++) {
            TreeCell cell = (TreeCell) tree.getCell(i, 0);
            RuleDecisionTreeModel rule = buildInputRule(cell, varNames, sheet.getName(),
                    beginRow, beginCol);
            rules.add(rule);
            i += cell.getMergeDown();
        }

        ret.setChildren(rules);
        return ret;
    }

    private String getInputVarName(RuleModel ruleModel, ExcelSheet sheet, int rowIndex, int colIndex) {
        ExcelTable table = sheet.getTable();
        ExcelCell cell = (ExcelCell) table.getCell(rowIndex, colIndex).getRealCell();
        String text = cell.getText();
        text = StringHelper.strip(text);
        if (StringHelper.isEmpty(text))
            throw new NopException(ERR_RULE_VAR_CELL_TEXT_IS_EMPTY)
                    .source(table)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        if (cell.getMergeAcross() != 0)
            throw new NopException(ERR_RULE_VAR_CELL_SPAN_MUST_BE_ONE)
                    .source(table)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        ObjVarDefineModel var = ruleModel.getInputVar(text);
        if (var != null)
            return var.getName();

        Map<String, ValueWithLocation> vars = getCommentVars(cell, sheet.getName(), rowIndex, colIndex);
        if (vars == null)
            throw new NopException(ERR_RULE_UNKNOWN_INPUT_VAR)
                    .source(table)
                    .param(ARG_TEXT, text)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        ValueWithLocation vl = vars.get(RuleConstants.NAME_VAR);
        if (vl == null || vl.isEmpty())
            throw new NopException(ERR_RULE_UNKNOWN_INPUT_VAR)
                    .source(table)
                    .param(ARG_VAR_NAME, text)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        String varName = vl.asString();
        if (!StringHelper.isValidPropPath(varName))
            throw new NopException(ERR_RULE_INVALID_INPUT_VAR)
                    .source(table)
                    .param(ARG_TEXT, varName)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        // 有可能是访问input变量的属性
        String objName = StringHelper.firstPart(varName, '.');
        var = ruleModel.getInput(objName);
        if (var == null)
            throw new NopException(ERR_RULE_UNKNOWN_INPUT_VAR)
                    .source(table)
                    .param(ARG_TEXT, varName)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));
        return varName;
    }

    private RuleDecisionTreeModel buildInputRule(TreeCell cell, List<String> varNames,
                                                 String sheetName, int beginRow, int beginCol) {
        ExcelCell ec = (ExcelCell) cell.getValue();

        int rowIndex = beginRow + cell.getRowIndex();
        int colIndex = beginCol + cell.getColIndex();

        Map<String, ValueWithLocation> commentVars = getCommentVars(ec, sheetName, rowIndex, colIndex);
        String varName = varNames.get(cell.getTreeLevel());
        SourceLocation loc = null;
        String label = null;
        String expr = null;

        String id = null;

        if (commentVars != null) {
            ValueWithLocation vl = commentVars.get(RuleConstants.NAME_VALUE_EXPR);
            if (vl != null) {
                loc = vl.getLocation();
                label = ec.getText();
                expr = vl.asString();
            }
            id = getCommentVar(commentVars, RuleConstants.NAME_ID);
        }

        if (StringHelper.isEmpty(id))
            id = CellPosition.toABString(rowIndex, colIndex);

        if (expr == null) {
            expr = StringHelper.strip(ec.getText());
        }
        if (StringHelper.isEmpty(label))
            label = expr;

        if (loc == null)
            loc = getLocation(ec, sheetName, rowIndex, colIndex);

        TreeBean predicate = parsePredicate(loc, varName, expr);
        RuleDecisionTreeModel node = new RuleDecisionTreeModel();
        node.setId(id);
        node.setLocation(loc);
        if (predicate != null) {
            node.setPredicate(XNode.fromTreeBean(predicate));
        }
        node.setLabel(label);
        node.setMultiMatch(getMultiMatch(commentVars, false));

        List<TreeCell> children = cell.getChildren();
        if (children != null) {
            List<RuleDecisionTreeModel> ruleChildren = new ArrayList<>(children.size());
            for (TreeCell child : children) {
                RuleDecisionTreeModel ruleChild = buildInputRule(child, varNames, sheetName, beginRow, beginCol);
                ruleChildren.add(ruleChild);
            }
            node.setChildren(ruleChildren);
        }

        return node;
    }

    private TreeBean parsePredicate(SourceLocation loc, String varName, String text) {
        if ("-".equals(text) || StringHelper.isEmpty(text))
            return FilterBeans.alwaysTrue();

        Expression ruleExpr = new RuleExprParser(varName).parseExpr(loc, text);
        if (ruleExpr == null)
            return FilterBeans.alwaysTrue();

        return new ExpressionToFilterBeanTransformer().transform(ruleExpr);
    }

    private boolean getMultiMatch(Map<String, ValueWithLocation> commentVars, boolean defaultValue) {
        if (commentVars == null)
            return defaultValue;

        ValueWithLocation vl = commentVars.get(RuleConstants.NAME_MULTI_MATCH);
        Boolean b = vl == null ? null : ConvertHelper.toBoolean(vl.getValue());
        if (b == null)
            return defaultValue;
        return b;
    }

    private Map<String, ValueWithLocation> getCommentVars(ExcelCell cell, String sheetName, int rowIndex, int colIndex) {
        if (cell == null)
            return null;

        SourceLocation loc = getLocation(cell, sheetName, rowIndex, colIndex);
        return MultiLineConfigParser.INSTANCE.parseConfig(loc, cell.getComment());
    }

    private String getCommentVar(Map<String, ValueWithLocation> vars, String name) {
        if (vars == null)
            return null;

        ValueWithLocation vl = vars.get(name);
        return vl == null ? null : vl.asString();
    }

    private SourceLocation getLocation(ExcelCell cell, String sheetName, int rowIndex, int colIndex) {
        if (cell.getLocation() != null)
            return cell.getLocation();
        String path = "<excel>";
        return new SourceLocation(path, 0, 0, 0, 0, sheetName, CellPosition.toABString(rowIndex, colIndex), null);
    }
}