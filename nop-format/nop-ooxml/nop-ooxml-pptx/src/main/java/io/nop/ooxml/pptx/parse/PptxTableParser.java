/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.pptx.parse;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelTable;
import io.nop.ooxml.common.model.ImageUrlMapper;

import java.util.Objects;

/*
PowerPoint表格XML结构：
<a:tbl>
  <a:tblPr firstRow="1" bandRow="1">
    <a:tableStyleId>{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId>
  </a:tblPr>
  <a:tblGrid>
    <a:gridCol w="2032000"/>
    <a:gridCol w="2032000"/>
    <a:gridCol w="2032000"/>
  </a:tblGrid>
  <a:tr h="370840">
    <a:tc>
      <a:txBody>
        <a:bodyPr/>
        <a:lstStyle/>
        <a:p>
          <a:r>
            <a:t>Cell Content</a:t>
          </a:r>
        </a:p>
      </a:txBody>
      <a:tcPr>
        <a:lnL w="12700">
          <a:solidFill>
            <a:schemeClr val="dk1"/>
          </a:solidFill>
        </a:lnL>
      </a:tcPr>
    </a:tc>
  </a:tr>
</a:tbl>
*/
public class PptxTableParser {
    private ImageUrlMapper urlMapper;
    private boolean forMarkdown;

    public PptxTableParser imageUrlMapper(ImageUrlMapper urlMapper) {
        this.urlMapper = urlMapper;
        return this;
    }

    public PptxTableParser forMarkdown(boolean forMarkdown) {
        this.forMarkdown = forMarkdown;
        return this;
    }

    public ExcelTable parseTable(XNode tblNode) {
        ExcelTable tbl = new ExcelTable();
        int i, n = tblNode.getChildCount();
        int rowIndex = 0;

        for (i = 0; i < n; i++) {
            XNode child = tblNode.child(i);
            String name = child.getTagName();
            if (name.equals("a:tr")) {
                parseRow(rowIndex, child, tbl);
                rowIndex++;
            }
        }
        return tbl;
    }

    protected ExcelRow parseRow(int rowIndex, XNode node, ExcelTable tbl) {
        int i, n = node.getChildCount();
        int colIndex = 0;

        for (i = 0; i < n; i++) {
            XNode child = node.child(i);
            String name = child.getTagName();
            if (name.equals("a:tc")) {
                ExcelCell cell = new ExcelCell();
                if (parseCell(child, cell)) {
                    tbl.setCell(rowIndex, colIndex, cell);
                }
                colIndex += cell.getMergeAcross() + 1;
            }
        }

        ExcelRow row = tbl.makeRow(rowIndex);

        Double height = getRowHeight(node);
        if (height != null)
            row.setHeight(height);
        return row;
    }

    protected Double getRowHeight(XNode node) {
        // PowerPoint行高在a:tr的h属性中，单位是EMU (English Metric Units)
        String heightStr = node.attrText("h");
        if (heightStr != null) {
            try {
                long heightEmu = Long.parseLong(heightStr);
                // 转换EMU到磅值 (1 EMU = 1/914400 inch, 1 inch = 72 points)
                return heightEmu / 914400.0 * 72.0;
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }
        }
        return null;
    }

    protected String parseData(XNode node) {
        return PptxXmlHelper.getText(node, forMarkdown, urlMapper);
    }

    protected boolean parseCell(XNode node, ExcelCell cell) {
        XNode tcPr = node.childByTag("a:tcPr");

        // 处理单元格合并
        handleCellSpanning(node, cell);

        // 提取单元格文本内容
        String data = this.parseData(node);
        cell.setValue(data);
        return true;
    }

    private void handleCellSpanning(XNode tcNode, ExcelCell cell) {
        XNode tcPr = tcNode.childByTag("a:tcPr");
        if (tcPr == null) return;

        // 处理水平合并 (gridSpan)
        String gridSpanStr = tcPr.attrText("gridSpan");
        if (gridSpanStr != null) {
            try {
                int gridSpan = Integer.parseInt(gridSpanStr);
                if (gridSpan > 1) {
                    cell.setMergeAcross(gridSpan - 1);
                }
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }
        }

        // 处理垂直合并 (rowSpan)
        String rowSpanStr = tcPr.attrText("rowSpan");
        if (rowSpanStr != null) {
            try {
                int rowSpan = Integer.parseInt(rowSpanStr);
                if (rowSpan > 1) {
                    cell.setMergeDown(rowSpan - 1);
                }
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }
        }

        // PowerPoint也可能使用vMerge和hMerge属性
        handleVerticalMerge(tcNode, cell);
        handleHorizontalMerge(tcNode, cell);
    }

