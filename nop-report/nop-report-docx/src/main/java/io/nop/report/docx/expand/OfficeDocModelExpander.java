package io.nop.report.docx.expand;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.commons.collections.KeyedList;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.office.doc.model.NopOfficeDocModelException;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeDocPageTemplateModel;
import io.nop.office.doc.model.OfficeDocTemplateModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficeListTemplateModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeParagraphTemplateModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeRunTemplateModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.OfficeTitleTemplateModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.doc.model.WordTableRowTemplateModel;
import io.nop.office.doc.model.WordTableTemplateModel;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.ExcelToXptModelTransformer;
import io.nop.report.core.build.XptModelInitializer;
import io.nop.report.core.engine.ExpandedSheetGenerator;
import io.nop.report.core.engine.XptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCol;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;
import io.nop.report.docx.support.WordTableToExcelTableTransformer;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;


import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * WI7: model -> model expansion of an {@link OfficeDocModel} (decision E1-E5 in
 * ai-dev/design/word-editor/online-word-model-design.md §19).
 *
 * <ul>
 * <li>E1: tables with table.model.renderType="xpt" go through the existing xpt
 * pipeline (WordTableToExcelTableTransformer + ExcelToXptModelTransformer +
 * XptModelInitializer + ExpandedSheetGenerator) and the ExpandedTable result is
 * mapped back into a new WordTable - no XML rewrite. The model layer only carries
 * row/col structure, merges, cell text, col width, row height and styleId.</li>
 * <li>E2: xpt cell expressions are compiled at expansion time with a fresh
 * XLangCompileTool; block-level template expressions (WI4) are already compiled
 * IEvalAction/IEvalPredicate and are evaluated directly.</li>
 * <li>E3: page-level loop replicates the page body block sequence; doc-level loop
 * replicates all pages; block-level loop repeats the whole block.
 * deleteAllAfterConfigTable is generation-time semantics and is ignored here.</li>
 * <li>E4: expand is a pure transformation; the input model is never modified.
 * Expanded tables keep table.model (renderType="xpt") so downstream links can
 * still recognize them.</li>
 * <li>E5: expansion failures (expression errors, missing dataset) fail fast via
 * the existing report pipeline exceptions; plain tables (no renderType="xpt")
 * are kept as-is.</li>
 * </ul>
 */
public class OfficeDocModelExpander {
    private static final String DEFAULT_TABLE_ID = "WordTable";

    private final XLangCompileTool cp;
    /**
     * WI8 (B2): image binding runs as an automatic post-processing phase at the
     * tail of expand; a custom binder can be injected (e.g. for tests simulating
     * a missing echarts classpath), null disables the binding phase.
     */
    private final DocImageBinder imageBinder;

    /**
     * WI9: number of xpt tables expanded by the most recent {@link #expand} call
     * (observability only; no behavioral effect).
     */
    private int lastExpandedTableCount;

    /**
     * WI9: number of images bound by the binding phase of the most recent
     * {@link #expand} call (observability only; no behavioral effect).
     */
    private int lastBoundImageCount;

    public OfficeDocModelExpander() {
        // E2: expansion-time compile context, same shape as
        // XptWordTableParser.fromCompileScope (new XLangCompileTool over a compile
        // scope); allowUnregisteredScopeVar so ds/field references resolve at
        // runtime rather than compile time.
        this(XLang.newCompileTool().allowUnregisteredScopeVar(true));
    }

    public OfficeDocModelExpander(XLangCompileTool cp) {
        this(cp, new DocImageBinder());
    }

    public OfficeDocModelExpander(XLangCompileTool cp, DocImageBinder imageBinder) {
        this.cp = cp;
        this.imageBinder = imageBinder;
    }

    /**
     * Convenience overload: data variables (e.g. the {@code data} dataset) are
     * placed into a fresh evaluation scope.
     */
    public OfficeDocModel expand(OfficeDocModel doc, Map<String, Object> dataScope) {
        // XptRuntime writes back into the scope (xptRt var, wrapped datasets), so a
        // mutable scope is required - the map-backed factory scope is read-only.
        IEvalScope scope = XLang.newEvalScope();
        if (dataScope != null) {
            for (Map.Entry<String, Object> entry : dataScope.entrySet()) {
                scope.setLocalValue(null, entry.getKey(), entry.getValue());
            }
        }
        return expand(doc, scope);
    }

