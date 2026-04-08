/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.parse;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.model.ImageUrlMapper;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableRow;

import java.util.Objects;

/*
 <w:tbl>
 <w:tblPr><w:tblW w:w="9638" w:type="dxa"/><w:jc w:val="left"/><w:tblInd w:w="50" w:type="dxa"/>
    <w:tblBorders>
       <w:top w:val="single" w:sz="2" w:space="0" w:color="000001"/>
      <w:left w:val="single" w:sz="2"  w:space="0" w:color="000001"/>
    <w:bottom w:val="single" w:sz="2" w:space="0"  w:color="000001"/>
 <w:insideH w:val="single" w:sz="2" w:space="0" w:color="000001"/>
 </w:tblBorders>
 <w:tblCellMar>
    <w:top w:w="55" w:type="dxa"/>
    <w:left w:w="48" w:type="dxa"/>
    <w:bottom w:w="55" w:type="dxa"/>
    <w:right w:w="55" w:type="dxa"/>
 </w:tblCellMar>
 </w:tblPr>
 <w:tblGrid><w:gridCol w:w="3212"/><w:gridCol w:w="3212"/><w:gridCol w:w="3213"/></w:tblGrid>
 <w:tr><w:trPr/>
 <w:tc>
 <w:tcPr><w:tcW w:w="3212" w:type="dxa"/><w:vMerge w:val="restart"/>
 <w:tcBorders>
    <w:top w:val="single" w:sz="2" w:space="0" w:color="000001"/>
    <w:left w:val="single" w:sz="2" w:space="0" w:color="000001"/>
    <w:bottom w:val="single" w:sz="2" w:space="0" w:color="000001"/>
    <w:insideH w:val="single" w:sz="2" w:space="0" w:color="000001"/>
 </w:tcBorders>
 <w:shd w:fill="auto" w:val="clear"/>
 <w:tcMar>
 <w:left w:w="48" w:type="dxa"/>
 </w:tcMar>
 </w:tcPr>
 <w:p><w:pPr><w:pStyle w:val="Style19"/><w:rPr/></w:pPr>
 <w:r><w:rPr/></w:r></w:p>
 </w:tc>
 </w:tr>
 </w:tbl>
 */
public class WordTableParser {
    public static final double DEFAULT_WIDTH = 72.0;

    private ImageUrlMapper urlMapper;
    private boolean forMarkdown;

    public WordTableParser imageUrlMapper(ImageUrlMapper urlMapper) {
        this.urlMapper = urlMapper;
        return this;
    }

    public WordTableParser forMarkdown(boolean forMarkdown) {
        this.forMarkdown = forMarkdown;
        return this;
    }

    public WordTable parseTable(XNode tblNode) {
        WordTable tbl = new WordTable();
        int i, n = tblNode.getChildCount();
        int rowIndex = 0;
        for (i = 0; i < n; i++) {
            XNode child = tblNode.child(i);
            String name = child.getTagName();
            if (name.equals("w:tr")) {
                parseRow(rowIndex, child, tbl);
                rowIndex++;
            }
        }

        parseCols(tblNode, tbl);
        return tbl;
    }

    protected WordTableRow parseRow(int rowIndex, XNode node, WordTable tbl) {
        int i, n = node.getChildCount();
        int colIndex = 0;
        for (i = 0; i < n; i++) {
            XNode child = node.child(i);
            String name = child.getTagName();
            if (name.equals("w:tc")) {
                WordTableCell cell = new WordTableCell();
                if (parseCell(child, cell)) {
                    tbl.setCell(rowIndex, colIndex, cell);
                }
                colIndex += cell.getMergeAcross() + 1;
            }
        }

        WordTableRow row = tbl.makeRow(rowIndex);

        Double height = getRowHeight(node);
        if (height != null)
            row.setHeight(height);
        return row;
    }

    protected Double getRowHeight(XNode node) {
        // 解析行高
        XNode trPrN = node.childByTag("w:trPr");
        if (trPrN != null) {
            XNode trHeight = trPrN.childByTag("w:trHeight");
            if (trHeight != null) {
                Integer height = trHeight.attrInt("w:val");
                String heightRule = trHeight.attrText("w:hRule");
                // 如果高度规则是"exact"或"atLeast"，则设置行高
                if ("exact".equals(heightRule) || "atLeast".equals(heightRule)) {
                    return twipsToPoints(height);
                }
            }
        }
        return null;
    }

