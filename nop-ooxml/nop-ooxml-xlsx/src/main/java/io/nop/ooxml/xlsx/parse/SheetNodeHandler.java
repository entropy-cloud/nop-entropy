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

// refactor from POI XSSFSheetXMLHandler

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.handler.XNodeHandlerAdapter;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.util.UnitsHelper;
import io.nop.ooxml.xlsx.model.SharedStringsPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SheetNodeHandler extends XNodeHandlerAdapter {

    /**
     * These are the different kinds of cells we support. We keep track of the current one between the start and end.
     */
    enum xssfDataType {
        BOOLEAN, ERROR, FORMULA, INLINE_STRING, SST_STRING, DATETIME, NUMBER,
    }

    // Set when V start element is seen
    private boolean vIsOpen;
    // Set when F start element is seen
    private boolean fIsOpen;
    // Set when an Inline String "is" is seen
    private boolean isIsOpen;
    // Set when a header/footer element is seen
    private boolean hfIsOpen;

    // Set when cell start element is seen;
    // used when cell close element is seen.
    private xssfDataType nextDataType;

    private int rowNum;
    private Double rowHeight;
    private int nextRowNum; // some sheets do not have rowNums, Excel can read them so we should try to handle them
    // correctly as well
    private String cellRef;
    private int styleId;

    /**
     * Read only access to the shared strings table, for looking up (most) string cell's contents
     */
    private final SharedStringsPart sharedStringsTable;

    /**
     * Where our text is going
     */
    private final SheetContentsHandler output;

    // Gathers characters as they are seen.
    private final StringBuilder value = new StringBuilder(64);
    private final StringBuilder formula = new StringBuilder(64);
    private final StringBuilder headerFooter = new StringBuilder(64);

//    private Queue<CellPosition> commentCellRefs;

    private List<ExcelColumnConfig> cols;

    public SheetNodeHandler(SharedStringsPart sharedStringsTable, SheetContentsHandler output) {
        this.sharedStringsTable = sharedStringsTable;
        this.output = output;
    }

//    private void init(CommentsPart commentsTable) {
//        if (commentsTable != null) {
//            commentCellRefs = new LinkedList<>(commentsTable.getCellAddresses());
//        }
//    }

    private boolean isTextTag(String name) {
        if ("v".equals(name)) {
            // Easy, normal v text tag
            return true;
        }
        if ("inlineStr".equals(name)) {
            // Easy inline string
            return true;
        }
        if ("t".equals(name) && isIsOpen) {
            // Inline string <is><t>...</t></is> pair
            return true;
        }
        // It isn't a text tag
        return false;
    }

    @Override
    @SuppressWarnings("unused")
    public void beginNode(SourceLocation loc, String localName, Map<String, ValueWithLocation> attrs) {
        if (isTextTag(localName)) {
            vIsOpen = true;
            // Clear contents cache
            if (!isIsOpen) {
                value.setLength(0);
            }
        } else if ("is".equals(localName)) {
            // Inline string outer tag
            isIsOpen = true;
        } else if ("f".equals(localName)) {
            // Clear contents cache
            formula.setLength(0);

            // Mark us as being a formula if not already
            if (nextDataType == xssfDataType.NUMBER) {
                nextDataType = xssfDataType.FORMULA;
            }

            // Decide where to get the formula string from
            String type = getAttr(attrs, "t");
            if (type != null && type.equals("shared")) {
                // Is it the one that defines the shared, or uses it?
                String ref = getAttr(attrs, "ref");
                String si = getAttr(attrs, "si");

                if (ref != null) {
                    // This one defines it
                    // TODO Save it somewhere
                    fIsOpen = true;
                } else { //NOPMD - TODO
                    // This one uses a shared formula
                    // TODO Retrieve the shared formula and tweak it to
                    // match the current cell
                    // if (formulasNotResults) {
                    // LOG.atWarn().log("shared formulas not yet supported!");
                    // }
                    /*
                     * else { // It's a shared formula, so we can't get at the formula string yet // However, they don't
                     * care about the formula string, so that's ok! }
                     */
                }
            } else {
                fIsOpen = true;
            }
        } else if ("oddHeader".equals(localName) || "evenHeader".equals(localName) || "firstHeader".equals(localName)
                || "firstFooter".equals(localName) || "oddFooter".equals(localName) || "evenFooter".equals(localName)) {
            hfIsOpen = true;
            // Clear contents cache
            headerFooter.setLength(0);
        } else if ("row".equals(localName)) {
            String rowNumStr = getAttr(attrs, "r");
            if (rowNumStr != null) {
                rowNum = Integer.parseInt(rowNumStr) - 1;
            } else {
                rowNum = nextRowNum;
            }
            rowHeight = getAttrDouble(attrs, "ht", null);
            output.startRow(rowNum, rowHeight);
        }
        // c => cell
        else if ("c".equals(localName)) {
            // Set up defaults.
            this.nextDataType = xssfDataType.NUMBER;
            // this.formatIndex = -1;
            // this.formatString = null;
            cellRef = getAttr(attrs, "r");
            String cellType = getAttr(attrs, "t");
            String cellStyleStr = getAttr(attrs, "s");
            if (cellStyleStr != null) {
                styleId = ConvertHelper.toPrimitiveInt(cellStyleStr, 0, NopException::new);
            } else {
                styleId = -1;
            }
            if ("b".equals(cellType))
                nextDataType = xssfDataType.BOOLEAN;
            else if ("e".equals(cellType))
                nextDataType = xssfDataType.ERROR;
            else if ("inlineStr".equals(cellType))
                nextDataType = xssfDataType.INLINE_STRING;
            else if ("s".equals(cellType))
                nextDataType = xssfDataType.SST_STRING;
            else if ("str".equals(cellType))
                nextDataType = xssfDataType.FORMULA;
            else { //NOPMD - TODO
                // Number, but almost certainly with a special style or format
                // XSSFCellStyle style = null;
                // if (stylesTable != null) {
                // if (cellStyleStr != null) {
                // int styleIndex = Integer.parseInt(cellStyleStr);
                // style = stylesTable.getStyleAt(styleIndex);
                // } else if (stylesTable.getNumCellStyles() > 0) {
                // style = stylesTable.getStyleAt(0);
                // }
                // }
                // if (style != null) {
                // this.formatIndex = style.getDataFormat();
                // this.formatString = style.getDataFormatString();
                // if (this.formatString == null)
                // this.formatString = BuiltinFormats.getBuiltinFormat(this.formatIndex);
                // }
            }
        } else if ("mergeCell".equals(localName)) {
            CellRange range = CellRange.fromABString(getAttr(attrs, "ref"));
            output.mergeCell(range);
        } else if ("col".equals(localName)) {
            int min = getAttrInt(attrs, "min", 1);
            int max = getAttrInt(attrs, "max", min);
            // 宽度为字符个数
            Double width = getAttrDouble(attrs, "width", null);
            if (width != null)
                width = width * UnitsHelper.DEFAULT_CHARACTER_WIDTH_IN_PT;

            if (cols == null) {
                cols = new ArrayList<>();
            }
            for (int i = min - 1; i < max; i++) {
                ExcelColumnConfig col = new ExcelColumnConfig();
                col.setWidth(width);
                CollectionHelper.set(cols, i, col);
            }
        } else if ("pageMargins".equals(localName)) {
            //     <pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>
            Double left = getAttrDouble(attrs, "left", null);
            Double right = getAttrDouble(attrs, "right", null);
            Double top = getAttrDouble(attrs, "top", null);
            Double bottom = getAttrDouble(attrs, "bottom", null);
            Double header = getAttrDouble(attrs, "header", null);
            Double footer = getAttrDouble(attrs, "footer", null);

            ExcelPageMargins margins = new ExcelPageMargins();
            margins.setLocation(loc);
            margins.setLeft(left);
            margins.setRight(right);
            margins.setTop(top);
            margins.setBottom(bottom);
            margins.setHeader(header);
            margins.setFooter(footer);

            output.pageMargins(margins);
        } else if ("sheetFormatPr".equals(localName)) {
            Double defaultRowHeight = getAttrDouble(attrs, "defaultRowHeight", null);
            output.sheetFormat(defaultRowHeight);
        } else if ("drawing".equals(localName)) {
            String id = getAttr(attrs, "r:id");
            if (id != null)
                output.drawing(id);
        }
    }

    @Override
    public void endNode(String localName) {
        if ("c".equals(localName)) {
            outputCell();
            value.setLength(0);
        } else if (isTextTag(localName)) {
            // v => contents of a cell
            vIsOpen = false;
        } else if ("f".equals(localName)) {
            fIsOpen = false;
        } else if ("is".equals(localName)) {
            isIsOpen = false;
        } else if ("row".equals(localName)) {
            // Finish up the row
            output.endRow(rowNum);

            // some sheets do not have rowNum set in the XML, Excel can read them so we should try to read them as well
            nextRowNum = rowNum + 1;
        } else if ("sheetData".equals(localName)) {
            // indicate that this sheet is now done
            output.endSheet();
        } else if ("oddHeader".equals(localName) || "evenHeader".equals(localName) || "firstHeader".equals(localName)) {
            hfIsOpen = false;
            output.headerFooter(headerFooter.toString(), true, localName);
        } else if ("oddFooter".equals(localName) || "evenFooter".equals(localName) || "firstFooter".equals(localName)) {
            hfIsOpen = false;
            output.headerFooter(headerFooter.toString(), false, localName);
        } else if ("cols".equals(localName)) {
            output.cols(cols);
        }
    }

    /**
     * Captures characters only if a suitable element is open. Originally was just "v"; extended for inlineStr also.
     */
    @Override
    public void text(SourceLocation loc, String text) {
        if (vIsOpen) {
            value.append(text);
        }
        if (fIsOpen) {
            formula.append(text);
        }
        if (hfIsOpen) {
            headerFooter.append(text);
        }
    }

    private void outputCell() {
        Object thisStr = null;
        String formulaStr = null;

        // Process the value contents as required, now we have it all
        switch (nextDataType) {
            case BOOLEAN:
                char first = value.charAt(0);
                thisStr = first == '1';
                break;

            case ERROR:
                thisStr = "ERROR:" + value;
                break;

            case FORMULA: {
                String fv = value.toString();

                try {
                    thisStr = StringHelper.parseNumber(fv);
                } catch (Exception e) {
                    thisStr = fv;
                }
                formulaStr = formula.toString();
                break;
            }

            case INLINE_STRING:
                // TODO: Can these ever have formatting on them?
                thisStr = value.toString();
                break;

            case SST_STRING:
                String sstIndex = value.toString();
                if (!sstIndex.isEmpty()) {
                    int idx = Integer.parseInt(sstIndex);
                    thisStr = sharedStringsTable.getItemAt(idx);
                }
                break;

            case NUMBER:
                String n = value.toString();
                try {
                    thisStr = StringHelper.parseNumber(n);
                } catch (NumberFormatException e) {
                    thisStr = n;
                }
                break;
            default:
                thisStr = "(TODO: Unexpected type: " + nextDataType + ")";
                break;
        }

        CellPosition cellPos = CellPosition.fromABString(cellRef);

        // Output
        output.cell(cellPos, thisStr, formulaStr, styleId);
    }
}