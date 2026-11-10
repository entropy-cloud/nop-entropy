/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.output;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficePageBreakModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.OfficeTabModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.OfficeBorderStyle;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordBorderStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.WordTableStyle;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.constants.TargetMode;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxErrors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static io.nop.ooxml.docx.DocxErrors.ARG_BLOCK_TYPE;
import static io.nop.ooxml.docx.DocxErrors.ARG_REASON;
import static io.nop.ooxml.docx.DocxErrors.ARG_TARGET_ID;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_GEN_BLOCK_TYPE_UNKNOWN;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_GEN_IMAGE_INVALID;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_GEN_LIST_NUM_ID_MISSING;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_GEN_RUN_TYPE_UNKNOWN;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_GEN_TITLE_LEVEL_INVALID;

/**
 * Converts OfficeDocModel objects to OOXML XNode trees (WI5: all eight block types,
 * four inline run types, full run/paragraph styles, table borders/shading/cell styles,
 * per-section sectPr placement, background element and named styles.xml).
 *
 * <p>Relationship-bearing content (hyperlinks, images) needs a part-scoped
 * {@link PartContext}; the no-context overloads keep working for standalone use by
 * synthesizing relationship ids without registering them.</p>
 */
public class OfficeDocXmlBuilder {

    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String NS_W14 = "http://schemas.microsoft.com/office/word/2010/wordml";
    private static final String NS_WP =
            "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String NS_PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private static final String URI_PIC = NS_PIC;

    /**
     * Separator blocks carry no style fields in the model; the visual form (bottom
     * border on an otherwise empty paragraph) is a fixed generation convention
     * mirrored on what the corpus/parser recognize as a separator paragraph.
     */
    private static final String SEPARATOR_BORDER_VAL = "single";
    private static final int SEPARATOR_BORDER_SZ = 6;
    private static final int SEPARATOR_BORDER_SPACE = 1;
    private static final String SEPARATOR_BORDER_COLOR = "808080";

    /**
     * Part-scoped generation state: relationship sink for the part being built plus
     * the resolver mapping image assetRef -> package-internal media part path.
     */
    public static class PartContext {
        private final OfficeRelsPart rels;
        private final Function<String, String> imageTargetResolver;
        private int standaloneSeq = 0;
        private int docPrSeq = 0;

        public PartContext(OfficeRelsPart rels, Function<String, String> imageTargetResolver) {
            this.rels = rels;
            this.imageTargetResolver = imageTargetResolver;
        }

        public boolean hasRels() {
            return rels != null;
        }

        public OfficeRelsPart getRels() {
            return rels;
        }

        String nextDocPrId() {
            return String.valueOf(++docPrSeq);
        }

        String addHyperlinkRel(String href) {
            if (rels != null) {
                return rels.addRelationship(OfficeConstants.NS_LINK, href, TargetMode.External);
            }
            return "rIdHl" + (++standaloneSeq);
        }

        String addImageRel(String assetRef) {
            String target = imageTargetResolver == null ? null : imageTargetResolver.apply(assetRef);
            if (target == null) {
                throw new NopException(ERR_DOCX_GEN_IMAGE_INVALID)
                        .param(ARG_REASON, "assetRef is not present in doc.assets")
                        .param(ARG_TARGET_ID, assetRef);
            }
            if (rels != null) {
                return rels.addImage(target).getId();
            }
            return "rIdImg" + (++standaloneSeq);
        }
    }

    /**
     * Doc-scoped state used to resolve named cell styles referenced by
     * cell.styleId during table generation. Null for standalone builder use.
     */
    private OfficeDocModel doc;

    /**
     * Part context for relationship allocation (hyperlinks/images). Null means
     * standalone generation: ids are synthesized but no relationship is registered.
     */
    private PartContext partContext;

    public void setDoc(OfficeDocModel doc) {
        this.doc = doc;
    }

    public void setPartContext(PartContext context) {
        this.partContext = context;
    }

    private PartContext requirePartContext() {
        if (partContext == null) {
            throw new NopException(ERR_DOCX_GEN_IMAGE_INVALID)
                    .param(ARG_REASON, "no part context for relationship allocation")
                    .param(ARG_TARGET_ID, null);
        }
        return partContext;
    }

    // ---- document-level --------------------------------------------------

    public XNode buildDocumentXml(OfficeDocModel doc) {
        return buildDocumentXml(doc, null);
    }

    /**
     * G3: each page is one section. A non-last section's sectPr is embedded in the
     * pPr of that section's last paragraph (a dedicated empty w:p carries it when the
     * section does not end with a paragraph); the last section's sectPr sits at body
     * level. The document root carries w:background when the model has one.
     */
    public XNode buildDocumentXml(OfficeDocModel doc, PartContext context) {
        XNode document = XNode.make("w:document");
        document.setAttr("xmlns:w", NS_W);
        document.setAttr("xmlns:r", NS_R);
        document.setAttr("xmlns:mc", NS_MC);
        document.setAttr("xmlns:w14", NS_W14);

        if (!StringHelper.isEmpty(doc.getBackground())) {
            XNode background = XNode.make("w:background");
            background.setAttr("w:color", doc.getBackground());
            document.appendChild(background);
        }

        XNode body = document.makeChild("w:body");

        List<OfficeDocPageModel> pages = doc.getPages();
        for (int i = 0; i < pages.size(); i++) {
            OfficeDocPageModel page = pages.get(i);
            if (page.getBody() != null) {
                for (OfficeBlock block : page.getBody()) {
                    for (XNode node : buildBlocks(block)) {
                        body.appendChild(node);
                    }
                }
            }

            XNode sectPr = buildSectPr(doc, page, i + 1);
            if (i < pages.size() - 1) {
                embedSectPr(body, sectPr);
            } else {
                body.appendChild(sectPr);
            }
        }

        return document;
    }