    /**
     * Expand doc/page/block loops, conditional filters and xpt tables. Returns a
     * new OfficeDocModel; the input model is left untouched (E4).
     */
    public OfficeDocModel expand(OfficeDocModel doc, IEvalScope scope) {
        // WI9: per-call observability counters
        lastExpandedTableCount = 0;
        lastBoundImageCount = 0;

        OfficeDocModel result = new OfficeDocModel();
        result.setLocation(doc.getLocation());
        result.setWidth(doc.getWidth());
        result.setHeight(doc.getHeight());
        result.setBackground(doc.getBackground());
        result.setStyles(doc.getStyles());
        result.setAssets(doc.getAssets());

        OfficeDocTemplateModel docModel = doc.getModel();
        if (docModel != null && !pass(docModel.getTestExpr(), scope))
            return result;

        result.setPages(new KeyedList<>(p -> p.getName()));
        List<OfficeDocPageModel> outPages = result.getPages();

        Set<String> usedPageNames = new HashSet<>();
        for (PageIteration it : iterations(docModel, scope)) {
            IEvalScope childScope = it.scope;
            for (OfficeDocPageModel page : doc.getPages()) {
                expandPage(page, childScope, outPages, usedPageNames);
            }
        }

        // WI8 (B2): image binding post-processing at the tail of expand; images
        // without a binding definition are untouched
        if (imageBinder != null) {
            imageBinder.bind(result, scope);
            lastBoundImageCount = imageBinder.getLastBoundImageCount();
        }
        return result;
    }

    /**
     * WI9: number of xpt tables expanded by the most recent {@link #expand} call.
     */
    public int getLastExpandedTableCount() {
        return lastExpandedTableCount;
    }

    /**
     * WI9: number of images bound by the binding phase of the most recent
     * {@link #expand} call; 0 when the binding phase is disabled.
     */
    public int getLastBoundImageCount() {
        return lastBoundImageCount;
    }

    // ---- doc / page / block loop iteration -------------------------------

    private List<PageIteration> iterations(OfficeDocTemplateModel docModel, IEvalScope scope) {
        ILoopTemplateModel loopModel = docModel == null ? null : new DocModelLoopAdapter(docModel);
        List<PageIteration> ret = new ArrayList<>();
        Iterator<?> it = beginLoopIterator(loopModel, scope);
        if (it == null) {
            ret.add(new PageIteration(scope, 0));
            return ret;
        }
        int index = 0;
        while (it.hasNext()) {
            IEvalScope child = scope.newChildScope();
            child.setLocalValue(null, loopVarName(loopModel, XptConstants.WORKBOOK_LOOP_VAR), it.next());
            child.setLocalValue(null, loopIndexName(loopModel, XptConstants.WORKBOOK_LOOP_INDEX), index);
            ret.add(new PageIteration(child, index));
            index++;
        }
        return ret;
    }

    private void expandPage(OfficeDocPageModel page, IEvalScope scope, List<OfficeDocPageModel> outPages,
                            Set<String> usedPageNames) {
        OfficeDocPageTemplateModel pageModel = page.getModel();
        if (pageModel != null && !pass(pageModel.getTestExpr(), scope))
            return;

        ILoopTemplateModel loopModel = pageModel == null ? null : new PageModelLoopAdapter(pageModel);
        Iterator<?> loop = beginLoopIterator(loopModel, scope);
        if (loop == null) {
            outPages.add(copyPage(page, scope, usedPageNames));
            return;
        }
        int index = 0;
        while (loop.hasNext()) {
            IEvalScope child = scope.newChildScope();
            child.setLocalValue(null, loopVarName(loopModel, XptConstants.SHEET_LOOP_VAR), loop.next());
            child.setLocalValue(null, loopIndexName(loopModel, XptConstants.SHEET_LOOP_INDEX), index);
            outPages.add(copyPage(page, child, usedPageNames));
            index++;
        }
    }

    private OfficeDocPageModel copyPage(OfficeDocPageModel page, IEvalScope scope, Set<String> usedPageNames) {
        OfficeDocPageModel copy = new OfficeDocPageModel();
        copy.setLocation(page.getLocation());
        copy.setName(uniquePageName(page.getName(), usedPageNames));
        copy.setWidth(page.getWidth());
        copy.setHeight(page.getHeight());
        copy.setOrientation(page.getOrientation());
        copy.setMargins(page.getMargins());
        copy.setModel(page.getModel());
        copy.setHeader(expandBlocks(page.getHeader(), scope));
        copy.setFooter(expandBlocks(page.getFooter(), scope));
        copy.setBody(expandBlocks(page.getBody(), scope));
        return copy;
    }

