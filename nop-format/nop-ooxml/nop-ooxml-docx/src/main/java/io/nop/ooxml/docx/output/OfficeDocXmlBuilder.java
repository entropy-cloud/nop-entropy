/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.output;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;

import java.util.List;

/**
 * Converts OfficeDocModel objects to OOXML XNode trees. All methods are pure
 * functions with no I/O side effects.
 */
public class OfficeDocXmlBuilder {

    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String NS_W14 = "http://schemas.microsoft.com/office/word/2010/wordml";

    // ---- document-level --------------------------------------------------

    public XNode buildDocumentXml(OfficeDocModel doc) {
        XNode document = XNode.make("w:document");
        document.setAttr("xmlns:w", NS_W);
        document.setAttr("xmlns:r", NS_R);
        document.setAttr("xmlns:mc", NS_MC);
        document.setAttr("xmlns:w14", NS_W14);

        XNode body = document.makeChild("w:body");

        for (OfficeDocPageModel page : doc.getPages()) {
            for (OfficeBlock block : page.getBody()) {
                body.appendChild(buildBlock(block));
            }
            body.appendChild(buildSectPr(doc, page));
        }

        return document;
    }

    public XNode buildHeaderFooterXml(List<OfficeBlock> blocks) {
        XNode root = XNode.make("w:hdr");
        for (OfficeBlock block : blocks) {
            root.appendChild(buildBlock(block));
        }
        return root;
    }

    // ---- sectPr ----------------------------------------------------------

    protected XNode buildSectPr(OfficeDocModel doc, OfficeDocPageModel page) {
        XNode sectPr = XNode.make("w:sectPr");

        if (doc.getWidth() > 0 || doc.getHeight() > 0) {
            XNode pgSz = sectPr.makeChild("w:pgSz");
            if (doc.getWidth() > 0) {
                pgSz.setAttr("w:w", pointsToTwips(doc.getWidth()));
            }
            if (doc.getHeight() > 0) {
                pgSz.setAttr("w:h", pointsToTwips(doc.getHeight()));
            }
            if (!StringHelper.isEmpty(page.getOrientation())) {
                pgSz.setAttr("w:orient", page.getOrientation());
            }
        }

        if (!page.getHeader().isEmpty()) {
            XNode headerRef = XNode.make("w:headerReference");
            headerRef.setAttr("w:type", "default");
            headerRef.setAttr("r:id", "rIdHdr1");
            sectPr.appendChild(headerRef);
        }

        if (!page.getFooter().isEmpty()) {
            XNode footerRef = XNode.make("w:footerReference");
            footerRef.setAttr("w:type", "default");
            footerRef.setAttr("r:id", "rIdFtr1");
            sectPr.appendChild(footerRef);
        }

        return sectPr;
    }

    // ---- block dispatch --------------------------------------------------