    /**
     * Embeds a section break into the last paragraph currently in body (G3). When the
     * section does not end with a paragraph, a dedicated empty paragraph carries the
     * sectPr (an extra empty block appears on re-parse in that case).
     */
    private void embedSectPr(XNode body, XNode sectPr) {
        List<XNode> children = body.getChildren();
        XNode last = children.isEmpty() ? null : children.get(children.size() - 1);
        if (last == null || !"w:p".equals(last.getTagName())) {
            XNode p = XNode.make("w:p");
            XNode pPr = p.makeChild("w:pPr");
            pPr.appendChild(sectPr);
            body.appendChild(p);
            return;
        }
        XNode pPr = last.childByTag("w:pPr");
        if (pPr == null) {
            pPr = XNode.make("w:pPr");
            last.insertChild(0, pPr);
        }
        pPr.appendChild(sectPr);
    }

    public XNode buildHeaderFooterXml(List<OfficeBlock> blocks) {
        XNode root = XNode.make("w:hdr");
        root.setAttr("xmlns:w", NS_W);
        root.setAttr("xmlns:r", NS_R);
        if (blocks != null) {
            for (OfficeBlock block : blocks) {
                for (XNode node : buildBlocks(block)) {
                    root.appendChild(node);
                }
            }
        }
        return root;
    }

    /**
     * Builds word/settings.xml content. Currently only the displayBackgroundShape
     * flag is produced (the only settings aspect the model carries, G4).
     */
    public XNode buildSettingsXml() {
        XNode root = XNode.make("w:settings");
        root.setAttr("xmlns:w", NS_W);
        root.makeChild("w:displayBackgroundShape");
        return root;
    }

    // ---- sectPr ----------------------------------------------------------

    public XNode buildSectPr(OfficeDocModel doc, OfficeDocPageModel page) {
        return buildSectPr(doc, page, 1);
    }

    /**
     * sectPr for section {@code sectionIndex} (1-based). Header/footer parts are
     * named headerN/footerN and referenced by the deterministic ids rIdHdrN/rIdFtrN.
     */
    public XNode buildSectPr(OfficeDocModel doc, OfficeDocPageModel page, int sectionIndex) {
        XNode sectPr = XNode.make("w:sectPr");

        double width = page.getWidth() != null && page.getWidth() > 0 ? page.getWidth() : doc.getWidth();
        double height = page.getHeight() != null && page.getHeight() > 0 ? page.getHeight() : doc.getHeight();
        if (width > 0 || height > 0 || !StringHelper.isEmpty(page.getOrientation())) {
            XNode pgSz = sectPr.makeChild("w:pgSz");
            if (width > 0) {
                pgSz.setAttr("w:w", pointsToTwips(width));
            }
            if (height > 0) {
                pgSz.setAttr("w:h", pointsToTwips(height));
            }
            if (!StringHelper.isEmpty(page.getOrientation())) {
                pgSz.setAttr("w:orient", page.getOrientation());
            }
        }

        if (page.getMargins() != null) {
            XNode pgMar = sectPr.makeChild("w:pgMar");
            pgMar.setAttr("w:top", pointsToTwips(page.getMargins().getTop()));
            pgMar.setAttr("w:right", pointsToTwips(page.getMargins().getRight()));
            pgMar.setAttr("w:bottom", pointsToTwips(page.getMargins().getBottom()));
            pgMar.setAttr("w:left", pointsToTwips(page.getMargins().getLeft()));
            pgMar.setAttr("w:header", pointsToTwips(page.getMargins().getHeader()));
            pgMar.setAttr("w:footer", pointsToTwips(page.getMargins().getFooter()));
            pgMar.setAttr("w:gutter", pointsToTwips(page.getMargins().getGutter()));
        }

        if (!page.getHeader().isEmpty()) {
            XNode headerRef = sectPr.makeChild("w:headerReference");
            headerRef.setAttr("w:type", "default");
            headerRef.setAttr("r:id", "rIdHdr" + sectionIndex);
        }

        if (!page.getFooter().isEmpty()) {
            XNode footerRef = sectPr.makeChild("w:footerReference");
            footerRef.setAttr("w:type", "default");
            footerRef.setAttr("r:id", "rIdFtr" + sectionIndex);
        }

        return sectPr;
    }

    // ---- block dispatch --------------------------------------------------