    /**
     * pages is a KeyedList keyed by page name; loop copies must not collide or the
     * list would silently replace the earlier copy.
     */
    private String uniquePageName(String name, Set<String> usedNames) {
        if (name == null)
            return null;
        if (usedNames.add(name))
            return name;
        int index = 1;
        while (!usedNames.add(name + "_" + index)) {
            index++;
        }
        return name + "_" + index;
    }

    // ---- block sequence expansion ----------------------------------------

    private List<OfficeBlock> expandBlocks(List<OfficeBlock> blocks, IEvalScope scope) {
        List<OfficeBlock> out = new ArrayList<>();
        if (blocks == null)
            return out;
        for (OfficeBlock block : blocks) {
            expandBlock(block, scope, out);
        }
        return out;
    }

    private void expandBlock(OfficeBlock block, IEvalScope scope, List<OfficeBlock> out) {
        if (block instanceof WordTable) {
            expandTableBlock((WordTable) block, scope, out);
            return;
        }

        BlockLoop loop = blockLoop(block);
        if (loop == null) {
            if (passBlockTest(block, scope))
                out.add(block);
            return;
        }

        Iterator<?> it = loop.beginLoop(scope);
        if (it == null) {
            if (passBlockTest(block, scope))
                out.add(block);
            return;
        }
        int index = 0;
        while (it.hasNext()) {
            IEvalScope child = scope.newChildScope();
            child.setLocalValue(null, loop.loopVarName(), it.next());
            child.setLocalValue(null, loop.loopIndexName(), index);
            // Block copies share the block instance: expand is a read-only pass
            // and the docx/pdf output links never mutate blocks (E4). xpt tables
            // are the exception - they are materialized as new WordTable objects.
            // Paragraphs whose runs carry template value expressions are also
            // materialized: each copy renders its own evaluated text.
            if (!passBlockTest(block, child)) {
                index++;
                continue;
            }
            if (block instanceof OfficeParagraphModel
                    && hasRunValueExpr((OfficeParagraphModel) block)) {
                out.add(materializeParagraph((OfficeParagraphModel) block, child));
            } else {
                out.add(block);
            }
            index++;
        }
    }

    private boolean hasRunValueExpr(OfficeParagraphModel para) {
        if (para.getRuns() == null)
            return false;
        for (OfficeRun run : para.getRuns()) {
            if (run instanceof OfficeRunModel) {
                OfficeRunTemplateModel model = ((OfficeRunModel) run).getModel();
                if (model != null && model.getValueExpr() != null)
                    return true;
            }
        }
        return false;
    }

    /**
     * Shallow-copy a paragraph and evaluate its run template expressions into the copy's
     * text (the shared original is never mutated).
     */
    private OfficeBlock materializeParagraph(OfficeParagraphModel source, IEvalScope scope) {
        OfficeParagraphModel copy = new OfficeParagraphModel();
        copy.setId(source.getId());
        copy.setType(source.getType());
        copy.setStyle(source.getStyle());
        copy.setLocation(source.getLocation());
        if (source.getModel() != null)
            copy.setModel(source.getModel());
        if (source.getRuns() != null) {
            for (OfficeRun run : source.getRuns()) {
                if (run instanceof OfficeRunModel) {
                    OfficeRunModel src = (OfficeRunModel) run;
                    OfficeRunModel rc = new OfficeRunModel();
                    rc.setId(src.getId());
                    rc.setType(src.getType());
                    rc.setStyle(src.getStyle());
                    rc.setModel(src.getModel());
                    rc.setLocation(src.getLocation());
                    rc.setT(src.getT());
                    OfficeRunTemplateModel model = src.getModel();
                    if (model != null && model.getValueExpr() != null) {
                        Object value = model.getValueExpr().invoke(scope);
                        rc.setT(value == null ? null : String.valueOf(value));
                    }
                    copy.addRun(rc);
                } else {
                    copy.addRun(run);
                }
            }
        }
        return copy;
    }

