/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.mutable.MutableBoolean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;
import io.nop.excel.ExcelConstants;
import io.nop.excel.imp.model.IFieldContainer;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_CELL_POS;
import static io.nop.excel.ExcelErrors.ARG_ALLOWED_NAMES;
import static io.nop.excel.ExcelErrors.ARG_FIELD_NAME;
import static io.nop.excel.ExcelErrors.ARG_ROW_NUMBER;
import static io.nop.excel.ExcelErrors.ARG_SHEET_NAME;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_INVALID_DATA_ROW;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_UNKNOWN_FIELD;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_UNKNOWN_GROUP_FIELD;

public class TreeTableDataParser {
    private final IEvalScope scope;
    private String sheetName;

    public TreeTableDataParser(IEvalScope scope) {
        this.scope = scope;
    }

    public void parse(String sheetName, ITableView table, ImportSheetModel sheetModel, ITableDataEventListener listener) {
        listener.beginSheet(sheetName, sheetModel);
        this.sheetName = sheetName;

        int rowIndex = skipNonDataRow(table, 0);
        if (rowIndex >= 0) {
            if (sheetModel.isList()) {
                CellRange range = parseListSheet(sheetName, table, rowIndex, sheetModel, listener);
                rowIndex = skipNonDataRow(table, range.getLastRowIndex() + 1);
                if (rowIndex >= 0) {
                    throw new NopException(ERR_IMPORT_INVALID_DATA_ROW).param(ARG_SHEET_NAME, sheetName)
                            .param(ARG_ROW_NUMBER, rowIndex + 1);
                }
            } else {
                parseFields(sheetName, sheetModel, table, rowIndex, 0,
                        table.getRowCount() - 1, table.getColCount() - 1, listener);
            }
        }

        listener.endSheet(sheetModel);
    }

    private CellRange parseListSheet(String sheetName, ITableView table, int rowIndex, ImportSheetModel sheetModel,
                                     ITableDataEventListener listener) {
        int maxRowIndex = table.getRowCount() - 1;
        int maxColIndex = table.getColCount() - 1;

        ICellView cell = table.getCell(rowIndex, 0);
        // 如果名称占据多行，则字段值放置在右侧, 否则放在下方
        boolean alignRight = cell.getMergeDown() > 0;

        String text = cell.getText();
        CellRange dataRange;
        // 如果headerCell的内容为数字，则表示它实际对应于编号。整个列表是采用card模式显示
        if (StringHelper.isNumber(text)) {
            dataRange = parseCardListData(sheetName, sheetModel, table, rowIndex, 0, maxRowIndex,
                    maxColIndex, listener);
        } else {
            // 第一行为表头，后续行为内容，直到发现编号列为空
            dataRange = parseListData(sheetName, sheetModel, table, rowIndex, 0, maxRowIndex,
                    maxColIndex, listener);
        }
        if (alignRight) {
            maxColIndex = dataRange.getLastRowIndex();
        } else {
            maxRowIndex = dataRange.getLastRowIndex();
        }

        return new CellRange(rowIndex, 0, maxRowIndex, maxColIndex);
    }

    private CellRange parseCardListData(String sheetName,
                                        IFieldContainer field, ITableView table, int rowIndex,
                                        int colIndex, int maxRowIndex, int maxColIndex, ITableDataEventListener listener) {

        listener.beginList(rowIndex, colIndex, maxRowIndex, maxColIndex, field, true);

        for (int i = rowIndex; i <= maxRowIndex; i++) {
            ICellView seqCell = table.getCell(i, colIndex);
            if (seqCell == null || !StringHelper.isNumber(seqCell.getText())) {
                maxRowIndex = i - 1;
                break;
            }

            int nextRowIndex = parseFields(sheetName, field, table, i, colIndex + seqCell.getColSpan(),
                    i + seqCell.getMergeDown(), maxColIndex, listener);

            if (i < nextRowIndex - 1) {
                i = nextRowIndex - 1;
            }
        }

        listener.endList(maxRowIndex, maxColIndex, field);
        return new CellRange(rowIndex, colIndex, maxRowIndex, maxColIndex);
    }