    /**
     * Dispatch by block type. Returns a list because a list block renders as one
     * w:p per item. G5: unknown block types fail fast instead of producing a silent
     * empty w:p.
     */
    protected List<XNode> buildBlocks(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) {
            return java.util.Collections.singletonList(buildParagraph((OfficeParagraphModel) block));
        } else if (block instanceof WordTable) {
            return java.util.Collections.singletonList(buildTable((WordTable) block));
        } else if (block instanceof OfficeTitleModel) {
            return java.util.Collections.singletonList(buildTitle((OfficeTitleModel) block));
        } else if (block instanceof OfficeListModel) {
            return buildListBlocks((OfficeListModel) block);
        } else if (block instanceof OfficeImageModel) {
            return java.util.Collections.singletonList(buildImageBlock((OfficeImageModel) block));
        } else if (block instanceof OfficeHyperlinkModel) {
            return java.util.Collections.singletonList(buildHyperlinkBlock((OfficeHyperlinkModel) block));
        } else if (block instanceof OfficeSeparatorModel) {
            return java.util.Collections.singletonList(buildSeparator((OfficeSeparatorModel) block));
        } else if (block instanceof OfficePageBreakModel) {
            return java.util.Collections.singletonList(buildPageBreak((OfficePageBreakModel) block));
        }
        throw new NopException(ERR_DOCX_GEN_BLOCK_TYPE_UNKNOWN)
                .param(ARG_BLOCK_TYPE, block == null ? null : block.getClass().getName());
    }

    protected XNode buildBlock(OfficeBlock block) {
        List<XNode> nodes = buildBlocks(block);
        if (nodes.size() != 1) {
            throw new NopException(ERR_DOCX_GEN_BLOCK_TYPE_UNKNOWN)
                    .param(ARG_BLOCK_TYPE, block.getClass().getName() + " expands to " + nodes.size() + " nodes");
        }
        return nodes.get(0);
    }

    // ---- paragraph / title -----------------------------------------------

    public XNode buildParagraph(OfficeParagraphModel para) {
        XNode pNode = XNode.make("w:p");

        if (para.getStyle() != null) {
            XNode pPr = buildParagraphStyle(para.getStyle());
            if (pPr != null && pPr.getChildCount() > 0) {
                pNode.appendChild(pPr);
            }
        }

        if (para.getRuns() != null) {
            for (OfficeRun run : para.getRuns()) {
                pNode.appendChild(buildRun(run));
            }
        }

        return pNode;
    }

    public XNode buildTitle(OfficeTitleModel title) {
        XNode pNode = XNode.make("w:p");
        WordParagraphStyle style = title.getStyle();

        XNode pPr = XNode.make("w:pPr");
        String pStyleId = style == null ? null : style.getId();
        Integer titleLevel = style == null ? null : style.getTitleLevel();
        if (StringHelper.isEmpty(pStyleId)) {
            // Adjudication: a title without an explicit style id maps level 1 -> Title
            // and level N (2..9) -> HeadingN; anything else is not representable.
            if (titleLevel != null && titleLevel == 1) {
                pStyleId = "Title";
            } else if (titleLevel != null && titleLevel >= 2 && titleLevel <= 9) {
                pStyleId = "Heading" + titleLevel;
            } else {
                throw new NopException(ERR_DOCX_GEN_TITLE_LEVEL_INVALID)
                        .param(ARG_TARGET_ID, title.getId());
            }
        }
        XNode pStyle = pPr.makeChild("w:pStyle");
        pStyle.setAttr("w:val", pStyleId);

        if (style != null) {
            buildParagraphStyleChildren(style, pPr);
        }

        if (titleLevel != null && titleLevel >= 1 && titleLevel <= 9) {
            XNode outlineLvl = pPr.makeChild("w:outlineLvl");
            outlineLvl.setAttr("w:val", titleLevel - 1);
        }

        pNode.appendChild(pPr);

        if (title.getRuns() != null) {
            for (OfficeRun run : title.getRuns()) {
                pNode.appendChild(buildRun(run));
            }
        }
        return pNode;
    }

    /**
     * Appends spacing/ind/jc/outlineLvl to an existing pPr in CT_PPrBase order
     * (pStyle/numPr/pBdr are inserted by the callers before these).
     */
    private void buildParagraphStyleChildren(WordParagraphStyle style, XNode pPr) {
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

        Integer leftTwips = pointsToTwips(style.getLeftIndent());
        Integer rightTwips = pointsToTwips(style.getRightIndent());
        Integer firstLineTwips = pointsToTwips(style.getFirstLineIndent());
        if (leftTwips != null || rightTwips != null || firstLineTwips != null) {
            XNode ind = pPr.makeChild("w:ind");
            if (leftTwips != null) {
                ind.setAttr("w:left", leftTwips);
            }
            if (rightTwips != null) {
                ind.setAttr("w:right", rightTwips);
            }
            if (firstLineTwips != null) {
                ind.setAttr("w:firstLine", firstLineTwips);
            }
        }

        if (style.getAlign() != null) {
            XNode jc = pPr.makeChild("w:jc");
            jc.setAttr("w:val", style.getAlign().getWmlText());
        }

        if (style.getTitleLevel() != null && style.getTitleLevel() >= 1 && style.getTitleLevel() <= 9) {
            XNode outlineLvl = pPr.makeChild("w:outlineLvl");
            outlineLvl.setAttr("w:val", style.getTitleLevel() - 1);
        }
    }

    protected XNode buildParagraphStyle(WordParagraphStyle style) {
        XNode pPr = XNode.make("w:pPr");

        if (!StringHelper.isEmpty(style.getId())) {
            XNode pStyle = pPr.makeChild("w:pStyle");
            pStyle.setAttr("w:val", style.getId());
        }

        buildParagraphStyleChildren(style, pPr);
        return pPr;
    }

    // ---- list ------------------------------------------------------------

    /**
     * A list renders as one w:p per item, each carrying numPr(ilvl/numId) plus the
     * item's own paragraph style.
     */
    public List<XNode> buildListBlocks(OfficeListModel list) {
        if (StringHelper.isEmpty(list.getNumId())) {
            throw new NopException(ERR_DOCX_GEN_LIST_NUM_ID_MISSING).param(ARG_TARGET_ID, list.getId());
        }

        List<XNode> nodes = new java.util.ArrayList<>();
        if (list.getItems() != null) {
            for (OfficeListItemModel item : list.getItems()) {
                XNode pNode = XNode.make("w:p");

                XNode pPr = XNode.make("w:pPr");
                XNode numPr = pPr.makeChild("w:numPr");
                XNode ilvl = numPr.makeChild("w:ilvl");
                ilvl.setAttr("w:val", item.getLevel());
                XNode numId = numPr.makeChild("w:numId");
                numId.setAttr("w:val", list.getNumId());

                if (item.getStyle() != null) {
                    buildParagraphStyleChildren(item.getStyle(), pPr);
                }
                pNode.appendChild(pPr);

                if (item.getRuns() != null) {
                    for (OfficeRun run : item.getRuns()) {
                        pNode.appendChild(buildRun(run));
                    }
                }
                nodes.add(pNode);
            }
        }
        if (nodes.isEmpty()) {
            // a list with no items still yields one empty numbered paragraph to keep
            // the block visible in the generated document
            XNode pNode = XNode.make("w:p");
            XNode pPr = pNode.makeChild("w:pPr");
            XNode numPr = pPr.makeChild("w:numPr");
            numPr.makeChild("w:ilvl").setAttr("w:val", 0);
            numPr.makeChild("w:numId").setAttr("w:val", list.getNumId());
            nodes.add(pNode);
        }
        return nodes;
    }

    // ---- image / hyperlink blocks -----------------------------------------

    public XNode buildImageBlock(OfficeImageModel image) {
        XNode pNode = XNode.make("w:p");
        pNode.appendChild(buildDrawing(image));
        return pNode;
    }

    /**
     * Inline drawing generated in the same shape the parser recognizes as a
     * standalone image paragraph (wp:inline + extent + pic structure). Extent is
     * points -> EMU (x 12700).
     */
    public XNode buildDrawing(OfficeImageModel image) {
        XNode runNode = XNode.make("w:r");
        XNode drawing = runNode.makeChild("w:drawing");
        drawing.setAttr("xmlns:wp", NS_WP);

        if (image.getWidth() == null || image.getHeight() == null) {
            throw new NopException(ERR_DOCX_GEN_IMAGE_INVALID)
                    .param(ARG_REASON, "image width/height missing")
                    .param(ARG_TARGET_ID, image.getId());
        }
        long cx = Math.round(image.getWidth() * 12700);
        long cy = Math.round(image.getHeight() * 12700);

        XNode inline = drawing.makeChild("wp:inline");
        inline.setAttr("distT", 0);
        inline.setAttr("distB", 0);
        inline.setAttr("distL", 0);
        inline.setAttr("distR", 0);

        XNode extent = inline.makeChild("wp:extent");
        extent.setAttr("cx", cx);
        extent.setAttr("cy", cy);

        XNode docPr = inline.makeChild("wp:docPr");
        docPr.setAttr("id", partContext == null ? "1" : requirePartContext().nextDocPrId());
        docPr.setAttr("name", "image" + image.getId());

        XNode graphic = inline.makeChild("a:graphic");
        graphic.setAttr("xmlns:a", NS_A);
        XNode graphicData = graphic.makeChild("a:graphicData");
        graphicData.setAttr("uri", URI_PIC);

        XNode pic = graphicData.makeChild("pic:pic");
        pic.setAttr("xmlns:pic", NS_PIC);

        XNode nvPicPr = pic.makeChild("pic:nvPicPr");
        XNode cNvPr = nvPicPr.makeChild("pic:cNvPr");
        cNvPr.setAttr("id", 1);
        cNvPr.setAttr("name", StringHelper.isEmpty(image.getAssetRef()) ? "image"
                : StringHelper.fileName(image.getAssetRef()));
        nvPicPr.makeChild("pic:cNvPicPr");

        XNode blipFill = pic.makeChild("pic:blipFill");
        XNode blip = blipFill.makeChild("a:blip");
        if (StringHelper.isEmpty(image.getAssetRef())) {
            throw new NopException(ERR_DOCX_GEN_IMAGE_INVALID)
                    .param(ARG_REASON, "image has no assetRef")
                    .param(ARG_TARGET_ID, image.getId());
        }
        PartContext ctx = requirePartContext();
        blip.setAttr("r:embed", ctx.addImageRel(image.getAssetRef()));
        XNode stretch = blipFill.makeChild("a:stretch");
        stretch.makeChild("a:fillRect");

        XNode spPr = pic.makeChild("pic:spPr");
        XNode xfrm = spPr.makeChild("a:xfrm");
        XNode off = xfrm.makeChild("a:off");
        off.setAttr("x", 0);
        off.setAttr("y", 0);
        XNode ext = xfrm.makeChild("a:ext");
        ext.setAttr("cx", cx);
        ext.setAttr("cy", cy);
        XNode prstGeom = spPr.makeChild("a:prstGeom");
        prstGeom.setAttr("prst", "rect");
        prstGeom.makeChild("a:avLst");

        return runNode;
    }

    public XNode buildHyperlinkBlock(OfficeHyperlinkModel hyperlink) {
        return buildHyperlinkNode(hyperlink.getHref(), hyperlink.getAnchor(), hyperlink.getRuns());
    }

    // ---- separator / page break -------------------------------------------

    public XNode buildSeparator(OfficeSeparatorModel separator) {
        XNode pNode = XNode.make("w:p");
        XNode pPr = pNode.makeChild("w:pPr");
        XNode pBdr = pPr.makeChild("w:pBdr");
        XNode bottom = pBdr.makeChild("w:bottom");
        bottom.setAttr("w:val", SEPARATOR_BORDER_VAL);
        bottom.setAttr("w:sz", SEPARATOR_BORDER_SZ);
        bottom.setAttr("w:space", SEPARATOR_BORDER_SPACE);
        bottom.setAttr("w:color", SEPARATOR_BORDER_COLOR);
        return pNode;
    }

    public XNode buildPageBreak(OfficePageBreakModel pageBreak) {
        XNode pNode = XNode.make("w:p");
        XNode r = pNode.makeChild("w:r");
        XNode br = r.makeChild("w:br");
        br.setAttr("w:type", "page");
        return pNode;
    }

    // ---- run -------------------------------------------------------------

    public XNode buildRun(OfficeRun run) {
        if (run instanceof OfficeRunModel) {
            return buildTextRun((OfficeRunModel) run);
        }
        if (run instanceof OfficeTabModel) {
            XNode rNode = XNode.make("w:r");
            rNode.makeChild("w:tab");
            return rNode;
        }
        if (run instanceof OfficeBreakModel) {
            OfficeBreakModel br = (OfficeBreakModel) run;
            XNode rNode = XNode.make("w:r");
            XNode brNode = rNode.makeChild("w:br");
            if (!StringHelper.isEmpty(br.getBrType())) {
                brNode.setAttr("w:type", br.getBrType());
            }
            return rNode;
        }
        if (run instanceof OfficeLinkModel) {
            OfficeLinkModel link = (OfficeLinkModel) run;
            return buildHyperlinkNode(link.getHref(), link.getAnchor(), link.getRuns());
        }
        throw new NopException(ERR_DOCX_GEN_RUN_TYPE_UNKNOWN)
                .param(ARG_BLOCK_TYPE, run == null ? null : run.getClass().getName());
    }

    private XNode buildHyperlinkNode(String href, String anchor, List<OfficeRun> runs) {
        XNode hlNode = XNode.make("w:hyperlink");
        if (!StringHelper.isEmpty(anchor)) {
            hlNode.setAttr("w:anchor", anchor);
        }
        if (!StringHelper.isEmpty(href)) {
            hlNode.setAttr("r:id", requirePartContext().addHyperlinkRel(href));
        }
        if (runs != null) {
            for (OfficeRun run : runs) {
                hlNode.appendChild(buildRun(run));
            }
        }
        return hlNode;
    }

    public XNode buildTextRun(OfficeRunModel textRun) {
        XNode rNode = XNode.make("w:r");

        if (textRun.getStyle() != null) {
            XNode rPr = buildRunStyle(textRun.getStyle());
            if (rPr != null && rPr.getChildCount() > 0) {
                rNode.appendChild(rPr);
            }
        }

        if (textRun.getT() != null) {
            XNode t = rNode.makeChild("w:t");
            t.content(textRun.getT());
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
            // CT_RPr element order: rFonts, b, i, strike, color, sz, szCs, highlight, u, vertAlign
            if (!StringHelper.isEmpty(font.getFontName())) {
                XNode rFonts = rPr.makeChild("w:rFonts");
                rFonts.setAttr("w:ascii", font.getFontName());
                rFonts.setAttr("w:hAnsi", font.getFontName());
                rFonts.setAttr("w:eastAsia", font.getFontName());
            }
            if (font.isBold()) {
                rPr.makeChild("w:b");
            }
            if (font.isItalic()) {
                rPr.makeChild("w:i");
            }
            if (font.isStrikeout()) {
                rPr.makeChild("w:strike");
            }
            if (!StringHelper.isEmpty(font.getFontColor())) {
                XNode color = rPr.makeChild("w:color");
                color.setAttr("w:val", font.getFontColor());
            }
            if (font.getFontSize() != null) {
                XNode sz = rPr.makeChild("w:sz");
                sz.setAttr("w:val", pointsToHalfPoints(font.getFontSize()));
                // Also set szCs for complex script
                XNode szCs = rPr.makeChild("w:szCs");
                szCs.setAttr("w:val", pointsToHalfPoints(font.getFontSize()));
            }
            if (!StringHelper.isEmpty(style.getHighlightColor())) {
                XNode highlight = rPr.makeChild("w:highlight");
                highlight.setAttr("w:val", style.getHighlightColor());
            }
            if (font.getUnderlineStyle() != null) {
                XNode u = rPr.makeChild("w:u");
                u.setAttr("w:val", font.getUnderlineStyle().getWmlText());
            }
            if (font.getVerticalAlign() != null) {
                XNode vertAlign = rPr.makeChild("w:vertAlign");
                vertAlign.setAttr("w:val", font.getVerticalAlign().name());
            }
        } else if (!StringHelper.isEmpty(style.getHighlightColor())) {
            XNode highlight = rPr.makeChild("w:highlight");
            highlight.setAttr("w:val", style.getHighlightColor());
        }

        return rPr;
    }

    // ---- table -----------------------------------------------------------

    public XNode buildTable(WordTable table) {
        XNode tblNode = XNode.make("w:tbl");

        tblNode.appendChild(buildTblPr(table));

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
        boolean anyRowPr = false;
        if (table.getRows() != null) {
            for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
                WordTableRow row = table.getRows().get(rowIndex);
                if (row.getHeight() != null) {
                    anyRowPr = true;
                }
            }
            // The parser locates merged continuation cells by XML child index of the
            // w:tc inside its row; emitting an empty w:trPr on every row keeps those
            // indexes aligned across rows with and without heights.
            anyRowPr = anyRowPr || hasVerticalMerge(table);
            for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
                WordTableRow row = table.getRows().get(rowIndex);
                tblNode.appendChild(buildTableRow(table, row, rowIndex, anyRowPr));
            }
        }

        return tblNode;
    }

    private boolean hasVerticalMerge(WordTable table) {
        if (table.getRows() == null) {
            return false;
        }
        for (WordTableRow row : table.getRows()) {
            if (row.getCells() == null) {
                continue;
            }
            for (WordTableCell cell : row.getCells()) {
                if (cell.getMergeDown() > 0 || cell.isProxyCell()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Aggregates tblBorders from the per-cell inline styles when every real cell
     * resolves to a cell style carrying the same four borders (the uniform grid the
     * parser materializes from a table-level tblBorders).
     */
    private XNode buildTblPr(WordTable table) {
        XNode tblPr = XNode.make("w:tblPr");

        if (!StringHelper.isEmpty(table.getStyleId()) && !table.getStyleId().startsWith("__")) {
            XNode tblStyle = tblPr.makeChild("w:tblStyle");
            tblStyle.setAttr("w:val", table.getStyleId());
        }

        XNode tblW = tblPr.makeChild("w:tblW");
        tblW.setAttr("w:w", 0);
        tblW.setAttr("w:type", "auto");

        WordBorderStyle[] uniform = uniformTableBorders(table);
        if (uniform != null) {
            XNode tblBorders = tblPr.makeChild("w:tblBorders");
            appendBorder(tblBorders, "w:top", uniform[0]);
            appendBorder(tblBorders, "w:left", uniform[1]);
            appendBorder(tblBorders, "w:bottom", uniform[2]);
            appendBorder(tblBorders, "w:right", uniform[3]);
            appendBorder(tblBorders, "w:insideH", uniform[0]);
            appendBorder(tblBorders, "w:insideV", uniform[3]);
        }
        return tblPr;
    }

    private WordBorderStyle[] uniformTableBorders(WordTable table) {
        if (table.getRows() == null) {
            return null;
        }
        WordBorderStyle[] uniform = null;
        boolean any = false;
        for (WordTableRow row : table.getRows()) {
            if (row.getCells() == null) {
                continue;
            }
            for (WordTableCell cell : row.getCells()) {
                if (cell.isProxyCell()) {
                    continue;
                }
                any = true;
                WordCellStyle style = resolveCellStyle(cell);
                WordBorderStyle[] borders = style == null ? null : new WordBorderStyle[]{
                        style.getTopBorder(), style.getLeftBorder(),
                        style.getBottomBorder(), style.getRightBorder()};
                if (borders == null || borders[0] == null || borders[1] == null
                        || borders[2] == null || borders[3] == null) {
                    return null;
                }
                if (uniform == null) {
                    uniform = borders;
                } else if (!isSameBorders(uniform, borders)) {
                    return null;
                }
            }
        }
        return any ? uniform : null;
    }

    private boolean isSameBorders(WordBorderStyle[] a, WordBorderStyle[] b) {
        for (int i = 0; i < 4; i++) {
            WordBorderStyle x = a[i];
            WordBorderStyle y = b[i];
            if (x == null || y == null) {
                return false;
            }
            if (!Objects.equals(x.getType(), y.getType()) || x.getWeight() != y.getWeight()
                    || !Objects.equals(x.getColor(), y.getColor())) {
                return false;
            }
        }
        return true;
    }

    private WordCellStyle resolveCellStyle(WordTableCell cell) {
        String styleId = cell.getStyleId();
        if (StringHelper.isEmpty(styleId) || doc == null) {
            return null;
        }
        OfficeStyle style = doc.getStyle(styleId);
        return style instanceof WordCellStyle ? (WordCellStyle) style : null;
    }

    protected XNode buildTableRow(WordTable table, WordTableRow row, int rowIndex, boolean emitTrPr) {
        XNode trNode = XNode.make("w:tr");

        if (emitTrPr) {
            XNode trPr = trNode.makeChild("w:trPr");
            if (row.getHeight() != null) {
                XNode trHeight = trPr.makeChild("w:trHeight");
                trHeight.setAttr("w:val", pointsToTwips(row.getHeight()));
                trHeight.setAttr("w:hRule", "atLeast");
            }
        }

        if (row.getCells() != null) {
            for (int cellIndex = 0; cellIndex < row.getCells().size(); cellIndex++) {
                WordTableCell cell = row.getCells().get(cellIndex);
                if (cell.isProxyCell() && cell.getColOffset() > 0) {
                    // horizontal merge continuation: already covered by the real
                    // cell's gridSpan; emitting an extra tc would break the grid
                    continue;
                }
                trNode.appendChild(buildTableCell(table, cell, rowIndex, cellIndex));
            }
        }

        return trNode;
    }

    protected XNode buildTableCell(WordTable table, WordTableCell cell, int rowIndex, int cellIndex) {
        XNode tcNode = XNode.make("w:tc");

        if (cell.isProxyCell()) {
            // Continuation of a vertical merge: carries the merge marker plus the
            // visual style of the real (restart) cell so borders stay continuous.
            WordTableCell real = cell.getRealCell();
            XNode tcPr = tcNode.makeChild("w:tcPr");
            tcPr.makeChild("w:vMerge");
            if (real != null && real.getMergeAcross() > 0) {
                tcPr.makeChild("w:gridSpan").setAttr("w:val", real.getMergeAcross() + 1);
            }
            appendCellStylePr(real, tcPr);
            tcNode.makeChild("w:p");
            return tcNode;
        }

        XNode tcPr = tcNode.makeChild("w:tcPr");
        if (cell.getMergeAcross() > 0) {
            tcPr.makeChild("w:gridSpan").setAttr("w:val", cell.getMergeAcross() + 1);
        }
        if (cell.getMergeDown() > 0) {
            XNode vMerge = tcPr.makeChild("w:vMerge");
            vMerge.setAttr("w:val", "restart");
        }
        appendCellStylePr(cell, tcPr);

        // G1: inline cell styles (__cell_*) and named cell styles are expanded into
        // tcPr; they are never written to styles.xml.

        // Cell text may carry multiple paragraphs (getText joins them with '\n')
        String text = cell.getText();
        List<String> lines = text == null || text.isEmpty()
                ? java.util.Collections.singletonList(null)
                : java.util.Arrays.asList(text.split("\n", -1));
        for (String line : lines) {
            // note: makeChild reuses an existing same-tag child, so repeated siblings
            // must be created explicitly with XNode.make + appendChild
            XNode p = XNode.make("w:p");
            tcNode.appendChild(p);
            if (line != null && !line.isEmpty()) {
                XNode r = XNode.make("w:r");
                p.appendChild(r);
                XNode t = XNode.make("w:t");
                t.content(line);
                t.setAttr("xml:space", "preserve");
                r.appendChild(t);
            }
        }

        return tcNode;
    }

    /**
     * Expands a resolved cell style into tcPr children: tcBorders (top/left/bottom/
     * right), shd fill and vAlign. Missing style or empty fields contribute nothing.
     */
    private void appendCellStylePr(WordTableCell cell, XNode tcPr) {
        WordCellStyle style = resolveCellStyle(cell);
        if (style == null) {
            return;
        }

        boolean hasBorders = style.getTopBorder() != null || style.getLeftBorder() != null
                || style.getBottomBorder() != null || style.getRightBorder() != null;
        if (hasBorders) {
            XNode tcBorders = tcPr.makeChild("w:tcBorders");
            appendBorder(tcBorders, "w:top", style.getTopBorder());
            appendBorder(tcBorders, "w:left", style.getLeftBorder());
            appendBorder(tcBorders, "w:bottom", style.getBottomBorder());
            appendBorder(tcBorders, "w:right", style.getRightBorder());
        }

        if (!StringHelper.isEmpty(style.getBackgroundColor())) {
            XNode shd = tcPr.makeChild("w:shd");
            shd.setAttr("w:val", "clear");
            shd.setAttr("w:fill", style.getBackgroundColor());
        }

        if (style.getVerticalAlign() != null) {
            XNode vAlign = tcPr.makeChild("w:vAlign");
            vAlign.setAttr("w:val", style.getVerticalAlign().getWmlText());
        }
    }

    private void appendBorder(XNode parent, String tagName, WordBorderStyle border) {
        if (border == null) {
            return;
        }
        XNode node = parent.makeChild(tagName);
        node.setAttr("w:val", border.getType().getWmlText());
        node.setAttr("w:sz", border.getWeight() * 8);
        if (!StringHelper.isEmpty(border.getColor())) {
            node.setAttr("w:color", border.getColor());
        }
    }

    // ---- styles.xml ------------------------------------------------------

    /**
     * Builds w:styles from doc.styles (G1: inline/composed cell styles are excluded;
     * they expand into tcPr at their usage sites). Named styles carry id/name/
     * basedOn/default plus the fields the paragraph/run style models hold.
     */
    public XNode buildStylesXml(OfficeDocModel docModel) {
        XNode root = XNode.make("w:styles");
        root.setAttr("xmlns:w", NS_W);

        if (docModel.getStyles() == null) {
            return root;
        }

        for (OfficeStyle style : docModel.getStyles()) {
            XNode styleNode = buildStyleNode(style);
            if (styleNode != null) {
                root.appendChild(styleNode);
            }
        }
        return root;
    }

    private XNode buildStyleNode(OfficeStyle style) {
        String type;
        if (style instanceof WordParagraphStyle) {
            type = "paragraph";
        } else if (style instanceof WordRunStyle) {
            type = "character";
        } else if (style instanceof WordTableStyle) {
            type = "table";
        } else if (style instanceof WordListStyle) {
            type = "numbering";
        } else {
            // cell styles have no w:style type in OOXML; they are expanded inline
            return null;
        }

        XNode styleNode = XNode.make("w:style");
        styleNode.setAttr("w:type", type);
        styleNode.setAttr("w:styleId", style.getId());
        if (style.isDefault()) {
            styleNode.setAttr("w:default", "1");
        }

        String name = getStyleName(style);
        if (!StringHelper.isEmpty(name)) {
            styleNode.makeChild("w:name").setAttr("w:val", name);
        }
        if (!StringHelper.isEmpty(style.getBasedOn())) {
            styleNode.makeChild("w:basedOn").setAttr("w:val", style.getBasedOn());
        }

        if (style instanceof WordParagraphStyle) {
            WordParagraphStyle ps = (WordParagraphStyle) style;
            XNode pPr = styleNode.makeChild("w:pPr");
            if (ps.getAlign() != null) {
                pPr.makeChild("w:jc").setAttr("w:val", ps.getAlign().getWmlText());
            }
            Integer beforeTwips = pointsToTwips(ps.getSpaceBefore());
            Integer afterTwips = pointsToTwips(ps.getSpaceAfter());
            if (beforeTwips != null || afterTwips != null) {
                XNode spacing = pPr.makeChild("w:spacing");
                if (beforeTwips != null) {
                    spacing.setAttr("w:before", beforeTwips);
                }
                if (afterTwips != null) {
                    spacing.setAttr("w:after", afterTwips);
                }
            }
            Integer leftTwips = pointsToTwips(ps.getLeftIndent());
            Integer rightTwips = pointsToTwips(ps.getRightIndent());
            Integer firstLineTwips = pointsToTwips(ps.getFirstLineIndent());
            if (leftTwips != null || rightTwips != null || firstLineTwips != null) {
                XNode ind = pPr.makeChild("w:ind");
                if (leftTwips != null) {
                    ind.setAttr("w:left", leftTwips);
                }
                if (rightTwips != null) {
                    ind.setAttr("w:right", rightTwips);
                }
                if (firstLineTwips != null) {
                    ind.setAttr("w:firstLine", firstLineTwips);
                }
            }
            if (ps.getTitleLevel() != null && ps.getTitleLevel() >= 1 && ps.getTitleLevel() <= 9) {
                pPr.makeChild("w:outlineLvl").setAttr("w:val", ps.getTitleLevel() - 1);
            }
        } else if (style instanceof WordRunStyle) {
            WordRunStyle rs = (WordRunStyle) style;
            XNode rPr = XNode.make("w:rPr");
            XNode built = buildRunStyle(rs);
            if (built != null) {
                // detach during iteration mutates built.getChildren() and throws
                // ConcurrentModificationException when the style carries a font block;
                // copy the list first
                for (XNode child : new ArrayList<>(built.getChildren())) {
                    rPr.appendChild(child.detach());
                }
            }
            if (rPr.getChildCount() > 0) {
                styleNode.appendChild(rPr);
            }
        } else if (style instanceof WordListStyle) {
            WordListStyle ls = (WordListStyle) style;
            // numFmt/lvlText/start live in numbering.xml in OOXML and have no valid
            // location inside a styles.xml w:style; only the indent is representable.
            if (ls.getIndent() != null) {
                styleNode.makeChild("w:pPr")
                        .makeChild("w:ind").setAttr("w:left", pointsToTwips(ls.getIndent()));
            }
        }
        // table styles: the model carries no tblPr fields beyond align/width; the
        // generated w:style keeps id/name/basedOn/default only.

        return styleNode;
    }

    private String getStyleName(OfficeStyle style) {
        if (style instanceof WordParagraphStyle) {
            return ((WordParagraphStyle) style).getName();
        }
        if (style instanceof WordRunStyle) {
            return ((WordRunStyle) style).getName();
        }
        if (style instanceof WordCellStyle) {
            return ((WordCellStyle) style).getName();
        }
        if (style instanceof WordTableStyle) {
            return ((WordTableStyle) style).getName();
        }
        if (style instanceof WordListStyle) {
            return ((WordListStyle) style).getName();
        }
        return null;
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