    private void expandTableBlock(WordTable table, IEvalScope scope, List<OfficeBlock> out) {
        WordTableTemplateModelAdapter adapter = new WordTableTemplateModelAdapter(table.getModel());
        if (!adapter.pass(scope))
            return;

        if (!"xpt".equals(adapter.renderType())) {
            // E5: plain tables are kept unchanged
            out.add(table);
            return;
        }

        WordTable filtered = filterRowsByTestExpr(table, scope);
        out.add(expandXptTable(filtered, table, scope));
    }

    // ---- xpt table expansion (E1) ----------------------------------------

    private WordTable expandXptTable(WordTable template, WordTable original, IEvalScope scope) {
        ExcelTable excelTable = WordTableToExcelTableTransformer.transform(template);
        ExcelToXptModelTransformer.INSTANCE.transformTable(excelTable);

        ExcelSheet sheet = new ExcelSheet();
        sheet.setName(DEFAULT_TABLE_ID);
        sheet.setTable(excelTable);
        new XptModelInitializer(cp).buildSheetModel(sheet);

        XptRuntime xptRt = new XptRuntime(scope);
        ExcelWorkbook wk = new ExcelWorkbook();
        xptRt.setWorkbook(wk);
        ExpandedSheet expandedSheet = new ExpandedSheetGenerator(wk).generateSheet(sheet, xptRt, new HashMap<>());
        ExpandedTable expanded = expandedSheet.getTable();
        expanded.assignRowIndexAndColIndex();

        WordTable mapped = mapExpandedTable(expanded, original);
        lastExpandedTableCount++;
        return mapped;
    }

    /**
     * Maps an ExpandedTable back into a new WordTable. Only the faces the document
     * model can carry are preserved (E1): row/col structure, merges, cell text,
     * column width, row height and styleId. Template-position cells keep their
     * original WordTableCellTemplateModel (matched by original row/col index);
     * cells created by expansion carry no cell model.
     */
    private WordTable mapExpandedTable(ExpandedTable expanded, WordTable original) {
        WordTable result = new WordTable();
        result.setLocation(original.getLocation());
        result.setId(original.getId());
        result.setStyleId(expanded.getStyleId() != null ? expanded.getStyleId() : original.getStyleId());
        result.setHeaderCount(original.getHeaderCount());
        result.setSideCount(original.getSideCount());
        result.setFooterCount(original.getFooterCount());
        result.setModel(original.getModel());

        int colCount = expanded.getColCount();
        for (int c = 0; c < colCount; c++) {
            WordTableColumnConfig col = new WordTableColumnConfig();
            ExpandedCol expandedCol = expanded.getCol(c);
            if (expandedCol != null) {
                col.setWidth(expandedCol.getWidth(XptConstants.DEFAULT_WIDTH));
                col.setStyleId(expandedCol.getStyleId());
                ExcelColumnConfig colModel = expandedCol.getColModel();
                if (colModel != null)
                    col.setHidden(colModel.isHidden());
            }
            result.getCols().add(col);
        }

        for (int r = 0, rowCount = expanded.getRowCount(); r < rowCount; r++) {
            ExpandedRow expandedRow = expanded.getRow(r);
            if (expandedRow == null || expandedRow.isRemoved())
                continue;
            WordTableRow row = result.makeRow(result.getRowCount());
            row.setHeight(expandedRow.getHeight());
            row.setStyleId(expandedRow.getStyleId());

            for (int c = 0; c < colCount; c++) {
                ExpandedCell cell = expandedRow.getCell(c);
                if (cell == null || cell.isProxyCell() || cell.getColOffset() > 0)
                    continue;

                WordTableCell newCell = result.newCell();
                newCell.setStyleId(cell.getStyleId());
                newCell.setValue(cell.getText());
                newCell.setMergeAcross(cell.getMergeAcross());
                newCell.setMergeDown(cell.getMergeDown());
                WordTableCell templateCell = originalTemplateCell(original, r, c);
                if (templateCell != null) {
                    newCell.setLocation(templateCell.getLocation());
                    newCell.setModel(templateCell.getModel());
                }
                result.setCell(row.getRowIndex(), c, newCell);
            }
        }
        return result;
    }