    private CellRange parseListData(String sheetName,
                                    IFieldContainer field, ITableView table,
                                    int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                                    ITableDataEventListener listener) {

        listener.beginList(rowIndex, colIndex, maxRowIndex, maxColIndex, field, false);

        Map<String, ImportFieldModel> nameMap = field.getFieldNameMap();

        List<LabelData> colHeaders = new ArrayList<>();

        int labelRowIndex = rowIndex;
        ICellView topHeaderCell = table.getCell(rowIndex, colIndex);
        if (topHeaderCell != null) {
            Guard.checkArgument(!topHeaderCell.isProxyCell(), "table header not allow proxy cell");
            // 如果第一个单元格占据多行，则表示是多级表头，具体的label列在最下面一行
            if (topHeaderCell.getMergeDown() > 0)
                labelRowIndex += topHeaderCell.getMergeDown();
        }

        for (int j = colIndex; j <= maxColIndex; j++) {
            ICellView headerCell = table.getCell(labelRowIndex, j);
            if (headerCell == null) {
                colHeaders.add(null);
                continue;
            }

            headerCell = headerCell.getRealCell();
            if (headerCell.isBlankCell()) {
                colHeaders.add(null);
                continue;
            }

            String name = headerCell.getText().trim();
            ImportFieldModel colField = getFieldModel(field, name, headerCell);
            if (colField == null) {
                // 第一列，可以被忽略
                if (j != colIndex) {
                    throw new NopException(ERR_IMPORT_UNKNOWN_FIELD).param(ARG_SHEET_NAME, sheetName)
                            .param(ARG_CELL_POS, getCellPosition(rowIndex, j)).param(ARG_FIELD_NAME, name)
                            .param(ARG_ALLOWED_NAMES, nameMap.keySet());
                }
            }

            if (colField != null) {
                ImportFieldModel groupField = null;
                ICellView groupCell = null;
                if (labelRowIndex != rowIndex && colField.getGroupField() != null) {
                    if (labelRowIndex > 0) {
                        groupCell = table.getCell(labelRowIndex - 1, j);
                        if (groupCell != null) {
                            groupCell = groupCell.getRealCell();
                        }
                    }
                    groupField = field.getFieldModel(colField.getGroupField());
                    if (groupField == null)
                        throw new NopException(ERR_IMPORT_UNKNOWN_GROUP_FIELD).source(colField)
                                .param(ARG_FIELD_NAME, colField.getGroupField());

                }

                LabelData header = new LabelData(headerCell, colField, groupCell, groupField);
                colHeaders.add(header);
                listener.onColHeader(labelRowIndex, j, header);
            } else {
                colHeaders.add(null);
            }

            if (headerCell.getMergeAcross() > 0) {
                for (int i = 0, n = headerCell.getMergeAcross(); i < n; i++) {
                    j++;
                    colHeaders.add(null);
                }
            }
        }

        for (int i = labelRowIndex + 1; i <= maxRowIndex; i++) {
            // 发现编号列不为数字，则认为表格结束
            if (!StringHelper.isNumber(table.getCellText(i, colIndex))) {
                maxRowIndex = i - 1;
                break;
            }

            listener.beginObject(i, colIndex, i, maxColIndex, field);

            for (int j = colIndex; j <= maxColIndex; j++) {
                LabelData header = colHeaders.get(j - colIndex);
                if (header == null) {
                    continue;
                }

                ICellView cell = table.getCell(i, j);
                try {
                    listener.simpleField(i, j, cell, header);
                } catch (NopException e) {
                    e.addXplStack("row=" + rowIndex + ",col=" + colIndex + ",sheet=" + sheetName + ",field=" + header);
                    throw e;
                }
            }

            listener.endObject(field);
        }

        listener.endList(maxRowIndex, maxColIndex, field);

        return new CellRange(rowIndex, colIndex, maxRowIndex, maxColIndex);
    }

    private ImportFieldModel getFieldModel(IFieldContainer fields, String text, ICellView cell) {
        ImportFieldModel field = fields.getFieldModel(text);
        if (field == null) {
            IEvalAction decider = fields.getFieldDecider();
            if (decider != null) {
                scope.setLocalValue(ExcelConstants.VAR_FIELD_LABEL, text);
                scope.setLocalValue(ExcelConstants.VAR_LABEL_CELL, cell);
                String fieldName = ConvertHelper.toString(decider.invoke(scope));
                if (!StringHelper.isEmpty(fieldName))
                    return fields.getFieldModel(fieldName);
            }
        }
        return field;
    }


