package io.nop.rule.core.excel;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.ITable;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.core.model.table.tree.TreeCell;
import io.nop.core.model.table.tree.TreeTableHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.excel.ExcelConstants;
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
import io.nop.rule.core.execute.RuleOutputAction;
import io.nop.rule.core.expr.RuleExprParser;
import io.nop.rule.core.model.RuleDecisionMatrixModel;
import io.nop.rule.core.model.RuleDecisionTreeModel;
import io.nop.rule.core.model.RuleModel;
import io.nop.rule.core.model.RuleOutputValueModel;
import io.nop.rule.core.model.RuleTableCellModel;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.exec.NullExecutable;
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
import static io.nop.rule.core.RuleErrors.ERR_RULE_INVALID_OUTPUT_CELL;
import static io.nop.rule.core.RuleErrors.ERR_RULE_NOT_ALLOW_MERGED_CELL;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_INPUT_VAR;
import static io.nop.rule.core.RuleErrors.ERR_RULE_UNKNOWN_OUTPUT_VAR;
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

        String type = StringHelper.strip(ruleSheet.getTable().getCellText(0, 0));
        if (RuleConstants.RULE_TYPE_MATRIX.equals(type)) {
            RuleDecisionMatrixModel decisionMatrix = parseDecisionMatrix(model, ruleSheet);
            model.setDecisionMatrix(decisionMatrix);
        } else {
            RuleDecisionTreeModel decisionTable = parseDecisionTree(model, ruleSheet);
            model.setDecisionTree(decisionTable);
        }
        return model;
    }

    private RuleModel parseRuleConfig(ExcelWorkbook wk) {
        ExcelSheet configSheet = wk.getSheet(RuleConstants.SHEET_NAME_CONFIG);
        if (configSheet == null)
            throw new NopException(ERR_RULE_WORKBOOK_NO_CONFIG_SHEET)
                    .source(wk);

        ImportModel importModel = ImportModelHelper.getImportModel(RuleConstants.IMP_PATH_RULE);
        ImportSheetModel sheetModel = importModel.getSheet(RuleConstants.SHEET_NAME_CONFIG);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(ExcelConstants.VAR_WORKBOOK, wk);
        RuleModel rule = ImportModelHelper.parseSheet(sheetModel, configSheet, compileTool, scope, RuleModel.class);
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

        ExcelCell outputCell = (ExcelCell) ruleSheet.getTable().getCell(0, 2);
        if (outputCell == null)
            throw new NopException(ERR_RULE_INVALID_DECISION_TREE_TABLE)
                    .source(ruleSheet);

        int endRow = getNonEmptyRowBound(ruleSheet.getTable(), cell0.getRowSpan());

        int beginRow = cell0.getRowSpan();
        int beginCol = cell0.getColSpan();
        int endCol = beginCol + inputCell.getColSpan();

        BaseTable tree = TreeTableHelper.buildTreeTable(ruleSheet.getTable(),
                beginRow, beginCol, endRow, endCol, false);

        RuleDecisionTreeModel ret = parseInputs(model, ruleSheet,
                cell0.getRowSpan(), cell0.getColSpan(), tree);

        List<String> outputVarNames = parseOutputVarNames(model, ruleSheet, beginRow - 1,
                endCol, endCol + outputCell.getColSpan());

        for (int i = 0, n = tree.getRowCount(); i < n; i++) {
            TreeCell cell = (TreeCell) tree.getCell(i, tree.getColCount() - 1).getRealCell();
            parseOutputVars(cell, outputVarNames, model, ruleSheet, beginRow, endCol);
        }

        return ret;
    }

    private int getNonEmptyRowBound(ExcelTable table, int start) {
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
                                              ExcelSheet sheet, int beginRow, int beginCol, BaseTable tree) {
        int endCol = beginCol + tree.getColCount();

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
                    beginRow, beginCol, false);
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
                                                 String sheetName, int beginRow, int beginCol, boolean vertical) {
        String varName = varNames.get(vertical ? cell.getRowIndex() : cell.getColIndex());
        int rowIndex = beginRow + cell.getRowIndex();
        int colIndex = beginCol + cell.getColIndex();

        RuleDecisionTreeModel node = buildRuleNode(cell, varName, sheetName, rowIndex, colIndex);

        List<TreeCell> children = cell.getChildren();
        if (children != null) {
            List<RuleDecisionTreeModel> ruleChildren = new ArrayList<>(children.size());
            for (TreeCell child : children) {
                RuleDecisionTreeModel ruleChild = buildInputRule(child, varNames, sheetName, beginRow, beginCol, vertical);
                ruleChildren.add(ruleChild);
            }
            node.setChildren(ruleChildren);
        }

        return node;
    }

    private RuleDecisionTreeModel buildRuleNode(TreeCell cell, String varName, String sheetName, int rowIndex, int colIndex) {
        ExcelCell ec = (ExcelCell) cell.getValue();


        Map<String, ValueWithLocation> commentVars = getCommentVars(ec, sheetName, rowIndex, colIndex);
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

            // 如果单元格上明确设置了var，则以明确设置的值为准
            String localVar = getCommentVar(commentVars, RuleConstants.NAME_VAR);
            if (!StringHelper.isEmpty(localVar)) {
                varName = localVar;
            }
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
        node.setLeafIndex(cell.getLeafIndex());
        cell.setModel(node);
        return node;
    }

    private List<String> parseOutputVarNames(RuleModel ruleModel, ExcelSheet sheet, int rowIndex, int beginCol, int endCol) {
        List<String> varNames = new ArrayList<>(endCol - beginCol);
        for (int i = beginCol; i < endCol; i++) {
            String varName = getOutputVarName(ruleModel, sheet, rowIndex, i);
            varNames.add(varName);
        }
        return varNames;
    }

    private void parseOutputVars(TreeCell cell, List<String> outputVarNames, RuleModel ruleModel, ExcelSheet sheet,
                                 int beginRow, int outputCol) {
        RuleDecisionTreeModel rule = (RuleDecisionTreeModel) cell.getModel();
        ExcelTable table = sheet.getTable();
        for (int i = 0, n = outputVarNames.size(); i < n; i++) {
            String varName = outputVarNames.get(i);
            int rowIndex = cell.getRowIndex() + beginRow;
            int colIndex = outputCol + i;
            ExcelCell outputCell = (ExcelCell) getRealCell(table, rowIndex, colIndex);

            RuleOutputValueModel outputModel = new RuleOutputValueModel();
            outputModel.setName(varName);
            outputModel.setVarModel(ruleModel.getOutputVar(varName));

            IEvalAction action = parseOutputAction(outputCell, sheet.getName(), varName, rowIndex, colIndex);
            outputModel.setValueExpr(action);

            rule.addOutput(outputModel);
        }
    }

    private ICell getRealCell(ITable table, int rowIndex, int colIndex) {
        ICell cell = table.getCell(rowIndex, colIndex);
        if (cell == null)
            return null;
        return cell.getRealCell();
    }

    private ExcelCell requireRealCell(ExcelTable table, int rowIndex, int colIndex) {
        ICell cell = table.getCell(rowIndex, colIndex);
        if (cell == null)
            return null;
        if (cell.isProxyCell())
            throw new NopException(ERR_RULE_NOT_ALLOW_MERGED_CELL)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));
        return (ExcelCell) cell;
    }

    private String getOutputVarName(RuleModel ruleModel, ExcelSheet sheet, int rowIndex, int colIndex) {
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

        ObjVarDefineModel var = ruleModel.getOutputVar(text);
        if (var != null)
            return var.getName();

        Map<String, ValueWithLocation> vars = getCommentVars(cell, sheet.getName(), rowIndex, colIndex);
        if (vars == null)
            throw new NopException(ERR_RULE_UNKNOWN_OUTPUT_VAR)
                    .source(table)
                    .param(ARG_TEXT, text)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        ValueWithLocation vl = vars.get(RuleConstants.NAME_VAR);
        if (vl == null || vl.isEmpty())
            throw new NopException(ERR_RULE_UNKNOWN_OUTPUT_VAR)
                    .source(table)
                    .param(ARG_VAR_NAME, text)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));

        String varName = vl.asString();
        var = ruleModel.getOutputVar(varName);
        if (var == null)
            throw new NopException(ERR_RULE_UNKNOWN_OUTPUT_VAR)
                    .source(table)
                    .param(ARG_TEXT, varName)
                    .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, colIndex));
        return varName;
    }

    private TreeBean parsePredicate(SourceLocation loc, String varName, String text) {
        if (StringHelper.isBlank(text) || "-".equals(text.trim()))
            return FilterBeans.alwaysTrue();

        Expression ruleExpr = new RuleExprParser(varName).parseExpr(loc, text);
        if (ruleExpr == null)
            return FilterBeans.alwaysTrue();

        return new ExpressionToFilterBeanTransformer().transform(ruleExpr);
    }

    private IEvalAction parseOutputAction(ExcelCell cell,
                                          String sheetName, String varName,
                                          int rowIndex, int colIndex) {

        String text = null;
        if (cell != null) {
            Map<String, ValueWithLocation> commentVars = getCommentVars(cell, sheetName,
                    rowIndex, colIndex);

            if (commentVars != null) {
                ValueWithLocation vl = commentVars.get(RuleConstants.NAME_VALUE_EXPR);
                if (vl != null) {
                    text = vl.asString();
                }
            }
            if (text == null) {
                text = cell.getText();
            }
        }
        SourceLocation loc = getLocation(cell, sheetName, rowIndex, colIndex);
        return parseOutputAction(loc, varName, text);
    }

    private IEvalAction parseOutputAction(SourceLocation loc, String varName, String text) {
        if (StringHelper.isBlank(text) || "-".equals(text.trim())) {
            return new EvalCode(loc, "", new RuleOutputAction(varName, new ExprEvalAction(NullExecutable.NULL)));
        }

        return new EvalCode(loc, text, new RuleOutputAction(varName, compileTool.compileSimpleExpr(loc, text)));
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

    private RuleDecisionMatrixModel parseDecisionMatrix(RuleModel ruleModel, ExcelSheet sheet) {
        ExcelTable table = sheet.getTable();
        ExcelCell cell0 = (ExcelCell) table.getCell(0, 0);

        int outBeginRow = cell0.getRowSpan();
        int outEndRow = getNonEmptyRowBound(table, outBeginRow);
        int outBeginCol = cell0.getColSpan();
        int outEndCol = getNonEmptyColBound(table, outBeginCol);

        BaseTable top = TreeTableHelper.buildTreeTable(table,
                0, outBeginCol, outBeginRow, outEndCol, true);

        BaseTable left = TreeTableHelper.buildTreeTable(table,
                outBeginRow, 0, outEndRow, outBeginCol, false);

        String sheetName = sheet.getName();

        RuleDecisionTreeModel rowDecider = buildRowDecider(left, outBeginRow, sheetName, ruleModel);
        RuleDecisionTreeModel colDecider = buildColDecider(top, outBeginCol, sheetName, ruleModel);

        RuleDecisionMatrixModel ret = new RuleDecisionMatrixModel();
        ret.setRowDecider(rowDecider);
        ret.setColDecider(colDecider);

        parseMatrixOutputs(ret, sheet, outBeginRow, outBeginCol, outEndRow, outEndCol, ruleModel);
        return ret;
    }

    private int getNonEmptyColBound(ExcelTable table, int start) {
        for (int i = start, n = table.getColCount(); i < n; i++) {
            ICell cell = table.getCell(0, i);
            if (cell == null)
                return i;
            String text = cell.getText();
            if (StringHelper.isEmpty(text))
                return i;
            i += cell.getMergeAcross();
        }
        return table.getColCount();
    }

    private RuleDecisionTreeModel buildRowDecider(BaseTable table, int beginRow, String sheetName, RuleModel ruleModel) {
        List<String> rowVarNames = getRowVarNames(table, sheetName, beginRow, ruleModel);

        RuleDecisionTreeModel ret = new RuleDecisionTreeModel();
        List<RuleDecisionTreeModel> children = new ArrayList<>();
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            TreeCell cell = (TreeCell) table.getCell(i, 0);
            RuleDecisionTreeModel rule = buildInputRule(cell, rowVarNames, sheetName, beginRow, 0, false);
            children.add(rule);
            i += cell.getMergeDown();
        }
        ret.setChildren(children);
        return ret;
    }

    private RuleDecisionTreeModel buildColDecider(BaseTable table, int beginCol, String sheetName, RuleModel ruleModel) {
        List<String> rowVarNames = getColVarNames(table, sheetName, beginCol, ruleModel);

        RuleDecisionTreeModel ret = new RuleDecisionTreeModel();
        List<RuleDecisionTreeModel> children = new ArrayList<>();
        for (int i = 0, n = table.getColCount(); i < n; i++) {
            TreeCell cell = (TreeCell) table.getCell(0, i);
            RuleDecisionTreeModel rule = buildInputRule(cell, rowVarNames, sheetName, 0, beginCol, true);
            children.add(rule);
            i += cell.getMergeAcross();
        }
        ret.setChildren(children);
        return ret;
    }

    private List<String> getRowVarNames(BaseTable table, String sheetName, int beginRow, RuleModel ruleModel) {
        List<String> varNames = new ArrayList<>(table.getColCount());

        for (int i = 0, n = table.getColCount(); i < n; i++) {
            TreeCell cell = (TreeCell) getRealCell(table, 0, i);
            String varName = getVarName(cell, sheetName, beginRow, i, ruleModel);
            varNames.add(varName);
        }
        return varNames;
    }

    private List<String> getColVarNames(BaseTable table, String sheetName, int beginCol, RuleModel ruleModel) {
        List<String> varNames = new ArrayList<>(table.getRowCount());

        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            TreeCell cell = (TreeCell) getRealCell(table, i, 0);
            String varName = getVarName(cell, sheetName, i, beginCol, ruleModel);
            varNames.add(varName);
        }
        return varNames;
    }

    private String getVarName(TreeCell cell, String sheetName, int rowIndex, int colIndex, RuleModel ruleModel) {
        if (cell == null) {
            SourceLocation loc = getLocation(null, sheetName, rowIndex, colIndex);
            throw new NopException(ERR_RULE_INVALID_INPUT_VAR)
                    .loc(loc).param(ARG_VAR_NAME, null);
        }

        ExcelCell ec = (ExcelCell) cell.getValue();
        Map<String, ValueWithLocation> commentVars = getCommentVars(ec, sheetName, rowIndex, colIndex);
        String varName = getCommentVar(commentVars, RuleConstants.NAME_VAR);
        String objName = StringHelper.firstPart(varName, '.');

        ObjVarDefineModel varDef = ruleModel.getInputVar(objName);
        if (varDef == null) {
            SourceLocation loc = getLocation(ec, sheetName, rowIndex, colIndex);
            throw new NopException(ERR_RULE_INVALID_INPUT_VAR)
                    .loc(loc).param(ARG_VAR_NAME, objName);
        }

        return objName.equals(varName) ? varDef.getName() : varName;
    }

    private void parseMatrixOutputs(RuleDecisionMatrixModel ret, ExcelSheet sheet,
                                    int outBeginRow, int outBeginCol,
                                    int outEndRow, int outEndCol, RuleModel ruleModel) {
        ExcelTable table = sheet.getTable();
        String sheetName = sheet.getName();

        int rowLeafIndex = 0;
        for (int i = outBeginRow; i < outEndRow; i++) {
            ExcelCell leftCell = (ExcelCell) getRealCell(table, i, outBeginCol - 1);

            int colLeafIndex = 0;
            for (int j = outBeginCol; j < outEndCol; j++) {
                ExcelCell topCell = (ExcelCell) getRealCell(table, outBeginRow - 1, j);

                RuleTableCellModel cellModel = new RuleTableCellModel();
                cellModel.setPos(CellPosition.of(rowLeafIndex, colLeafIndex));

                ExcelCell cell = requireRealCell(table, i, j);
                if (isSingleCell(leftCell, topCell, cell)) {
                    // 单个值
                    IEvalAction outAction = parseOutputAction(cell, sheetName, RuleConstants.VAR_RESULT, i, j);
                    RuleOutputValueModel output = new RuleOutputValueModel();
                    output.setName(RuleConstants.VAR_RESULT);
                    output.setValueExpr(outAction);
                    cellModel.addOutput(output);
                } else {
                    if (leftCell.getRowSpan() != 2) {
                        throw new NopException(ERR_RULE_INVALID_OUTPUT_CELL)
                                .param(ARG_CELL_POS, CellPosition.toABString(i, j));
                    }
                    parseOutputCell(ruleModel, cellModel, sheet, topCell, i, j);
                }

                ret.addCell(cellModel);
                colLeafIndex++;
                j += topCell.getMergeAcross();
            }
            rowLeafIndex++;
            i += leftCell.getMergeDown();
        }
    }

    private void parseOutputCell(RuleModel ruleModel, RuleTableCellModel cellModel,
                                 ExcelSheet sheet, ExcelCell topCell, int rowIndex, int colIndex) {
        ExcelTable table = sheet.getTable();

        for (int i = 0, n = topCell.getColSpan(); i < n; i++) {
            ExcelCell cell = requireRealCell(table, rowIndex, colIndex + i);
            if (cell == null || cell.getColSpan() != 1 || cell.getRowSpan() != 1)
                throw new NopException(ERR_RULE_INVALID_OUTPUT_CELL)
                        .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, i + colIndex));

            String outputVar = getOutputVarName(ruleModel, sheet, rowIndex, colIndex + i);
            if (StringHelper.isEmpty(outputVar))
                throw new NopException(ERR_RULE_INVALID_OUTPUT_CELL)
                        .param(ARG_CELL_POS, CellPosition.toABString(rowIndex, i + colIndex));

            ExcelCell valueCell = requireRealCell(table, rowIndex + 1, colIndex + i);
            IEvalAction outAction = this.parseOutputAction(valueCell, sheet.getName(), outputVar, rowIndex + 1, colIndex + i);


            RuleOutputValueModel output = new RuleOutputValueModel();
            output.setName(outputVar);
            output.setValueExpr(outAction);
            cellModel.addOutput(output);
        }
    }

    private boolean isSingleCell(ExcelCell leftCell, ExcelCell topCell, ExcelCell cell) {
        if (cell == null) {
            return leftCell.getRowSpan() == 1 && topCell.getColSpan() == 1;
        }
        return leftCell.getRowSpan() == cell.getRowSpan() && topCell.getColSpan() == cell.getColSpan();
    }
}