    private WordTableCell originalTemplateCell(WordTable original, int rowIndex, int colIndex) {
        if (original == null || rowIndex >= original.getRowCount() || colIndex >= original.getColCount())
            return null;
        WordTableRow row = original.getRow(rowIndex);
        if (row == null)
            return null;
        WordTableCell cell = (WordTableCell) row.getCell(colIndex);
        if (cell == null || cell.isProxyCell())
            return null;
        return cell;
    }

    /**
     * Removes template rows whose row.model.testExpr / visibleExpr evaluates to
     * false before xpt conversion. The input table is not modified.
     */
    private WordTable filterRowsByTestExpr(WordTable table, IEvalScope scope) {
        boolean anyRemoved = false;
        Set<Integer> keepIndexes = new HashSet<>();
        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            WordTableRow row = table.getRow(i);
            if (row == null) {
                anyRemoved = true;
                continue;
            }
            WordTableRowTemplateModel rowModel = row.getModel();
            if (!pass(rowModel == null ? null : rowModel.getTestExpr(), scope)
                    || !passVisible(rowModel == null ? null : rowModel.getVisibleExpr(), scope)) {
                anyRemoved = true;
                continue;
            }
            keepIndexes.add(i);
        }
        if (!anyRemoved)
            return table;

        WordTable filtered = table.cloneInstance();
        for (int i = filtered.getRowCount() - 1; i >= 0; i--) {
            if (!keepIndexes.contains(i))
                filtered.removeRow(i);
        }
        return filtered;
    }

    // ---- template model adapters -----------------------------------------

    private Iterator<?> beginLoopIterator(ILoopTemplateModel model, IEvalScope scope) {
        if (model == null || (model.getBeginLoop() == null && model.getLoopItemsName() == null))
            return null;

        Object c = null;
        if (model.getBeginLoop() != null)
            c = model.getBeginLoop().invoke(scope);
        if (model.getLoopItemsName() != null)
            c = scope.getValueByPropPath(model.getLoopItemsName());
        return toIterator(c);
    }

    private Iterator<?> toIterator(Object c) {
        if (c == null)
            return null;
        if (c instanceof Iterator)
            return (Iterator<?>) c;
        if (c instanceof Iterable)
            return ((Iterable<?>) c).iterator();
        throw new NopOfficeDocModelException(
                "loop items must be an Iterable or Iterator, actual: " + c.getClass().getName());
    }

    private boolean pass(IEvalPredicate predicate, IEvalScope scope) {
        return predicate == null || predicate.passConditions(scope);
    }

    private boolean passVisible(IEvalAction visibleExpr, IEvalScope scope) {
        if (visibleExpr == null)
            return true;
        Object value = visibleExpr.invoke(scope);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    private String loopVarName(ILoopTemplateModel model, String defaultName) {
        String name = model == null ? null : model.getLoopVarName();
        return StringHelper.isEmpty(name) ? defaultName : name;
    }

    private String loopIndexName(ILoopTemplateModel model, String defaultName) {
        String name = model == null ? null : model.getLoopIndexName();
        return StringHelper.isEmpty(name) ? defaultName : name;
    }

    /**
     * Uniform view over the loop/test fields that WI4 compiles onto the block
     * template models. Tables only carry testExpr (no loop fields in
     * word-table.xdef); paragraphs/titles/lists carry loop + test + visible.
     */
    private interface ILoopTemplateModel {
        IEvalAction getBeginLoop();

        String getLoopItemsName();

        String getLoopVarName();

        String getLoopIndexName();
    }

    private BlockLoop blockLoop(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) {
            return new BlockLoop(new ParagraphLoopAdapter(((OfficeParagraphModel) block).getModel()));
        }
        if (block instanceof OfficeTitleModel) {
            return new BlockLoop(new TitleLoopAdapter(((OfficeTitleModel) block).getModel()));
        }
        if (block instanceof OfficeListModel) {
            return new BlockLoop(new ListLoopAdapter(((OfficeListModel) block).getModel()));
        }
        return null;
    }

    private boolean passBlockTest(OfficeBlock block, IEvalScope scope) {
        if (block instanceof OfficeParagraphModel) {
            OfficeParagraphTemplateModel model = ((OfficeParagraphModel) block).getModel();
            return pass(model == null ? null : model.getTestExpr(), scope)
                    && passVisible(model == null ? null : model.getVisibleExpr(), scope);
        }
        if (block instanceof OfficeTitleModel) {
            OfficeTitleTemplateModel model = ((OfficeTitleModel) block).getModel();
            return pass(model == null ? null : model.getTestExpr(), scope)
                    && passVisible(model == null ? null : model.getVisibleExpr(), scope);
        }
        if (block instanceof OfficeListModel) {
            OfficeListTemplateModel model = ((OfficeListModel) block).getModel();
            return pass(model == null ? null : model.getTestExpr(), scope)
                    && passVisible(model == null ? null : model.getVisibleExpr(), scope);
        }
        return true;
    }

    private abstract static class AbstractLoopAdapter implements ILoopTemplateModel {
        protected final IEvalAction beginLoop;
        protected final String loopItemsName;
        protected final String loopVarName;
        protected final String loopIndexName;

        protected AbstractLoopAdapter(IEvalAction beginLoop, String loopItemsName,
                                      String loopVarName, String loopIndexName) {
            this.beginLoop = beginLoop;
            this.loopItemsName = loopItemsName;
            this.loopVarName = loopVarName;
            this.loopIndexName = loopIndexName;
        }

        @Override
        public IEvalAction getBeginLoop() {
            return beginLoop;
        }

        @Override
        public String getLoopItemsName() {
            return loopItemsName;
        }

        @Override
        public String getLoopVarName() {
            return loopVarName;
        }

        @Override
        public String getLoopIndexName() {
            return loopIndexName;
        }
    }

    private static class DocModelLoopAdapter extends AbstractLoopAdapter {
        DocModelLoopAdapter(OfficeDocTemplateModel model) {
            super(model.getBeginLoop(), model.getLoopItemsName(),
                    model.getLoopVarName(), model.getLoopIndexName());
        }
    }

    private static class PageModelLoopAdapter extends AbstractLoopAdapter {
        PageModelLoopAdapter(OfficeDocPageTemplateModel model) {
            super(model.getBeginLoop(), model.getLoopItemsName(),
                    model.getLoopVarName(), model.getLoopIndexName());
        }
    }

    private static class ParagraphLoopAdapter extends AbstractLoopAdapter {
        ParagraphLoopAdapter(OfficeParagraphTemplateModel model) {
            super(model == null ? null : model.getBeginLoop(),
                    model == null ? null : model.getLoopItemsName(),
                    model == null ? null : model.getLoopVarName(),
                    model == null ? null : model.getLoopIndexName());
        }
    }

    private static class TitleLoopAdapter extends AbstractLoopAdapter {
        TitleLoopAdapter(OfficeTitleTemplateModel model) {
            super(model == null ? null : model.getBeginLoop(),
                    model == null ? null : model.getLoopItemsName(),
                    model == null ? null : model.getLoopVarName(),
                    model == null ? null : model.getLoopIndexName());
        }
    }

    private static class ListLoopAdapter extends AbstractLoopAdapter {
        ListLoopAdapter(OfficeListTemplateModel model) {
            super(model == null ? null : model.getBeginLoop(),
                    model == null ? null : model.getLoopItemsName(),
                    model == null ? null : model.getLoopVarName(),
                    model == null ? null : model.getLoopIndexName());
        }
    }

    private class BlockLoop {
        private final ILoopTemplateModel model;

        BlockLoop(ILoopTemplateModel model) {
            this.model = model;
        }

        Iterator<?> beginLoop(IEvalScope scope) {
            return beginLoopIterator(model, scope);
        }

        String loopVarName() {
            return StringHelper.isEmpty(model.getLoopVarName()) ? "it" : model.getLoopVarName();
        }

        String loopIndexName() {
            return StringHelper.isEmpty(model.getLoopIndexName()) ? "it_index" : model.getLoopIndexName();
        }
    }

    /**
     * WordTableTemplateModel has testExpr only (no loop fields); rendered as an
     * adapter so expandTableBlock stays uniform with the other blocks.
     */
    private static class WordTableTemplateModelAdapter {
        private final WordTableTemplateModel model;

        WordTableTemplateModelAdapter(WordTableTemplateModel model) {
            this.model = model;
        }

        boolean pass(IEvalScope scope) {
            return model == null || model.getTestExpr() == null
                    || model.getTestExpr().passConditions(scope);
        }

        String renderType() {
            return model == null ? null : model.getRenderType();
        }
    }

    private static class PageIteration {
        final IEvalScope scope;
        final int index;

        PageIteration(IEvalScope scope, int index) {
            this.scope = scope;
            this.index = index;
        }
    }
}