    protected XNode buildBlock(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) {
            return buildParagraph((OfficeParagraphModel) block);
        } else if (block instanceof WordTable) {
            return buildTable((WordTable) block);
        }
        return XNode.make("w:p");
    }

    // ---- paragraph -------------------------------------------------------

    public XNode buildParagraph(OfficeParagraphModel para) {
        XNode pNode = XNode.make("w:p");

        if (para.getStyle() != null) {
            XNode pPr = buildParagraphStyle(para.getStyle());
            if (pPr != null && pPr.getChildCount() > 0) {
                pNode.appendChild(pPr);
            }
        }

        if (para.getRuns() != null) {
            for (OfficeRunModel run : para.getRuns()) {
                pNode.appendChild(buildRun(run));
            }
        }

        return pNode;
    }

    protected XNode buildParagraphStyle(WordParagraphStyle style) {
        XNode pPr = XNode.make("w:pPr");

        if (!StringHelper.isEmpty(style.getId())) {
            XNode pStyle = pPr.makeChild("w:pStyle");
            pStyle.setAttr("w:val", style.getId());
        }

        if (style.getAlign() != null) {
            XNode jc = pPr.makeChild("w:jc");
            jc.setAttr("w:val", style.getAlign().getWmlText());
        }

        Integer beforeTwips = pointsToTwips(style.getSpaceBefore());
        Integer afterTwips = pointsToTwips(style.getSpaceAfter());
        if (beforeTwips != null || afterTwips != null) {
            XNode spacing = pPr.makeChild("w:spacing");
            if (beforeTwips != null) {
                spacing.setAttr("w:before", beforeTwips);
            }
            if (afterTwips != null) {
                spacing.setAttr("w:after", afterTwips);
            }
        }

        return pPr;
    }

    // ---- run -------------------------------------------------------------

    public XNode buildRun(OfficeRunModel run) {
        XNode rNode = XNode.make("w:r");

        if (run.getStyle() != null) {
            XNode rPr = buildRunStyle(run.getStyle());
            if (rPr != null && rPr.getChildCount() > 0) {
                rNode.appendChild(rPr);
            }
        }

        if (run.getT() != null) {
            XNode t = rNode.makeChild("w:t");
            t.content(run.getT());
            t.setAttr("xml:space", "preserve");
        }

        return rNode;
    }

    protected XNode buildRunStyle(WordRunStyle style) {
        XNode rPr = XNode.make("w:rPr");

        if (!StringHelper.isEmpty(style.getId())) {
            XNode rStyle = rPr.makeChild("w:rStyle");
            rStyle.setAttr("w:val", style.getId());
        }

        OfficeFont font = style.getFont();
        if (font != null) {
            if (font.isBold()) {
                rPr.makeChild("w:b");
            }
            if (font.isItalic()) {
                rPr.makeChild("w:i");
            }
            if (font.getUnderlineStyle() != null) {
                XNode u = rPr.makeChild("w:u");
                u.setAttr("w:val", font.getUnderlineStyle().getWmlText());
            }
            if (font.getFontSize() != null) {
                XNode sz = rPr.makeChild("w:sz");
                sz.setAttr("w:val", pointsToHalfPoints(font.getFontSize()));
                // Also set szCs for complex script
                XNode szCs = rPr.makeChild("w:szCs");
                szCs.setAttr("w:val", pointsToHalfPoints(font.getFontSize()));
            }
            if (!StringHelper.isEmpty(font.getFontColor())) {
                XNode color = rPr.makeChild("w:color");
                color.setAttr("w:val", font.getFontColor());
            }
            if (!StringHelper.isEmpty(font.getFontName())) {
                XNode rFonts = rPr.makeChild("w:rFonts");
                rFonts.setAttr("w:ascii", font.getFontName());
                rFonts.setAttr("w:eastAsia", font.getFontName());
                rFonts.setAttr("w:hAnsi", font.getFontName());
            }
        }

        if (!StringHelper.isEmpty(style.getHighlightColor())) {
            XNode highlight = rPr.makeChild("w:highlight");
            highlight.setAttr("w:val", style.getHighlightColor());
        }

        return rPr;
    }

    // ---- table -----------------------------------------------------------

    public XNode buildTable(WordTable table) {
        XNode tblNode = XNode.make("w:tbl");

        // tblGrid with gridCol entries
        XNode tblGrid = tblNode.makeChild("w:tblGrid");
        if (table.getCols() != null) {
            for (WordTableColumnConfig col : table.getCols()) {
                XNode gridCol = XNode.make("w:gridCol");
                tblGrid.appendChild(gridCol);
                if (col.getWidth() != null) {
                    gridCol.setAttr("w:w", pointsToTwips(col.getWidth()));
                }
            }
        }

        // rows
        if (table.getRows() != null) {
            for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
                WordTableRow row = table.getRows().get(rowIndex);
                tblNode.appendChild(buildTableRow(table, row, rowIndex));
            }
        }

        return tblNode;
    }

    protected XNode buildTableRow(WordTable table, WordTableRow row, int rowIndex) {
        XNode trNode = XNode.make("w:tr");

        if (row.getHeight() != null) {
            XNode trPr = trNode.makeChild("w:trPr");
            XNode trHeight = trPr.makeChild("w:trHeight");
            trHeight.setAttr("w:val", pointsToTwips(row.getHeight()));
            trHeight.setAttr("w:hRule", "atLeast");
        }

        if (row.getCells() != null) {
            for (WordTableCell cell : row.getCells()) {
                trNode.appendChild(buildTableCell(table, cell, rowIndex));
            }
        }

        return trNode;
    }

    protected XNode buildTableCell(WordTable table, WordTableCell cell, int rowIndex) {
        XNode tcNode = XNode.make("w:tc");

        if (cell.isProxyCell()) {
            // Continuation of a vertical merge
            XNode tcPr = tcNode.makeChild("w:tcPr");
            tcPr.makeChild("w:vMerge");
            tcNode.makeChild("w:p");
            return tcNode;
        }

        boolean hasTcPr = cell.getMergeAcross() > 0 || cell.getMergeDown() > 0;

        if (hasTcPr) {
            XNode tcPr = tcNode.makeChild("w:tcPr");

            if (cell.getMergeAcross() > 0) {
                XNode gridSpan = tcPr.makeChild("w:gridSpan");
                gridSpan.setAttr("w:val", cell.getMergeAcross() + 1);
            }

            if (cell.getMergeDown() > 0) {
                XNode vMerge = tcPr.makeChild("w:vMerge");
                vMerge.setAttr("w:val", "restart");
            }
        }

        // Cell content as a paragraph
        XNode p = tcNode.makeChild("w:p");
        String text = cell.getText();
        if (text != null) {
            XNode r = p.makeChild("w:r");
            XNode t = r.makeChild("w:t");
            t.content(text);
            t.setAttr("xml:space", "preserve");
        }

        return tcNode;
    }

    // ---- unit conversion helpers -----------------------------------------

    public static Integer pointsToTwips(Double pts) {
        if (pts == null) {
            return null;
        }
        return (int) Math.round(pts * 20);
    }

    public static Integer pointsToTwips(double pts) {
        return (int) Math.round(pts * 20);
    }

    public static Integer pointsToHalfPoints(Float pts) {
        if (pts == null) {
            return null;
        }
        return (int) Math.round(pts * 2);
    }
}
