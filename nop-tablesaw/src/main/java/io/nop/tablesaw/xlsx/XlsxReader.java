/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.tablesaw.xlsx;

import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.model.table.ICell;
import io.nop.core.model.table.ICellView;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.InputStreamResource;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.tablesaw.utils.TablesawHelper;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.DataReader;
import tech.tablesaw.io.ReaderRegistry;
import tech.tablesaw.io.RuntimeIOException;
import tech.tablesaw.io.Source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class XlsxReader implements DataReader<XlsxReadOptions> {

    private static final XlsxReader INSTANCE = new XlsxReader();

    public static void register() {
        register(Table.defaultReaderRegistry);
    }

    public static void register(ReaderRegistry registry) {
        registry.registerExtension("xlsx", INSTANCE);
        registry.registerMimeType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", INSTANCE);
        registry.registerOptions(XlsxReadOptions.class, INSTANCE);
    }

    @Override
    public Table read(XlsxReadOptions options) {
        List<Table> tables = null;
        try {
            tables = readMultiple(options, true);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
        if (options.sheetIndex() != null) {
            int index = options.sheetIndex();
            if (index < 0 || index >= tables.size()) {
                throw new IndexOutOfBoundsException(
                        String.format("Sheet index %d outside bounds. %d sheets found.", index, tables.size()));
            }

            Table table = tables.get(index);
            if (table == null) {
                throw new IllegalArgumentException(
                        String.format("No table found at sheet index %d.", index));
            }
            return table;
        }
        // since no specific sheetIndex asked, return first table
        return tables.stream()
                .filter(t -> t != null)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tables found."));
    }

    public List<Table> readMultiple(XlsxReadOptions options) throws IOException {
        return readMultiple(options, false);
    }

    /**
     * Read at most a table from every sheet.
     *
     * @param includeNulls include nulls for sheets without a table
     * @return a list of tables, at most one for every sheet
     */
    protected List<Table> readMultiple(XlsxReadOptions options, boolean includeNulls)
            throws IOException {
        byte[] bytes = null;
        IResource input = getResource(options, bytes);
        List<Table> tables = new ArrayList<>();
        ExcelWorkbook workbook = new ExcelWorkbookParser().parseFromResource(input);


        for (ExcelSheet sheet : workbook.getSheets()) {
            TableRange tableArea = findTableArea(sheet);
            if (tableArea != null) {
                Table table = createTable(sheet, tableArea, options);
                tables.add(table);
            } else if (includeNulls) {
                tables.add(null);
            }
        }
        return tables;
    }

    private static class TableRange {
        private int startRow, endRow, startColumn, endColumn;

        TableRange(int startRow, int endRow, int startColumn, int endColumn) {
            this.startRow = startRow;
            this.endRow = endRow;
            this.startColumn = startColumn;
            this.endColumn = endColumn;
        }

        public int getColumnCount() {
            return endColumn - startColumn + 1;
        }
    }

    private TableRange findTableArea(ExcelSheet sheet) {
        // find first row and column with contents
        int row1 = -1;
        int row2 = -1;
        TableRange lastRowArea = null;
        int rowIndex = -1;
        for (ExcelRow row : sheet.getTable().getRows()) {
            rowIndex++;
            TableRange rowArea = findRowArea(row);
            if (lastRowArea == null && rowArea != null) {
                if (row1 < 0) {
                    lastRowArea = rowArea;
                    row1 = rowIndex;
                    row2 = row1;
                }
            } else if (lastRowArea != null && rowArea == null) {
                if (row2 > row1) {
                    break;
                } else {
                    row1 = -1;
                }
            } else if (lastRowArea == null && rowArea == null) {
                row1 = -1;
            } else if (rowArea.startColumn < lastRowArea.startColumn
                    || rowArea.endColumn > lastRowArea.endColumn) {
                lastRowArea = null;
                row2 = -1;
            } else {
                row2 = rowIndex;
            }
        }
        return row1 >= 0 && lastRowArea != null
                ? new TableRange(row1, row2, lastRowArea.startColumn, lastRowArea.endColumn)
                : null;
    }

    private TableRange findRowArea(ExcelRow row) {
        int col1 = -1;
        int col2 = -1;
        for (int colIndex = 0, n = row.getColCount(); colIndex < n; colIndex++) {
            ICell cell = row.getCell(colIndex);
            Boolean blank = StringHelper.isEmptyObject(cell == null ? null : cell.getValue());
            if (col1 < 0 && Boolean.FALSE.equals(blank)) {
                col1 = colIndex;
                col2 = col1;
            } else if (col1 >= 0 && col2 >= col1) {
                if (Boolean.FALSE.equals(blank)) {
                    col2 = colIndex;
                } else if (Boolean.TRUE.equals(blank)) {
                    break;
                }
            }
        }
        return col1 >= 0 && col2 >= col1 ? new TableRange(0, 0, col1, col2) : null;
    }

    private IResource getResource(XlsxReadOptions options, byte[] bytes) {
        String tableName = options.tableName();
        if (tableName == null)
            tableName = "table";

        String xlsxName = "/" + tableName;
        if (!xlsxName.endsWith(".xlsx"))
            xlsxName += ".xlsx";

        if (bytes != null) {
            return new ByteArrayResource(xlsxName, bytes, -1L);
        }
        if (options.source().inputStream() != null) {
            return new InputStreamResource(xlsxName, options.source().inputStream(), -1L);
        }
        return new FileResource(xlsxName, options.source().file());
    }

    private Table createTable(ExcelSheet sheet, TableRange tableArea, XlsxReadOptions options) {
        Optional<List<String>> optHeaderNames = getHeaderNames(sheet, tableArea);
        optHeaderNames.ifPresent(h -> tableArea.startRow++);
        List<String> headerNames = optHeaderNames.orElse(calculateDefaultColumnNames(tableArea));

        Table table = Table.create(options.tableName() + "#" + sheet.getName());
        ExcelTable sheetTable = sheet.getTable();
        List<Column<?>> columns = new ArrayList<>(Collections.nCopies(headerNames.size(), null));
        List<StdDataType> dataTypes = new ArrayList<>(Collections.nCopies(headerNames.size(), null));

        for (int rowNum = tableArea.startRow; rowNum <= tableArea.endRow; rowNum++) {
            ExcelRow row = sheetTable.getRow(rowNum);
            for (int colNum = 0; colNum < headerNames.size(); colNum++) {
                int excelColNum = colNum + tableArea.startColumn;
                ICell cell = row.getCell(excelColNum);
                if (cell != null && StringHelper.isEmptyObject(cell.getValue()))
                    cell = null;

                Column<?> column = columns.get(colNum);
                String columnName = headerNames.get(colNum);
                StdDataType dataType = dataTypes.get(colNum);
                if (cell != null) {
                    if (column == null) {
                        column = createColumn(colNum, columnName, sheet, excelColNum, tableArea, options);
                        dataType = TablesawHelper.columnTypeToDataType(column.type());
                        dataTypes.set(colNum, dataType);
                        columns.set(colNum, column);
                        while (column.size() < rowNum - tableArea.startRow) {
                            column.appendMissing();
                        }
                    }
                    Column<?> altColumn = appendValue(column, dataType, cell);
                    if (altColumn != null && altColumn != column) {
                        column = altColumn;
                        columns.set(colNum, column);
                    }
                } else {
                    boolean hasCustomizedType =
                            options.columnTypeReadOptions().columnType(colNum, columnName).isPresent();
                    if (column == null && hasCustomizedType) {
                        ColumnType columnType =
                                options.columnTypeReadOptions().columnType(colNum, columnName).get();
                        dataType = TablesawHelper.columnTypeToDataType(columnType);
                        column = columnType.create(columnName).appendMissing();
                        columns.set(colNum, column);
                        dataTypes.set(colNum, dataType);
                    } else if (hasCustomizedType) {
                        column.appendMissing();
                    }
                }
                if (column != null) {
                    while (column.size() <= rowNum - tableArea.startRow) {
                        column.appendMissing();
                    }
                }
            }
        }
        columns.removeAll(Collections.singleton(null));
        table.addColumns(columns.toArray(new Column<?>[columns.size()]));
        return table;
    }

    private Optional<List<String>> getHeaderNames(ExcelSheet sheet, TableRange tableArea) {
        ExcelTable table = sheet.getTable();
        // assume header row if all cells are of type String
        ExcelRow row = table.getRow(tableArea.startRow);
        List<String> headerNames =
                IntStream.range(tableArea.startColumn, tableArea.endColumn + 1)
                        .mapToObj(row::getCell)
                        .filter(cell -> {
                            if (cell == null || cell.isProxyCell())
                                return false;
                            ExcelCell ec = (ExcelCell) cell;
                            return ec.getValue() instanceof String;
                        })
                        .map(ICellView::getText)
                        .collect(Collectors.toList());
        return headerNames.size() == tableArea.getColumnCount()
                ? Optional.of(headerNames)
                : Optional.empty();
    }

    private List<String> calculateDefaultColumnNames(TableRange tableArea) {
        return IntStream.range(tableArea.startColumn, tableArea.endColumn + 1)
                .mapToObj(i -> "col" + i)
                .collect(Collectors.toList());
    }

    private Column<?> appendValue(Column<?> column, StdDataType dataType, ICell cell) {
        Object value = cell.getValue();
        if (dataType != null)
            value = dataType.convert(value);
        return column.appendObj(value);
    }

    private Column<?> createColumn(
            int colNum,
            String name,
            ExcelSheet sheet,
            int excelColNum,
            TableRange tableRange,
            XlsxReadOptions options) {
        Column<?> column;

        ColumnType columnType =
                options
                        .columnTypeReadOptions()
                        .columnType(colNum, name)
                        .orElse(
                                calculateColumnTypeForColumn(sheet, excelColNum, tableRange)
                                        .orElse(ColumnType.STRING));

        column = columnType.create(name);
        return column;
    }

    @Override
    public Table read(Source source) {
        return read(XlsxReadOptions.builder(source).build());
    }

    private Optional<ColumnType> calculateColumnTypeForColumn(
            ExcelSheet sheet, int col, TableRange tableRange) {
        Set<StdDataType> cellTypes = getCellTypes(sheet, col, tableRange);

        if (cellTypes.size() != 1) {
            return Optional.empty();
        }

        StdDataType cellType = CollectionHelper.first(cellTypes);
        ColumnType colType = TablesawHelper.dataTypeToColumnType(cellType);
        if (colType == null)
            return Optional.empty();
        return Optional.of(colType);
    }

    private Set<StdDataType> getCellTypes(ExcelSheet sheet, int col, TableRange tableRange) {
        ExcelTable table = sheet.getTable();
        Set<StdDataType> dataTypes = new HashSet<>();
        IntStream.range(tableRange.startRow, tableRange.endRow + 1)
                .mapToObj(table::getRow)
                .filter(Objects::nonNull)
                .map(row -> row.getCell(col))
                .filter(cell -> {
                    if (cell == null || cell.isProxyCell())
                        return false;
                    return true;
                })
                .forEach(
                        cell -> {
                            ExcelCell ec = (ExcelCell) cell;
                            StdDataType dataType = ec.getType();
                            if (dataType == null)
                                dataType = StdDataType.guessFromValue(ec.getValue());
                            if (dataType != null) {
                                if (dataType == StdDataType.DOUBLE) {
                                    Number value = (Number) ec.getValue();
                                    if (value == null)
                                        value = MathHelper.ZERO_INT;
                                    long longValue = value.longValue();
                                    if (MathHelper.compareWithDouble(longValue, value.doubleValue(), 1E-10) == 0) {
                                        if (MathHelper.isInteger(longValue)) {
                                            dataType = StdDataType.INT;
                                        } else {
                                            dataType = StdDataType.LONG;
                                        }
                                    }
                                }
                                if (dataType == StdDataType.DOUBLE) {
                                    dataTypes.remove(StdDataType.INT);
                                    dataTypes.remove(StdDataType.LONG);
                                } else if (dataType == StdDataType.LONG) {
                                    dataTypes.remove(StdDataType.INT);
                                }
                                dataTypes.add(dataType);
                            }
                        }
                );
        return dataTypes;
    }
}