    /**
     * 从rowIndex,colIndex处开始解析一组字段。(rowIndex,colIndex)位置如果非空，则必须是一个字段的字段名
     */
    public int parseFields(String sheetName, IFieldContainer field, ITableView table, int rowIndex,
                           int colIndex, int maxRowIndex, int maxColIndex, ITableDataEventListener listener) {
        if (rowIndex >= 0) {

            listener.beginObject(rowIndex, colIndex, maxRowIndex, maxColIndex, field);
            do {
                CellRange range = parseField(sheetName, field, table,
                        rowIndex, colIndex, maxRowIndex, maxColIndex, listener);

                if (range == null) {
                    rowIndex++;
                } else {
                    // 成功解析一个字段之后，右侧可以有其他的字段定义，但是maxRowIndex必须在最左侧字段的范围之内
                    parseNextFields(sheetName, field, table, rowIndex, range.getLastColIndex() + 1,
                            Math.min(maxRowIndex, range.getLastRowIndex()), maxColIndex, listener);
                    rowIndex = range.getLastRowIndex() + 1;
                }
            } while (rowIndex <= maxRowIndex);

            listener.endObject(field);
        }

        return rowIndex;
    }

    /**
     * 如果rowIndex和colIndex指定的位置处为空，则直接返回null。表示没有数据需要解析。
     * <p>
     * 否则，假设指定位置为字段名，根据类型解析对应字段的值，并返回整个字段所占据的空间（包括name和value）
     * </p>
     */
    private CellRange parseField(String sheetName, IFieldContainer fieldContainer, ITableView table,
                                 int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                                 ITableDataEventListener listener) {
        ICellView cell = table.getCell(rowIndex, colIndex);
        String name = cell == null ? null : StringHelper.strip(cell.getText());
        if (name == null)
            return null;

        // 忽略所有以#为前缀的单元格，以及它所在的行
        if (name.startsWith("#"))
            return null;

        // nameMap提供了从displayName到field的映射。在excel数据文件中，字段名通过displayName区分。
        ImportFieldModel field = getFieldModel(fieldContainer, name, cell);
        if (field == null)
            field = fieldContainer.getUnknownField(); // 尝试缺省字段配置

        if (field == null)
            throw new NopException(ERR_IMPORT_UNKNOWN_FIELD).param(ARG_SHEET_NAME, sheetName)
                    .param(ARG_CELL_POS, getCellPosition(rowIndex, colIndex)).param(ARG_FIELD_NAME, name)
                    .param(ARG_ALLOWED_NAMES, fieldContainer.getFieldNameMap().keySet());

        cell = cell.getRealCell();
        listener.onFieldLabel(rowIndex, colIndex, new LabelData(cell, field));

        CellRange range;
        if (field.isList()) {
            range = parseListField(sheetName, field, table, rowIndex, colIndex, maxRowIndex, maxColIndex, listener);
        } else if (field.getFields() != null && !field.getFields().isEmpty()) {
            range = parseObjectField(sheetName, field, table, rowIndex, colIndex, maxRowIndex, maxColIndex, listener);
        } else {
            range = parseSimpleField(field, name, table, rowIndex, colIndex, listener);
        }
        listener.onFieldEnd(rowIndex, colIndex, new LabelData(cell, field), range);
        return range;
    }

    private void parseNextFields(String sheetName, IFieldContainer fieldContainer, ITableView table,
                                 int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                                 ITableDataEventListener listener) {
        for (int j = colIndex; j < maxColIndex; j++) {
            ICellView cell = table.getCell(rowIndex, colIndex);
            if (cell == null)
                continue;

            if (cell.isProxyCell()) {
                continue;
            }

            CellRange range = parseField(sheetName, fieldContainer, table, rowIndex, j, maxRowIndex, maxColIndex, listener);
            if (range == null)
                continue;

            j = range.getLastColIndex();
        }
    }