    private void handleVerticalMerge(XNode tcNode, ExcelCell cell) {
        XNode tcPr = tcNode.childByTag("a:tcPr");
        if (tcPr == null) return;

        String vMerge = tcPr.attrText("vMerge");
        if ("restart".equals(vMerge)) {
            // 计算垂直合并的单元格数量
            int mergeDown = calculateVerticalMerge(tcNode);
            if (mergeDown > 0) {
                cell.setMergeDown(mergeDown);
            }
        }
    }

    private void handleHorizontalMerge(XNode tcNode, ExcelCell cell) {
        XNode tcPr = tcNode.childByTag("a:tcPr");
        if (tcPr == null) return;

        String hMerge = tcPr.attrText("hMerge");
        if ("restart".equals(hMerge)) {
            // 计算水平合并的单元格数量
            int mergeAcross = calculateHorizontalMerge(tcNode);
            if (mergeAcross > 0) {
                cell.setMergeAcross(mergeAcross);
            }
        }
    }

    private int calculateVerticalMerge(XNode tcNode) {
        int cellIndex = tcNode.childIndex();
        XNode row = tcNode.getParent();
        XNode tblNode = row.getParent();
        int mergeDown = 0;

        // 从下一行开始检查
        int rowIndex = row.childIndex() + 1;
        while (rowIndex < tblNode.getChildCount()) {
            XNode nextRow = tblNode.child(rowIndex);
            if (!nextRow.getTagName().equals("a:tr")) {
                rowIndex++;
                continue;
            }

            // 确保下一行有足够的单元格
            if (cellIndex >= nextRow.getChildCount()) {
                break;
            }

            XNode nextCell = nextRow.child(cellIndex);
            if (!nextCell.getTagName().equals("a:tc")) {
                break;
            }

            XNode nextTcPr = nextCell.childByTag("a:tcPr");
            if (nextTcPr != null) {
                String nextVMerge = nextTcPr.attrText("vMerge");
                if (!"continue".equals(nextVMerge)) {
                    break;
                }
            } else {
                break;
            }

            mergeDown++;
            rowIndex++;
        }

        return mergeDown;
    }

    private int calculateHorizontalMerge(XNode tcNode) {
        XNode row = tcNode.getParent();
        int cellIndex = tcNode.childIndex();
        int mergeAcross = 0;

        // 检查右侧的单元格
        for (int i = cellIndex + 1; i < row.getChildCount(); i++) {
            XNode nextCell = row.child(i);
            if (!nextCell.getTagName().equals("a:tc")) {
                break;
            }

            XNode nextTcPr = nextCell.childByTag("a:tcPr");
            if (nextTcPr != null) {
                String nextHMerge = nextTcPr.attrText("hMerge");
                if (!"continue".equals(nextHMerge)) {
                    break;
                }
            } else {
                break;
            }

            mergeAcross++;
        }

        return mergeAcross;
    }

    public String getFirstCellData(XNode node) {
        XNode tr = node.childByTag("a:tr");
        if (tr == null)
            return null;
        XNode tc = tr.childByTag("a:tc");
        if (tc == null)
            return null;
        return parseData(tc);
    }

    public boolean isMatchHeader(XNode node, String value) {
        String header = getFirstCellData(node);
        header = StringHelper.strip(header);
        return Objects.equals(header, value);
    }

    /**
     * 获取表格的列数
     */
    public int getColumnCount(XNode tblNode) {
        XNode tblGrid = tblNode.childByTag("a:tblGrid");
        if (tblGrid != null) {
            return (int) tblGrid.getChildren().stream()
                    .filter(child -> "a:gridCol".equals(child.getTagName()))
                    .count();
        }

        // 如果没有tblGrid，从第一行计算
        XNode firstRow = tblNode.childByTag("a:tr");
        if (firstRow != null) {
            return (int) firstRow.getChildren().stream()
                    .filter(child -> "a:tc".equals(child.getTagName()))
                    .count();
        }

        return 0;
    }

    /**
     * 获取表格的行数
     */
    public int getRowCount(XNode tblNode) {
        return (int) tblNode.getChildren().stream()
                .filter(child -> "a:tr".equals(child.getTagName()))
                .count();
    }

    /**
     * 检查表格是否有表头
     */
    public boolean hasHeader(XNode tblNode) {
        XNode tblPr = tblNode.childByTag("a:tblPr");
        if (tblPr != null) {
            String firstRow = tblPr.attrText("firstRow");
            return "1".equals(firstRow) || "true".equals(firstRow);
        }
        return false;
    }
}