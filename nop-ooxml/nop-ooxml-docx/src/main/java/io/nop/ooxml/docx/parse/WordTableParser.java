/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.docx.parse;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.table.ICell;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelTable;
import io.nop.xlang.xpath.XPathHelper;

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
    static final IXSelector<XNode> SELECTOR_P_TEXT = XPathHelper.parseXSelector("w:p/w:r/w:t/$value");
    static final IXSelector<XNode> SELECTOR_TC_PR_SPAN = XPathHelper.parseXSelector("w:gridSpan/w:val"); // 对应colspan
    static final IXSelector<XNode> SELECTOR_TC_PR_VMERGE = XPathHelper.parseXSelector("w:vMerge/w:val"); // 从restart到continue,
    // 对应行合并

    public ExcelTable parseTable(XNode tblNode) {
        ExcelTable tbl = new ExcelTable();
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
        return tbl;
    }

    private void parseRow(int rowIndex, XNode node, ExcelTable tbl) {
        int i, n = node.getChildCount();
        int colIndex = 0;
        for (i = 0; i < n; i++) {
            XNode child = node.child(i);
            String name = child.getTagName();
            if (name.equals("w:tc")) {
                ExcelCell cell = new ExcelCell();
                if (!parseCell(child, cell)) {
                    // merge down
                    ICell prevCell = tbl.getCell(rowIndex - 1, colIndex);
                    if (prevCell != null && prevCell.getColOffset() == 0) {
                        tbl.mergeCell(rowIndex - prevCell.getRowOffset() - 1, colIndex, prevCell.getMergeDown() + 1,
                                prevCell.getMergeAcross());
                    }
                } else {
                    tbl.setCell(rowIndex, colIndex, cell);
                }
                colIndex += cell.getMergeAcross() + 1;
            }
        }
    }

    String parseData(XNode node) {
        return WordXmlHelper.getText(node);
        // StringBuilder sb = new StringBuilder();
        // int i, n = node.getChildCount();
        // int nCount = 0;
        // for (i = 0; i < n; i++) {
        // XNode child = node.child(i);
        // String name = child.getTagName();
        // if (name.equals("w:p")) {
        // if (nCount != 0)
        // sb.append("\n");
        // parseP(child, sb);
        // nCount++;
        // }
        // }
        // return sb.toString();
    }

    void parseP(XNode pN, StringBuilder sb) {
        int i, n = pN.getChildCount();
        for (i = 0; i < n; i++) {
            XNode child = pN.child(i);
            if (child.getTagName().equals("w:r")) {
                parseR(child, sb);
            }
        }
    }

    void parseR(XNode node, StringBuilder sb) {
        if (node.hasChild()) {
            int i, n = node.getChildCount();
            for (i = 0; i < n; i++) {
                XNode child = node.child(i);
                String name = child.getTagName();
                if (name.equals("w:rPr")) {
                    continue;
                } else if (name.equals("w:br")) {
                    sb.append("\n");
                } else if (child.isTextNode()) {
                    sb.append(child.content());
                } else if (name.equals("w:t") && !child.hasChild()) {
                    sb.append(child.content());
                } else if (name.equals("w:pict") || name.equals("w:binData")) {
                    continue;
                } else {
                    parseR(child, sb);
                }
            }
        } else if (node.getTagName().equals("w:t")) {
            sb.append(node.content());
        }
    }

    boolean parseCell(XNode node, ExcelCell cell) {
        XNode tcPrN = node.childByTag("w:tcPr");
        if (tcPrN != null) {
            String merge = (String) tcPrN.selectOne(SELECTOR_TC_PR_VMERGE);
            if ("continue".equals(merge))
                return false;

            Integer span = ConvertHelper.toInt(tcPrN.selectOne(SELECTOR_TC_PR_SPAN));
            if (span != null)
                cell.setMergeAcross(span - 1);
        }
        String data = this.parseData(node);
        cell.setValue(data);
        return true;
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