    private CellRange parseListField(String sheetName, ImportFieldModel field, ITableView table, int rowIndex,
                                     int colIndex, int maxRowIndex, int maxColIndex,
                                     ITableDataEventListener listener) {
        int minRowIndex = rowIndex;
        int minColIndex = colIndex;
        ICellView cell = table.getCell(rowIndex, colIndex);
        // 如果名称占据多行，则字段值放置在右侧。如果设置了displayMode=table，则强制认为是
        boolean alignRight;
        if (cell.getMergeDown() > 0) {
            colIndex = colIndex + cell.getColSpan();
            maxRowIndex = rowIndex + cell.getMergeDown();
            alignRight = true;
        } else {
            // 字段值放置在下方
            rowIndex = rowIndex + cell.getRowSpan();
            maxColIndex = colIndex + cell.getMergeAcross();
            alignRight = false;
        }

        String text = table.getCellText(rowIndex, colIndex);
        CellRange dataRange;
        // 如果headerCell的内容为数字，则表示它实际对应于编号。整个列表是采用card模式显示
        if (StringHelper.isNumber(text)) {
            dataRange = parseCardListData(sheetName, field, table, rowIndex, colIndex,
                    maxRowIndex, maxColIndex, listener);
        } else {
            // 第一行为表头，后续行为内容，直到发现编号列为空
            dataRange = parseListData(sheetName, field, table, rowIndex, colIndex, maxRowIndex,
                    maxColIndex, listener);
        }
        if (alignRight) {
            maxColIndex = dataRange.getLastRowIndex();
        } else {
            maxRowIndex = dataRange.getLastRowIndex();
        }

        return new CellRange(minRowIndex, minColIndex, maxRowIndex, maxColIndex);
    }

    private CellRange parseObjectField(String sheetName, IFieldContainer fieldModel, ITableView table, int rowIndex,
                                       int colIndex, int maxRowIndex, int maxColIndex,
                                       ITableDataEventListener listener) {
        ICellView cell = table.getCell(rowIndex, colIndex);
        // 如果名称占据多行，则字段值放置在右侧
        if (cell.getMergeDown() > 0) {
            colIndex = colIndex + cell.getColSpan();
            maxRowIndex = rowIndex + cell.getMergeDown();
        } else {
            // 字段值放置在下方
            rowIndex = rowIndex + cell.getRowSpan();
            maxColIndex = colIndex + cell.getMergeAcross();
            maxRowIndex = findEmptyRowIndex(table, rowIndex, colIndex, maxRowIndex, maxColIndex);
        }

        parseFields(sheetName, fieldModel, table, rowIndex, colIndex, maxRowIndex, maxColIndex,
                listener);

        return new CellRange(rowIndex, colIndex, maxRowIndex, maxColIndex);
    }


    private int findEmptyRowIndex(ITableView table, int rowIndex, int colIndex, int maxRowIndex, int maxColIndex) {
        for (int i = rowIndex; i <= maxRowIndex; i++) {
            IRowView row = table.getRow(rowIndex);
            MutableBoolean empty = new MutableBoolean();
            row.forEachRealCell(rowIndex, (cell, r, c) -> {
                if (c > maxColIndex)
                    return ProcessResult.STOP;

                if (c < colIndex)
                    return ProcessResult.CONTINUE;

                if (!shouldIgnore(cell)) {
                    empty.set(false);
                    return ProcessResult.STOP;
                }
                return ProcessResult.STOP;
            });
            if (empty.get())
                return i;
        }
        return maxRowIndex;
    }

    private CellRange parseSimpleField(ImportFieldModel fieldModel, String fieldLabel, ITableView table, int rowIndex,
                                       int colIndex, ITableDataEventListener listener) {
        ICellView cell = table.getCell(rowIndex, colIndex);
        colIndex += cell.getColSpan();
        ICellView valueCell = table.getCell(rowIndex, colIndex);

        try {
            listener.simpleField(rowIndex, colIndex, valueCell, new LabelData(cell, fieldModel));
        } catch (NopException e) {
            e.addXplStack("row=" + rowIndex + ",col=" + colIndex + ",sheet=" + sheetName + ",field=" + fieldLabel);
            throw e;
        }

        return new CellRange(rowIndex, colIndex, rowIndex + cell.getMergeDown(),
                valueCell == null ? colIndex + 1 : colIndex + valueCell.getMergeAcross());
    }

    String getCellPosition(int rowIndex, int colIndex) {
        return CellPosition.toABString(rowIndex, colIndex, false, false);
    }

    /**
     * 跳过可以忽略的非数据行。包含有数据的行，它的第一个单元格的内容一定是非空的。
     */
    private int skipNonDataRow(ITableView table, int rowIndex) {
        while (rowIndex <= table.getRowCount()) {
            ICellView cell = table.getCell(rowIndex, 0);
            if (!shouldIgnore(cell))
                return rowIndex;
            rowIndex++;
        }
        return -1;
    }

    private boolean shouldIgnore(ICellView cell) {
        if (cell == null || cell.isProxyCell())
            return true;

        return cell.isBlankCell() || cell.getText().startsWith("#");
    }
}