    protected String parseData(XNode node) {
        return WordXmlHelper.getText(node, forMarkdown, urlMapper);
    }

    protected boolean parseCell(XNode node, WordTableCell cell) {
        XNode tcPrN = node.childByTag("w:tcPr");
        if (tcPrN != null) {

            // 处理水平合并
            Integer span = ConvertHelper.toInt(tcPrN.childAttr("w:gridSpan", "w:val"));
            if (span == null)
                span = 1;

            if (span > 1) {
                cell.setMergeAcross(span - 1);
            }

            // 处理垂直合并
            String merge = getVMerge(tcPrN);
            if ("continue".equals(merge)) {
                return false; // 当前单元格是合并的延续部分，不需要处理
            }

            // 处理垂直合并的起始单元格
            if ("restart".equals(merge)) {
                int cellIndex = node.childIndex(); // 当前单元格在行中的位置
                XNode row = node.getParent();
                XNode tblNode = row.getParent();
                int mergeDown = 0;

                // 从下一行开始检查
                int rowIndex = row.childIndex() + 1;
                while (rowIndex < tblNode.getChildCount()) {
                    XNode nextRow = tblNode.child(rowIndex);

                    // 确保下一行有足够的单元格
                    if (cellIndex >= nextRow.getChildCount()) {
                        break;
                    }

                    XNode nextCell = nextRow.child(cellIndex);
                    XNode nextTcPrN = nextCell.childByTag("w:tcPr");

                    // 检查是否是垂直合并的延续
                    if (nextTcPrN != null) {
                        String nextMerge = getVMerge(nextTcPrN);
                        if (!"continue".equals(nextMerge)) {
                            break; // 不是延续，结束合并
                        }

                        // 确保水平合并范围一致
                        Integer nextSpan = ConvertHelper.toInt(nextTcPrN.childAttr("w:gridSpan", "w:val"));
                        if (nextSpan == null)
                            nextSpan = 1;

                        if (nextSpan.intValue() != span.intValue()) {
                            break;
                        }
                    } else {
                        break; // 没有tcPr节点，结束合并
                    }

                    mergeDown++;
                    rowIndex++;
                }

                cell.setMergeDown(mergeDown);
            }
        }

        String data = this.parseData(node);
        cell.setValue(data);
        return true;
    }

    protected void parseCols(XNode tblNode, WordTable table) {
        XNode tblGrid = tblNode.childByTag("w:tblGrid");
        if (tblGrid == null) {
            return;
        }

        for (XNode colNode : tblGrid.getChildren()) {
            if (!"w:gridCol".equals(colNode.getTagName())) {
                continue;
            }

            String widthTwips = colNode.attrText("w:w");
            io.nop.office.doc.model.WordTableColumnConfig config = new io.nop.office.doc.model.WordTableColumnConfig();
            if (widthTwips != null) {
                config.setWidth(twipsToPoints(Integer.parseInt(widthTwips)));
            } else {
                config.setWidth(DEFAULT_WIDTH);
            }
            config.setHidden("true".equalsIgnoreCase(colNode.attrText("data-hidden")));
            table.getCols().add(config);
        }
    }

    protected double twipsToPoints(int twips) {
        return twips / 20.0;
    }

    private String getVMerge(XNode tcPr) {
        String merge = (String) tcPr.childAttr("w:vMerge", "w:val");
        if (merge == null && tcPr.hasChild("w:vMerge")) {
            merge = "continue";
        }
        return merge;
    }

    public String getFirstCellData(XNode node) {
        XNode tr = node.childByTag("w:tr");
        if (tr == null)
            return null;
        XNode tc = tr.childByTag("w:tc");
        if (tc == null)
            return null;
        return parseData(tc);
    }

    public boolean isMatchHeader(XNode node, String value) {
        String header = getFirstCellData(node);
        header = StringHelper.strip(header);
        return Objects.equals(header, value);
    }
}
