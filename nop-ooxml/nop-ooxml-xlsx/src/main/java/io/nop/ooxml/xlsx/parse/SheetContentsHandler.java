/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package io.nop.ooxml.xlsx.parse;

import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelPageMargins;

import java.util.List;

/**
 * This interface allows to provide callbacks when reading a sheet in streaming mode.
 * <p>
 * The XSLX file is usually read via XSSFReader.
 * <p>
 * By implementing the methods, you can process arbitrarily large files without exhausting main memory.
 */
public interface SheetContentsHandler {
    void startSheet(String sheetName);

    void cols(List<ExcelColumnConfig> cols);

    void pageMargins(ExcelPageMargins pageMargins);

    void sheetFormat(Double defaultRowHeight);

    /**
     * A row with the (zero based) row number has started
     */
    void startRow(int rowNum, Double height, boolean hidden);

    /**
     * A row with the (zero based) row number has ended
     */
    void endRow(int rowNum);

    /**
     * A cell, with the given formatted value (may be null), and possibly a comment (may be null), was encountered.
     * <p>
     * Sheets that have missing or empty cells may result in sparse calls to <code>cell</code>. See the code in
     * <code>poi-examples/src/main/java/org/apache/poi/xssf/eventusermodel/XLSX2CSV.java</code> for an example of how to
     * handle this scenario.
     */
    void cell(CellPosition cellRef, Object value, String formulaStr, int styleId);

    void mergeCell(CellRange range);

    void drawing(String id);

    void link(String ref, String location, String rId);

    /**
     * A header or footer has been encountered
     */
    default void headerFooter(String text, boolean isHeader, String tagName) {
    }

    /**
     * Signal that the end of a sheet was been reached
     */
    default void endSheet() {
    }
}