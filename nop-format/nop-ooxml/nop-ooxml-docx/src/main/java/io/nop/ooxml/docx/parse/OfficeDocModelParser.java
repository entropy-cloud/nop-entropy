/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.parse;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.gen.XplGenConfig;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.common.constants.TargetMode;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.model.WordHyperlink;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeDocTemplateModel;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficePageBreakModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeRunTemplateModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.OfficeTabModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.model.OfficeBorderStyle;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordBorderStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.WordTableStyle;
import io.nop.office.model.constants.OfficeFontUnderline;
import io.nop.office.model.constants.OfficeFontVerticalAlign;
import io.nop.office.model.constants.OfficeHorizontalAlignment;
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.office.model.constants.OfficeVerticalAlignment;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.api.source.IWithSourceCode;
import io.nop.xlang.api.source.SourceEvalPredicate;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xdef.domain.XplStdDomainHandlers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.ooxml.docx.DocxErrors.ARG_END_TAG_NAME;
import static io.nop.ooxml.docx.DocxErrors.ARG_LABEL;
import static io.nop.ooxml.docx.DocxErrors.ARG_LINK_NODE;
import static io.nop.ooxml.docx.DocxErrors.ARG_LINK_TARGET;
import static io.nop.ooxml.docx.DocxErrors.ARG_PART_PATH;
import static io.nop.ooxml.docx.DocxErrors.ARG_TAG_NAME;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_PARSE_PACKAGE_INVALID;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_PARSE_PART_MISSING;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_PARSE_XML_INVALID;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_END_NOT_MATCH;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_NO_END;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_END_NO_BEGIN;

/**
 * Parses a docx package into the typed {@link OfficeDocModel} (WI1/WI2 model):
 * eight block types (p/table/title/list/image/hyperlink/separator/pageBreak),
 * four inline run types (r/tab/br/link), per-section page setup, named styles
 * from word/styles.xml and image assets. Degraded inputs (missing parts,
 * dangling relationships, unknown elements) produce empty structures plus an
 * aggregated warn log; structurally broken XML maps to explicit DocxErrors codes.
 */
public class OfficeDocModelParser {
    private static final Logger LOG = LoggerFactory.getLogger(OfficeDocModelParser.class);

    private static final String STYLES_PATH = "word/styles.xml";

    private final WordTemplateParser templateParser = new WordTemplateParser();

    /**
     * Parse-scoped state (doc sink for assets/styles + skip diagnostics). Instances
     * are not guaranteed thread-safe; nested parses save/restore the previous context.
     */
    private ParseContext context;
    private ParseContext lastContext;

    public OfficeDocModel parseFromResource(IResource resource) {
        WordOfficePackage pkg = new WordOfficePackage();
        ParseContext oldContext = context;
        context = new ParseContext();
        try {
            try {
                // 非file:资源（jar内模板等）toFile()返回null会导致NPE，loadFromResource内部自动处理
                pkg.loadFromResource(resource);
            } catch (NopException e) {
                throw e;
            } catch (Exception e) {
                throw new NopException(ERR_DOCX_PARSE_PACKAGE_INVALID, e)
                        .param(ARG_PART_PATH, resource.getPath());
            }

            IOfficePackagePart docPart = pkg.getFile(DocxConstants.PATH_WORD_DOCUMENT);
            if (docPart == null) {
                throw new NopException(ERR_DOCX_PARSE_PART_MISSING)
                        .param(ARG_PART_PATH, DocxConstants.PATH_WORD_DOCUMENT);
            }

            XNode docNode;
            try {
                docNode = docPart.buildXml(null);
            } catch (Exception e) {
                throw new NopException(ERR_DOCX_PARSE_XML_INVALID, e)
                        .param(ARG_PART_PATH, DocxConstants.PATH_WORD_DOCUMENT);
            }

            XplGenConfig config = templateParser.getGenConfig(docNode);
            config.addImportLib(DocxConstants.LIB_DOCX_GEN);

            XLangCompileTool cp = config.newCompileTool();
            cp.getScope().setLocalValue(null, DocxConstants.VAR_OFC_PKG, pkg);

            OfficeDocModel doc = new OfficeDocModel();
            doc.setLocation(docNode.getLocation());
            applyConfig(doc.makeModel(), config, cp);
            context.doc = doc;

            parseStyles(pkg, doc);

            List<DocSection> sections = splitSections(getBodyNode(docNode));
            if (sections.isEmpty()) {
                sections.add(new DocSection(new ArrayList<>(), null));
            }

            applyDocPageSize(doc, sections.get(0).sectPr);

            int pageIndex = 0;
            for (DocSection section : sections) {
                pageIndex++;
                OfficeDocPageModel page = new OfficeDocPageModel();
                page.setLocation(docNode.getLocation());
                page.setName("page" + pageIndex);

                XNode sectionBody = wrapBlocks(section.blocks);
                page.setBody(parseBlocks(pkg, DocxConstants.PATH_WORD_DOCUMENT, sectionBody, page, config, cp));

                List<XNode> sectPrNodes = section.sectPr == null
                        ? Collections.emptyList()
                        : Collections.singletonList(section.sectPr);
                page.setHeader(parseRelatedParts(pkg, DocxConstants.PATH_WORD_DOCUMENT, sectPrNodes,
                        "w:headerReference", OfficeConstants.NS_HEADER, DocxConstants.PATH_PREFIX_HEADER, config, cp));
                page.setFooter(parseRelatedParts(pkg, DocxConstants.PATH_WORD_DOCUMENT, sectPrNodes,
                        "w:footerReference", OfficeConstants.NS_FOOTER, DocxConstants.PATH_PREFIX_FOOTER, config, cp));

                applySectionPageSetup(page, section.sectPr);
                doc.addPage(page);
            }
            return doc;
        } finally {
            if (context != null) {
                context.logSummary(LOG);
            }
            lastContext = context;
            context = oldContext;
            pkg.close();
        }
    }

    /**
     * Diagnostics (skipped node details) from the most recent {@link #parseFromResource}
     * call. Exposed for tolerance tests that assert degradation evidence.
     */
    public List<String> getLastParseSkips() {
        return lastContext == null ? Collections.emptyList() : lastContext.skips;
    }

    protected void applyConfig(OfficeDocTemplateModel model, XplGenConfig config, XLangCompileTool cp) {
        model.setLocation(config.getLocation());
        model.setDump(config.isDump());
        model.setDumpFile(config.getDumpFile());
        model.setImportLibs(config.getImportLibs());
        model.setNormalizeQuote(config.isNormalizeQuote());
        model.setDeleteAllAfterConfigTable(config.isDeleteAllAfterConfigTable());
        model.setBeforeGen(wrapAction(config.getBeforeGen(), cp));
        model.setAfterGen(wrapAction(config.getAfterGen(), cp));
    }

    protected List<OfficeBlock> parseRelatedParts(WordOfficePackage pkg, String sourcePath,
                                                  List<XNode> sectPrNodes, String refTagName,
                                                  String relType, String fallbackPrefix,
                                                  XplGenConfig config, XLangCompileTool cp) {
        Set<String> partPaths = new LinkedHashSet<>();
        OfficeRelsPart rels = pkg.getRelsForPartPath(sourcePath);
        if (rels != null) {
            for (XNode sectPr : sectPrNodes) {
                for (XNode ref : sectPr.getChildren()) {
                    if (!refTagName.equals(ref.getTagName())) {
                        continue;
                    }
                    String id = ref.attrText("r:id");
                    if (StringHelper.isEmpty(id)) {
                        continue;
                    }
                    OfficeRelationship rel = rels.getRelationship(id);
                    if (rel == null || !relType.equals(rel.getType())) {
                        continue;
                    }
                    partPaths.add(StringHelper.absolutePath(sourcePath, rel.getTarget()));
                }
            }
        }

        if (partPaths.isEmpty()) {
            for (IOfficePackagePart part : pkg.getFiles(fallbackPrefix)) {
                if (part.getPath().endsWith(".xml")) {
                    partPaths.add(part.getPath());
                }
            }
        }

        List<OfficeBlock> blocks = new ArrayList<>();
        for (String partPath : partPaths) {
            IOfficePackagePart part = pkg.getFile(partPath);
            if (part == null) {
                recordSkip("header/footer part missing: " + partPath);
                continue;
            }
            XNode xml = part.buildXml(null);
            blocks.addAll(parseBlocks(pkg, partPath, xml, null, config, cp));
        }
        return blocks;
    }

    // ------------------------------------------------------------------ blocks

    protected List<OfficeBlock> parseBlocks(WordOfficePackage pkg, String partPath, XNode container,
                                            OfficeDocPageModel page,
                                            XplGenConfig config, XLangCompileTool cp) {
        List<OfficeBlock> blocks = new ArrayList<>();
        if (container == null) {
            return blocks;
        }

        templateParser.replaceHyperLinkExprs(container);

        OfficeRelsPart rels = pkg.getRelsForPartPath(partPath);
        List<WordHyperlink> links = collectLinks(rels, container, config);
        templateParser.normalizeExprs(container, links);

        BlockTemplateParseState templateState = analyzeBlockTemplates(container, page, links, cp);
        removeConsumedTemplateMarkers(templateState);

        OfficeRelsPart oldRels = context == null ? null : context.rels;
        String oldPartPath = context == null ? null : context.partPath;
        if (context != null) {
            context.rels = rels;
            context.partPath = partPath;
        }
        try {
            BlockBuilder builder = new BlockBuilder(pkg, partPath, rels, links, config, cp, templateState);
            for (XNode child : container.getChildren()) {
                String tagName = child.getTagName();
                if ("w:p".equals(tagName)) {
                    builder.addParagraph(child);
                } else if ("w:tbl".equals(tagName)) {
                    builder.flushList();
                    builder.addTable(child);
                } else if ("w:hyperlink".equals(tagName)) {
                    builder.flushList();
                    builder.addHyperlinkBlock(child);
                } else {
                    recordSkip("unknown block element skipped: " + tagName);
                }
            }
            builder.flushList();
            blocks.addAll(builder.blocks);
        } finally {
            if (context != null) {
                context.rels = oldRels;
                context.partPath = oldPartPath;
            }
        }
        return blocks;
    }

    private int globalTableSeq = 0;

    protected class BlockBuilder {
        final WordOfficePackage pkg;
        final String partPath;
        final OfficeRelsPart rels;
        final List<WordHyperlink> links;
        final XplGenConfig config;
        final XLangCompileTool cp;
        final BlockTemplateParseState templateState;

        final List<OfficeBlock> blocks = new ArrayList<>();
        OfficeListModel pendingList;
        String pendingNumId;

        int paraIndex = 0;
        int tableIndex = 0;
        int listIndex = 0;
        int imageIndex = 0;
        int hyperlinkIndex = 0;
        int separatorIndex = 0;
        int pageBreakIndex = 0;

        BlockBuilder(WordOfficePackage pkg, String partPath, OfficeRelsPart rels,
                     List<WordHyperlink> links, XplGenConfig config, XLangCompileTool cp,
                     BlockTemplateParseState templateState) {
            this.pkg = pkg;
            this.partPath = partPath;
            this.rels = rels;
            this.links = links;
            this.config = config;
            this.cp = cp;
            this.templateState = templateState;
        }

        void addParagraph(XNode paraNode) {
            if (isPageBreakParagraph(paraNode)) {
                flushList();
                OfficePageBreakModel pageBreak = new OfficePageBreakModel();
                pageBreak.setLocation(paraNode.getLocation());
                pageBreak.setId("pageBreak" + (++pageBreakIndex));
                pageBreak.setType("pageBreak");
                blocks.add(pageBreak);
                return;
            }

            String numId = getListNumId(paraNode);
            if (numId != null) {
                addListItem(paraNode, numId);
                return;
            }

            flushList();

            if (isSeparatorParagraph(paraNode)) {
                OfficeSeparatorModel separator = new OfficeSeparatorModel();
                separator.setLocation(paraNode.getLocation());
                separator.setId("separator" + (++separatorIndex));
                separator.setType("separator");
                blocks.add(separator);
                return;
            }

            if (isImageParagraph(paraNode)) {
                OfficeImageModel image = parseImageBlock(paraNode);
                if (image != null) {
                    blocks.add(image);
                    return;
                }
            }

            BlockTemplateBinding binding = templateState.blockBindings.get(paraNode);
            Integer titleLevel = resolveTitleLevel(getPStyleId(paraNode));
            if (titleLevel != null) {
                OfficeTitleModel title = parseTitle(paraNode, links, config, cp, paraIndex++, titleLevel);
                applyTitleTemplateBinding(title, binding);
                blocks.add(title);
                return;
            }

            OfficeParagraphModel para = parseParagraph(paraNode, links, config, cp, paraIndex++);
            applyBlockTemplateBinding(para, binding);
            blocks.add(para);
        }

        void addListItem(XNode paraNode, String numId) {
            if (pendingList == null || !numId.equals(pendingNumId)) {
                flushList();
                pendingList = new OfficeListModel();
                pendingList.setLocation(paraNode.getLocation());
                pendingList.setId("list" + (++listIndex));
                pendingList.setType("list");
                pendingList.setNumId(numId);
                pendingNumId = numId;
            }

            OfficeListItemModel item = new OfficeListItemModel();
            item.setId(getParaId(paraNode, paraIndex++));
            item.setLevel(getIlvl(paraNode));

            WordParagraphStyle style = parseParagraphStyle(paraNode);
            if (style != null) {
                item.setStyle(style);
            }

            List<OfficeRun> runs = parseInlineContent(paraNode, links, config, cp, item.getId());
            if (!runs.isEmpty()) {
                item.setRuns(runs);
            }
            pendingList.addItem(item);

            BlockTemplateBinding binding = templateState.blockBindings.get(paraNode);
            if (binding != null) {
                applyListTemplateBinding(pendingList, binding);
            }
        }

        void flushList() {
            if (pendingList != null) {
                blocks.add(pendingList);
                pendingList = null;
                pendingNumId = null;
            }
        }

        void addTable(XNode tblNode) {
            WordTableParser tableParser = new WordTableParser();
            OfficeDocModel doc = currentDoc();
            if (doc != null) {
                // doc-level sequence: per-section builders restart tableIndex, which would
                // collide cell style ids (__cell_t1_r_c) across sections/headers
                final String tableTag = "t" + (++globalTableSeq);
                tableParser.styleIdPrefix(tableTag);
                tableParser.cellStyleSink(doc::addStyle);
            }
            WordTable table = tableParser.parseTable(tblNode);
            table.setLocation(tblNode.getLocation());
            table.setId("tbl" + (++tableIndex));
            applyBlockTemplateBinding(table, templateState.blockBindings.get(tblNode));
            blocks.add(table);
        }

        void addHyperlinkBlock(XNode hyperlinkNode) {
            OfficeHyperlinkModel hyperlink = new OfficeHyperlinkModel();
            hyperlink.setLocation(hyperlinkNode.getLocation());
            hyperlink.setId("hyperlink" + (++hyperlinkIndex));
            hyperlink.setType("hyperlink");
            String[] target = resolveHyperlinkTarget(hyperlinkNode, rels, partPath);
            if (target[0] != null) {
                hyperlink.setHref(target[0]);
            }
            if (target[1] != null) {
                hyperlink.setAnchor(target[1]);
            }
            List<OfficeRun> runs = parseInlineContent(hyperlinkNode, links, config, cp, hyperlink.getId());
            if (!runs.isEmpty()) {
                hyperlink.setRuns(runs);
            }
            blocks.add(hyperlink);
        }

        OfficeImageModel parseImageBlock(XNode paraNode) {
            XNode drawing = paraNode.find(node -> "w:drawing".equals(node.getTagName()));
            if (drawing == null) {
                return null;
            }

            OfficeImageModel image = new OfficeImageModel();
            image.setLocation(paraNode.getLocation());
            image.setId("image" + (++imageIndex));
            image.setType("image");

            XNode extent = drawing.find(node -> "wp:extent".equals(node.getTagName()));
            if (extent != null) {
                Double width = emuToPoints(extent.attrInt("cx"));
                Double height = emuToPoints(extent.attrInt("cy"));
                if (width != null) {
                    image.setWidth(width);
                }
                if (height != null) {
                    image.setHeight(height);
                }
            }

            XNode blip = drawing.find(node -> "a:blip".equals(node.getTagName()));
            String embedId = blip == null ? null : blip.attrText("r:embed");
            String assetName = embedId;
            if (!StringHelper.isEmpty(embedId)) {
                OfficeRelationship rel = rels == null ? null : rels.getRelationship(embedId);
                if (rel != null) {
                    assetName = StringHelper.absolutePath(partPath, rel.getTarget());
                } else {
                    recordSkip("image relationship missing: " + embedId);
                }
            } else {
                recordSkip("image blip embed id missing: " + paraNode.getLocation());
            }

            if (assetName != null) {
                image.setAssetRef(assetName);
                registerImageAsset(assetName);
            }
            return image;
        }

        void registerImageAsset(String assetName) {
            OfficeDocModel doc = currentDoc();
            if (doc == null || doc.hasAsset(assetName)) {
                return;
            }
            IOfficePackagePart part = pkg.getFile(assetName);
            if (part == null) {
                recordSkip("image media part missing: " + assetName);
                return;
            }
            byte[] bytes;
            try {
                bytes = ResourceHelper.readBytes(part.getResource());
            } catch (Exception e) {
                recordSkip("image media part unreadable: " + assetName);
                return;
            }
            OfficeImageAsset asset = new OfficeImageAsset();
            asset.setName(assetName);
            asset.setExt(StringHelper.fileExt(assetName));
            asset.setData(ByteString.of(bytes));
            doc.addAsset(asset);
        }
    }

    protected OfficeDocModel currentDoc() {
        return context == null ? null : context.doc;
    }

    protected void recordSkip(String detail) {
        if (context != null) {
            context.recordSkip(detail);
        }
    }

    // ------------------------------------------------------------------ sectioning

    protected static class DocSection {
        final List<XNode> blocks;
        final XNode sectPr;

        DocSection(List<XNode> blocks, XNode sectPr) {
            this.blocks = blocks;
            this.sectPr = sectPr;
        }
    }

    /**
     * P3: a paragraph whose pPr embeds w:sectPr ends a section; a body-level w:sectPr
     * ends the last section. Each section gets its own OfficeDocPageModel.
     */
    protected List<DocSection> splitSections(XNode body) {
        List<DocSection> sections = new ArrayList<>();
        if (body == null) {
            return sections;
        }
        List<XNode> current = new ArrayList<>();
        for (XNode child : body.getChildren()) {
            String tagName = child.getTagName();
            if ("w:sectPr".equals(tagName)) {
                sections.add(new DocSection(current, child));
                current = new ArrayList<>();
                continue;
            }
            if ("w:p".equals(tagName)) {
                XNode pPr = child.childByTag("w:pPr");
                XNode sectPr = pPr == null ? null : pPr.childByTag("w:sectPr");
                if (sectPr != null) {
                    current.add(child);
                    sections.add(new DocSection(current, sectPr));
                    current = new ArrayList<>();
                    continue;
                }
            }
            current.add(child);
        }
        if (!current.isEmpty() || sections.isEmpty()) {
            sections.add(new DocSection(current, null));
        }
        return sections;
    }

    protected XNode wrapBlocks(List<XNode> blocks) {
        XNode container = XNode.make("w:body");
        for (XNode block : blocks) {
            container.appendChild(block.detach());
        }
        return container;
    }

    protected void applyDocPageSize(OfficeDocModel doc, XNode sectPr) {
        XNode pgSz = sectPr == null ? null : sectPr.childByTag("w:pgSz");
        if (pgSz == null) {
            return;
        }
        Double width = twipsToPoints(pgSz.attrInt("w:w"));
        Double height = twipsToPoints(pgSz.attrInt("w:h"));
        if (width != null) {
            doc.setWidth(width);
        }
        if (height != null) {
            doc.setHeight(height);
        }
    }

    protected void applySectionPageSetup(OfficeDocPageModel page, XNode sectPr) {
        if (sectPr == null) {
            return;
        }
        XNode pgSz = sectPr.childByTag("w:pgSz");
        if (pgSz != null) {
            Double width = twipsToPoints(pgSz.attrInt("w:w"));
            Double height = twipsToPoints(pgSz.attrInt("w:h"));
            if (width != null) {
                page.setWidth(width);
            }
            if (height != null) {
                page.setHeight(height);
            }
            String orient = pgSz.attrText("w:orient");
            if (!StringHelper.isEmpty(orient)) {
                page.setOrientation(orient);
            }
        }

        XNode pgMar = sectPr.childByTag("w:pgMar");
        if (pgMar != null) {
            OfficePageMargins margins = new OfficePageMargins();
            margins.setLocation(pgMar.getLocation());
            Double top = twipsToPoints(pgMar.attrInt("w:top"));
            Double right = twipsToPoints(pgMar.attrInt("w:right"));
            Double bottom = twipsToPoints(pgMar.attrInt("w:bottom"));
            Double left = twipsToPoints(pgMar.attrInt("w:left"));
            Double header = twipsToPoints(pgMar.attrInt("w:header"));
            Double footer = twipsToPoints(pgMar.attrInt("w:footer"));
            Double gutter = twipsToPoints(pgMar.attrInt("w:gutter"));
            if (top != null) margins.setTop(top);
            if (right != null) margins.setRight(right);
            if (bottom != null) margins.setBottom(bottom);
            if (left != null) margins.setLeft(left);
            if (header != null) margins.setHeader(header);
            if (footer != null) margins.setFooter(footer);
            if (gutter != null) margins.setGutter(gutter);
            page.setMargins(margins);
        }
    }

    // ------------------------------------------------------------------ paragraphs & runs

    protected OfficeParagraphModel parseParagraph(XNode paraNode, List<WordHyperlink> links,
                                                  XplGenConfig config, XLangCompileTool cp,
                                                  int paraIndex) {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setLocation(paraNode.getLocation());
        para.setId(getParaId(paraNode, paraIndex));
        // P1: type is the xdef block dispatch attribute and is always the tag name "p";
        // the pStyle reference is carried solely by paragraph.style.id.
        para.setType("p");

        WordParagraphStyle style = parseParagraphStyle(paraNode);
        if (style != null) {
            para.setStyle(style);
        }

        List<OfficeRun> runs = parseInlineContent(paraNode, links, config, cp, para.getId());
        if (!runs.isEmpty()) {
            para.setRuns(runs);
        }
        return para;
    }

    protected OfficeTitleModel parseTitle(XNode paraNode, List<WordHyperlink> links,
                                          XplGenConfig config, XLangCompileTool cp,
                                          int paraIndex, Integer titleLevel) {
        OfficeTitleModel title = new OfficeTitleModel();
        title.setLocation(paraNode.getLocation());
        title.setId(getParaId(paraNode, paraIndex));
        title.setType("title");

        WordParagraphStyle style = parseParagraphStyle(paraNode);
        if (style != null) {
            style.setTitleLevel(titleLevel);
            title.setStyle(style);
        }

        List<OfficeRun> runs = parseInlineContent(paraNode, links, config, cp, title.getId());
        if (!runs.isEmpty()) {
            title.setRuns(runs);
        }
        return title;
    }

    /**
     * Parses the inline content of a paragraph/hyperlink container into the four
     * run types (r/tab/br/link). Template hyperlinks (expr/xpl markers) keep the
     * legacy run-level template semantics.
     */
    protected List<OfficeRun> parseInlineContent(XNode container, List<WordHyperlink> links,
                                                 XplGenConfig config, XLangCompileTool cp,
                                                 String idBase) {
        return parseInlineContent(container, links, config, cp, idBase, 0);
    }

    protected List<OfficeRun> parseInlineContent(XNode container, List<WordHyperlink> links,
                                                 XplGenConfig config, XLangCompileTool cp,
                                                 String idBase, int startIndex) {
        List<OfficeRun> runs = new ArrayList<>();
        if (container == null) {
            return runs;
        }
        int runIndex = startIndex;
        for (XNode child : container.getChildren()) {
            String tagName = child.getTagName();
            if ("w:r".equals(tagName)) {
                if (child.find(node -> "w:drawing".equals(node.getTagName())) != null) {
                    recordSkip("inline drawing outside a standalone image paragraph skipped: " + child.getLocation());
                    continue;
                }
                runs.add(parseRun(child, null, config, cp, idBase, runIndex++));
            } else if ("w:tab".equals(tagName)) {
                runs.add(parseTab(child, idBase, runIndex++));
            } else if ("w:br".equals(tagName)) {
                runs.add(parseBreak(child, idBase, runIndex++));
            } else if ("w:hyperlink".equals(tagName)) {
                int before = runs.size();
                runs.addAll(parseHyperlinkRuns(child, links, config, cp, idBase, runIndex));
                runIndex += runs.size() - before;
            } else if (!isStyleNode(tagName)) {
                recordSkip("unknown inline element skipped: " + tagName);
            }
        }
        return runs;
    }

    protected boolean isStyleNode(String tagName) {
        return "w:pPr".equals(tagName) || "w:rPr".equals(tagName);
    }

    protected OfficeTabModel parseTab(XNode node, String idBase, int runIndex) {
        OfficeTabModel tab = new OfficeTabModel();
        tab.setLocation(node.getLocation());
        tab.setId((idBase == null ? "p" : idBase) + "-r" + runIndex);
        tab.setType("tab");
        return tab;
    }

    protected OfficeBreakModel parseBreak(XNode node, String idBase, int runIndex) {
        OfficeBreakModel br = new OfficeBreakModel();
        br.setLocation(node.getLocation());
        br.setId((idBase == null ? "p" : idBase) + "-r" + runIndex);
        br.setType("br");
        String brType = node.attrText("w:type");
        if (!StringHelper.isEmpty(brType)) {
            br.setBrType(brType);
        }
        return br;
    }

    /**
     * Handles one w:hyperlink inside inline content. Template links (expr/tpl/xpl
     * complete tag) keep the legacy run semantics; plain external/anchor links
     * become OfficeLinkModel with nested runs; stray xpl begin/end markers flatten
     * their runs (legacy behavior). Returns the produced runs (possibly empty).
     */
    protected List<OfficeRun> parseHyperlinkRuns(XNode hyperlinkNode, List<WordHyperlink> links,
                                                 XplGenConfig config, XLangCompileTool cp,
                                                 String idBase, int runIndex) {
        return doParseHyperlinkRuns(hyperlinkNode, links, config, cp, idBase, runIndex, false);
    }

    private List<OfficeRun> doParseHyperlinkRuns(XNode hyperlinkNode, List<WordHyperlink> links,
                                                 XplGenConfig config, XLangCompileTool cp,
                                                 String idBase, int runIndex, boolean flatten) {
        WordHyperlink link = findLink(links, hyperlinkNode);
        if (!flatten && link != null && link.getLinkType() != WordHyperlink.LinkType.link
                && !link.isXplBegin() && !link.isXplEnd()) {
            List<OfficeRun> runs = new ArrayList<>(1);
            runs.add(parseRun(link.getLinkNode().childByTag("w:r"), link, config, cp, idBase, runIndex));
            return runs;
        }

        // plain (non-template) links become OfficeLinkModel with nested runs
        if (!flatten && (link == null || link.getLinkType() == WordHyperlink.LinkType.link)) {
            String[] target = resolveHyperlinkTarget(hyperlinkNode, currentRels(), currentPartPath());
            OfficeLinkModel linkModel = new OfficeLinkModel();
            linkModel.setLocation(hyperlinkNode.getLocation());
            linkModel.setId((idBase == null ? "p" : idBase) + "-r" + runIndex);
            linkModel.setType("link");
            if (target[0] != null) {
                linkModel.setHref(target[0]);
            }
            if (target[1] != null) {
                linkModel.setAnchor(target[1]);
            }
            List<OfficeRun> nested = parseInlineContent(hyperlinkNode, links, config, cp, linkModel.getId());
            if (!nested.isEmpty()) {
                linkModel.setRuns(nested);
            }
            List<OfficeRun> runs = new ArrayList<>(1);
            runs.add(linkModel);
            return runs;
        }

        // xpl begin/end markers not consumed as block templates: legacy flattening
        return parseInlineContent(hyperlinkNode, links, config, cp, idBase, runIndex);
    }

    protected OfficeRun parseRun(XNode runNode, WordHyperlink link,
                                 XplGenConfig config, XLangCompileTool cp,
                                 String paraId, int runIndex) {
        OfficeRunModel run = new OfficeRunModel();
        SourceLocation loc = runNode == null ? null : runNode.getLocation();
        run.setLocation(loc);
        run.setId((paraId == null ? "p" : paraId) + "-r" + runIndex);
        run.setType("r");

        if (runNode != null) {
            // run-level dispatch: a run that only carries a tab or a break becomes
            // the corresponding inline node (corpus form <w:r><w:tab/></w:r>)
            XNode br = runNode.childByTag("w:br");
            if (br != null && link == null
                    && runNode.find(node -> "w:t".equals(node.getTagName())) == null) {
                OfficeBreakModel brModel = parseBreak(br, paraId, runIndex);
                brModel.setId((paraId == null ? "p" : paraId) + "-r" + runIndex);
                return brModel;
            }
            XNode tab = runNode.childByTag("w:tab");
            if (tab != null && runNode.find(node -> "w:t".equals(node.getTagName())) == null
                    && link == null) {
                OfficeTabModel tabModel = parseTab(tab, paraId, runIndex);
                tabModel.setId((paraId == null ? "p" : paraId) + "-r" + runIndex);
                return tabModel;
            }

            // text run that also carries w:br/w:tab: the text is kept; the inline break/tab
            // cannot be dispatched as a separate node within one run and is named in skips
            if (br != null) {
                recordSkip("w:br inside text run not dispatched: " + runNode.getLocation());
            }
            if (tab != null) {
                recordSkip("w:tab inside text run not dispatched: " + runNode.getLocation());
            }

            WordRunStyle style = parseRunStyle(runNode);
            if (style != null) {
                run.setStyle(style);
            }
            run.setT(getRunText(runNode));
        }

        if (link != null) {
            applyHyperlinkModel(run.makeModel(), link, config, cp, loc);
        }
        return run;
    }

    protected void applyHyperlinkModel(OfficeRunTemplateModel model, WordHyperlink link,
                                       XplGenConfig config, XLangCompileTool cp,
                                       SourceLocation loc) {
        switch (link.getLinkType()) {
            case expr:
                model.setValueExpr(compileValueAction(loc, normalizeExprSource(link, config), cp));
                break;
            case tpl_expr:
                model.setTemplateExpr(compileTemplateAction(loc, normalizeTplSource(link, config), cp));
                break;
            case xpl:
                model.setTemplateExpr(compileTagTemplateAction(link.getSourceNode(), cp));
                break;
            default:
                break;
        }
    }

    protected IEvalAction compileValueAction(SourceLocation loc, String source, XLangCompileTool cp) {
        if (StringHelper.isBlank(source)) {
            return null;
        }
        IEvalAction action = XplStdDomainHandlers.ExprType.INSTANCE.parseProp(null, loc, "valueExpr", source, cp);
        if (action instanceof ExprEvalAction) {
            return EvalCode.addSource((ExprEvalAction) action, source);
        }
        return action;
    }

    protected IEvalPredicate compilePredicateAction(SourceLocation loc, String source, XLangCompileTool cp) {
        if (StringHelper.isBlank(source)) {
            return null;
        }
        IEvalPredicate predicate = (IEvalPredicate) XplStdDomainHandlers.XPL_PREDICATE_TYPE
                .parseProp(null, loc, "testExpr", source, cp);
        if (predicate instanceof IWithSourceCode) {
            return predicate;
        }
        return new SourceEvalPredicate(source, predicate);
    }

    protected IEvalAction compileTemplateAction(SourceLocation loc, String source, XLangCompileTool cp) {
        if (StringHelper.isBlank(source)) {
            return null;
        }
        XLangOutputMode oldMode = cp.getOutputMode();
        cp.outputMode(XLangOutputMode.none);
        ExprEvalAction action;
        try {
            action = cp.compileTemplateExpr(loc, source);
        } finally {
            cp.outputMode(oldMode);
        }
        return EvalCode.addSource(action, "tpl`" + source + "`");
    }

    protected IEvalAction compileTagTemplateAction(XNode sourceNode, XLangCompileTool cp) {
        if (sourceNode == null) {
            return null;
        }
        XNode wrapper = XNode.make("c:unit");
        wrapper.appendChild(sourceNode.cloneInstance());
        return cp.compileTagBodyWithSource(wrapper, XLangOutputMode.none);
    }

    protected IEvalAction wrapAction(XNode node, XLangCompileTool cp) {
        if (node == null) {
            return null;
        }
        return cp.compileTagBodyWithSource(node.cloneInstance(), io.nop.xlang.ast.XLangOutputMode.none);
    }

    protected String normalizeExprSource(WordHyperlink link, XplGenConfig config) {
        String source = link.getLinkTarget();
        if (StringHelper.startsWithNamespace(source, WordHyperlink.LinkType.expr.name())) {
            source = WordHyperlink.decodeUrl(source.substring(WordHyperlink.LinkType.expr.name().length() + 1));
        } else {
            source = link.getLinkLabel();
        }
        if (StringHelper.isEmpty(source)) {
            source = link.getLinkLabel();
        }
        if (config.isNormalizeQuote()) {
            source = StringHelper.normalizeChineseQuote(source);
        }
        return source;
    }

    protected String normalizeTplSource(WordHyperlink link, XplGenConfig config) {
        String source = link.getSourceNode() == null ? null : getRunText(link.getSourceNode());
        if (StringHelper.isEmpty(source)) {
            String target = link.getLinkTarget();
            if (StringHelper.startsWithNamespace(target, WordHyperlink.LinkType.xpl.name())) {
                source = WordHyperlink.decodeUrl(target.substring(WordHyperlink.LinkType.xpl.name().length() + 1));
            }
        }
        if (config.isNormalizeQuote()) {
            source = StringHelper.normalizeChineseQuote(source);
        }
        return source;
    }

    protected WordHyperlink findLink(List<WordHyperlink> links, XNode node) {
        for (WordHyperlink link : links) {
            if (link.getLinkNode() == node) {
                return link;
            }
        }
        return null;
    }

    protected List<WordHyperlink> collectLinks(OfficeRelsPart rels, XNode container, XplGenConfig config) {
        List<WordHyperlink> links = new ArrayList<>();
        container.findAll(WordXmlHelper::isHyperlink).forEach(node -> {
            if (!WordXmlHelper.isHyperlink(node)) {
                return;
            }
            if (rels == null && StringHelper.isEmpty(node.attrText("url"))) {
                return;
            }

            WordHyperlink link = WordHyperlink.build(rels, node, config);
            if (link == null || link.getLinkType() == WordHyperlink.LinkType.link) {
                return;
            }

            links.add(link);
            if (rels != null && !StringHelper.isEmpty(link.getId())) {
                rels.removeRelationshipById(link.getId());
            }
        });
        return links;
    }

    protected String getParaId(XNode paraNode, int paraIndex) {
        String id = paraNode.attrText("w14:paraId");
        if (StringHelper.isEmpty(id)) {
            id = paraNode.attrText("w:paraId");
        }
        return StringHelper.isEmpty(id) ? "p" + paraIndex : id;
    }

    protected String getRunText(XNode node) {
        if (node == null) {
            return null;
        }
        return WordXmlHelper.getText(node, false, null);
    }

    // ------------------------------------------------------------------ block classification

    protected String getPStyleId(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr == null) {
            return null;
        }
        String pStyleId = ConvertHelper.toString(pPr.childAttr("w:pStyle", "w:val"));
        return StringHelper.isEmpty(pStyleId) ? null : pStyleId;
    }

    /**
     * P1: Heading1-9 / Title map to OfficeTitleModel with titleLevel 1-9 (Title -> 1).
     */
    protected Integer resolveTitleLevel(String pStyleId) {
        if (StringHelper.isEmpty(pStyleId)) {
            return null;
        }
        if ("Title".equals(pStyleId)) {
            return 1;
        }
        if (pStyleId.startsWith("Heading")) {
            String suffix = pStyleId.substring("Heading".length());
            Integer level = ConvertHelper.toInt(suffix, null);
            if (level != null && level >= 1 && level <= 9) {
                return level;
            }
        }
        return null;
    }

    /**
     * P2: the numPr numId groups consecutive paragraphs into one OfficeListModel.
     */
    protected String getListNumId(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr == null) {
            return null;
        }
        XNode numPr = pPr.childByTag("w:numPr");
        if (numPr == null) {
            return null;
        }
        String numId = ConvertHelper.toString(numPr.childAttr("w:numId", "w:val"));
        return StringHelper.isEmpty(numId) ? null : numId;
    }

    protected int getIlvl(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        XNode numPr = pPr == null ? null : pPr.childByTag("w:numPr");
        if (numPr == null) {
            return 0;
        }
        Integer ilvl = ConvertHelper.toInt(numPr.childAttr("w:ilvl", "w:val"), null);
        return ilvl == null ? 0 : ilvl;
    }

    /**
     * A paragraph whose only content is a single page-type break run becomes an
     * OfficePageBreakModel block.
     */
    protected boolean isPageBreakParagraph(XNode paraNode) {
        boolean hasPageBreak = false;
        int contentCount = 0;
        for (XNode child : paraNode.getChildren()) {
            String tagName = child.getTagName();
            if ("w:pPr".equals(tagName)) {
                continue;
            }
            contentCount++;
            if ("w:r".equals(tagName)) {
                XNode br = child.childByTag("w:br");
                if (br != null && "page".equals(br.attrText("w:type"))
                        && child.find(node -> "w:t".equals(node.getTagName())) == null) {
                    hasPageBreak = true;
                }
            }
        }
        return hasPageBreak && contentCount == 1;
    }

    /**
     * P6/separator: a paragraph with pPr/pBdr and no run text is a separator block.
     */
    protected boolean isSeparatorParagraph(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr == null || pPr.childByTag("w:pBdr") == null) {
            return false;
        }
        return StringHelper.isEmpty(getRunText(paraNode));
    }

    /**
     * A standalone image paragraph: exactly one run child whose content is a
     * w:drawing with no text.
     */
    protected boolean isImageParagraph(XNode paraNode) {
        XNode onlyRun = null;
        int contentCount = 0;
        for (XNode child : paraNode.getChildren()) {
            String tagName = child.getTagName();
            if ("w:pPr".equals(tagName)) {
                continue;
            }
            contentCount++;
            if ("w:r".equals(tagName)) {
                onlyRun = child;
            }
        }
        if (contentCount != 1 || onlyRun == null) {
            return false;
        }
        return onlyRun.find(node -> "w:drawing".equals(node.getTagName())) != null
                && StringHelper.isEmpty(getRunText(onlyRun));
    }

    // ------------------------------------------------------------------ template binding

    protected BlockTemplateParseState analyzeBlockTemplates(XNode container, OfficeDocPageModel page,
                                                            List<WordHyperlink> links, XLangCompileTool cp) {
        BlockTemplateParseState state = new BlockTemplateParseState();
        XNode firstBlock = page == null ? null : getFirstBlockNode(container);
        XNode lastBlock = page == null ? null : getLastBlockNode(container);

        for (BlockTemplatePair pair : collectLinkPairs(links)) {
            XNode beginBlock = getTopLevelBlockNode(container, pair.begin.getLinkNode());
            XNode endBlock = getTopLevelBlockNode(container, pair.end.getLinkNode());
            if (beginBlock == null || endBlock == null) {
                continue;
            }

            if (beginBlock == endBlock) {
                BlockTemplateBinding binding = state.blockBindings.computeIfAbsent(beginBlock,
                        key -> new BlockTemplateBinding());
                if (applyTemplateSemantics(binding, pair.begin, cp)) {
                    state.consumedMarkerNodes.add(pair.begin.getLinkNode());
                    state.consumedMarkerNodes.add(pair.end.getLinkNode());
                }
                continue;
            }

            if (page != null && beginBlock == firstBlock && endBlock == lastBlock) {
                if (applyTemplateSemantics(state.pageBinding, pair.begin, cp)) {
                    state.consumedMarkerNodes.add(pair.begin.getLinkNode());
                    state.consumedMarkerNodes.add(pair.end.getLinkNode());
                }
            }
        }

        applyPageTemplateBinding(page, state.pageBinding);
        return state;
    }

    protected boolean applyTemplateSemantics(BlockTemplateBinding binding, WordHyperlink begin,
                                             XLangCompileTool cp) {
        XNode sourceNode = begin.getSourceNode();
        if (sourceNode == null) {
            return false;
        }

        String tagName = sourceNode.getTagName();
        if ("c:if".equals(tagName)) {
            String visibleExpr = getTagExprAttr(sourceNode, "visible");
            if (!StringHelper.isBlank(visibleExpr)) {
                binding.visibleExpr = compileValueAction(sourceNode.getLocation(), visibleExpr, cp);
                return true;
            }

            String testExpr = getTagExprAttr(sourceNode, "test");
            if (!StringHelper.isBlank(testExpr)) {
                IEvalPredicate predicate = compilePredicateAction(sourceNode.getLocation(), testExpr, cp);
                if (binding.testExpr == null) {
                    binding.testExpr = predicate;
                } else {
                    binding.testExpr = binding.testExpr.and(predicate);
                }
                return true;
            }
            return false;
        }

        if ("c:for".equals(tagName)) {
            String itemsExpr = getTagExprAttr(sourceNode, "items");
            if (isSimplePropPath(itemsExpr)) {
                binding.loopItemsName = itemsExpr;
            } else if (!StringHelper.isBlank(itemsExpr)) {
                binding.beginLoop = compileValueAction(sourceNode.getLocation(), itemsExpr, cp);
            } else {
                return false;
            }

            binding.loopVarName = sourceNode.attrText("var");
            binding.loopIndexName = sourceNode.attrText("index");
            return true;
        }

        return false;
    }

    protected void applyPageTemplateBinding(OfficeDocPageModel page, BlockTemplateBinding binding) {
        if (page == null || binding == null || binding.isEmpty()) {
            return;
        }

        if (binding.testExpr != null) {
            page.makeModel().setTestExpr(binding.testExpr);
        }
        if (binding.beginLoop != null) {
            page.makeModel().setBeginLoop(binding.beginLoop);
        }
        if (!StringHelper.isEmpty(binding.loopItemsName)) {
            page.makeModel().setLoopItemsName(binding.loopItemsName);
        }
        if (!StringHelper.isEmpty(binding.loopVarName)) {
            page.makeModel().setLoopVarName(binding.loopVarName);
        }
        if (!StringHelper.isEmpty(binding.loopIndexName)) {
            page.makeModel().setLoopIndexName(binding.loopIndexName);
        }
    }

    protected void applyBlockTemplateBinding(OfficeBlock block, BlockTemplateBinding binding) {
        if (block == null || binding == null || binding.isEmpty()) {
            return;
        }

        if (block instanceof OfficeParagraphModel) {
            OfficeParagraphModel para = (OfficeParagraphModel) block;
            if (binding.testExpr != null) {
                para.makeModel().setTestExpr(binding.testExpr);
            }
            if (binding.visibleExpr != null) {
                para.makeModel().setVisibleExpr(binding.visibleExpr);
            }
            return;
        }

        if (block instanceof WordTable && binding.testExpr != null) {
            ((WordTable) block).makeModel().setTestExpr(binding.testExpr);
        }
    }

    /**
     * P2: template bindings on paragraphs aggregated into a list migrate to the
     * OfficeListTemplateModel of the enclosing OfficeListModel.
     */
    protected void applyListTemplateBinding(OfficeListModel list, BlockTemplateBinding binding) {
        if (list == null || binding == null || binding.isEmpty()) {
            return;
        }

        if (binding.testExpr != null) {
            if (list.getModel() != null && list.getModel().getTestExpr() != null) {
                list.makeModel().setTestExpr(list.getModel().getTestExpr().and(binding.testExpr));
            } else {
                list.makeModel().setTestExpr(binding.testExpr);
            }
        }
        // OfficeListTemplateModel only carries testExpr/visibleExpr; c:for loop fields
        // have no counterpart on the list model and stay on the aggregated items.
        if (binding.visibleExpr != null && list.makeModel().getVisibleExpr() == null) {
            list.makeModel().setVisibleExpr(binding.visibleExpr);
        }
    }

    protected void applyTitleTemplateBinding(OfficeTitleModel title, BlockTemplateBinding binding) {
        if (title == null || binding == null || binding.isEmpty()) {
            return;
        }
        if (binding.testExpr != null) {
            title.makeModel().setTestExpr(binding.testExpr);
        }
        if (binding.visibleExpr != null) {
            title.makeModel().setVisibleExpr(binding.visibleExpr);
        }
    }

    protected void removeConsumedTemplateMarkers(BlockTemplateParseState state) {
        for (XNode markerNode : state.consumedMarkerNodes) {
            markerNode.detach();
        }
    }

    protected void ensureMatched(WordHyperlink begin, WordHyperlink end) {
        if (!java.util.Objects.equals(begin.getTagName(), end.getTagName())) {
            throw new NopException(ERR_DOCX_XPL_BEGIN_END_NOT_MATCH).param(ARG_TAG_NAME, begin.getTagName())
                    .param(ARG_END_TAG_NAME, end.getTagName()).param(ARG_LABEL, end.getLinkLabel())
                    .param(ARG_LINK_TARGET, end.getLinkTarget());
        }
    }

    protected List<BlockTemplatePair> collectLinkPairs(List<WordHyperlink> links) {
        List<BlockTemplatePair> pairs = new ArrayList<>();
        List<WordHyperlink> stack = new ArrayList<>();
        // Preserve the original traversal order from collectLinks so pairing matches
        // WordTemplateParser.normalizeLinks and remains compatible with existing templates.
        for (WordHyperlink link : links) {
            if (link.isXplBegin()) {
                stack.add(link);
            } else if (link.isXplEnd()) {
                if (stack.isEmpty()) {
                    throw new NopException(ERR_DOCX_XPL_END_NO_BEGIN).param(ARG_TAG_NAME, link.getTagName())
                            .param(ARG_LABEL, link.getLinkLabel()).param(ARG_LINK_TARGET, link.getLinkTarget());
                }
                WordHyperlink begin = stack.remove(stack.size() - 1);
                ensureMatched(begin, link);
                pairs.add(new BlockTemplatePair(begin, link));
            }
        }

        if (!stack.isEmpty()) {
            WordHyperlink link = stack.get(0);
            throw new NopException(ERR_DOCX_XPL_BEGIN_NO_END).param(ARG_LABEL, link.getLinkLabel())
                    .param(ARG_TAG_NAME, link.getTagName()).param(ARG_LINK_TARGET, link.getLinkTarget())
                    .param(ARG_LINK_NODE, link.getLinkNode());
        }
        return pairs;
    }

    protected XNode getTopLevelBlockNode(XNode container, XNode node) {
        XNode current = node;
        while (current != null) {
            if (current.getParent() == container) {
                String tagName = current.getTagName();
                if ("w:p".equals(tagName) || "w:tbl".equals(tagName)) {
                    return current;
                }
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    protected XNode getFirstBlockNode(XNode container) {
        for (XNode child : container.getChildren()) {
            String tagName = child.getTagName();
            if ("w:p".equals(tagName) || "w:tbl".equals(tagName)) {
                return child;
            }
        }
        return null;
    }

    protected XNode getLastBlockNode(XNode container) {
        List<XNode> children = container.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            XNode child = children.get(i);
            String tagName = child.getTagName();
            if ("w:p".equals(tagName) || "w:tbl".equals(tagName)) {
                return child;
            }
        }
        return null;
    }

    protected String getTagExprAttr(XNode node, String attrName) {
        return unwrapExpr(node.attrText(attrName));
    }

    protected String unwrapExpr(String source) {
        source = StringHelper.strip(source);
        if (StringHelper.isEmpty(source)) {
            return source;
        }
        if ((source.startsWith("${") || source.startsWith("#{")) && source.endsWith("}")) {
            return source.substring(2, source.length() - 1);
        }
        return source;
    }

    protected boolean isSimplePropPath(String source) {
        if (StringHelper.isBlank(source)) {
            return false;
        }
        return source.matches("[A-Za-z_][\\w]*(\\.[A-Za-z_][\\w]*)*");
    }

    // ------------------------------------------------------------------ styles

    protected WordParagraphStyle parseParagraphStyle(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr == null) {
            return null;
        }
        return parseParagraphStyleFromPPr(pPr, null);
    }

    protected WordParagraphStyle parseParagraphStyleFromPPr(XNode pPr, String styleId) {
        WordParagraphStyle style = new WordParagraphStyle();
        style.setLocation(pPr.getLocation());
        if (!StringHelper.isEmpty(styleId)) {
            style.setId(styleId);
        } else {
            String ref = ConvertHelper.toString(pPr.childAttr("w:pStyle", "w:val"));
            if (!StringHelper.isEmpty(ref)) {
                style.setId(ref);
            }
        }

        XNode jc = pPr.childByTag("w:jc");
        if (jc != null) {
            OfficeHorizontalAlignment align = OfficeHorizontalAlignment.fromWmlText(jc.attrText("w:val"));
            if (align != null) {
                style.setAlign(align);
            }
        }

        XNode spacing = pPr.childByTag("w:spacing");
        if (spacing != null) {
            style.setSpaceBefore(twipsToPoints(spacing.attrInt("w:before")));
            style.setSpaceAfter(twipsToPoints(spacing.attrInt("w:after")));
        }

        if (!isEmptyParagraphStyle(style)) {
            return style;
        }
        return null;
    }

    protected WordRunStyle parseRunStyle(XNode runNode) {
        XNode rPr = runNode.childByTag("w:rPr");
        if (rPr == null) {
            return null;
        }

        WordRunStyle style = new WordRunStyle();
        style.setLocation(rPr.getLocation());
        String styleId = ConvertHelper.toString(rPr.childAttr("w:rStyle", "w:val"));
        if (!StringHelper.isEmpty(styleId)) {
            style.setId(styleId);
        }

        OfficeFont font = parseFontFromRPr(rPr);
        if (font != null) {
            style.setFont(font);
        }

        XNode highlight = rPr.childByTag("w:highlight");
        if (highlight != null) {
            String val = highlight.attrText("w:val");
            if (!StringHelper.isEmpty(val)) {
                style.setHighlightColor(val);
            }
        }

        return isEmptyRunStyle(style) ? null : style;
    }

    /**
     * Maps the run properties of an rPr node onto OfficeFont. Used both for inline
     * run styles and for named styles defined in styles.xml.
     */
    protected OfficeFont parseFontFromRPr(XNode rPr) {
        if (rPr == null) {
            return null;
        }

        OfficeFont font = new OfficeFont();
        font.setLocation(rPr.getLocation());
        boolean hasFont = false;

        if (rPr.hasChild("w:b")) {
            font.setBold(true);
            hasFont = true;
        }
        if (rPr.hasChild("w:i")) {
            font.setItalic(true);
            hasFont = true;
        }
        if (rPr.hasChild("w:strike")) {
            font.setStrikeout(true);
            hasFont = true;
        }

        XNode underline = rPr.childByTag("w:u");
        if (underline != null) {
            String underlineVal = underline.attrText("w:val");
            OfficeFontUnderline underlineStyle = StringHelper.isEmpty(underlineVal)
                    ? OfficeFontUnderline.SINGLE
                    : OfficeFontUnderline.fromWmlText(underlineVal);
            if (underlineStyle == null) {
                underlineStyle = OfficeFontUnderline.SINGLE;
            }
            font.setUnderlineStyle(underlineStyle);
            hasFont = true;
        }

        XNode vertAlign = rPr.childByTag("w:vertAlign");
        if (vertAlign != null) {
            OfficeFontVerticalAlign va = OfficeFontVerticalAlign.fromText(vertAlign.attrText("w:val"));
            if (va != null) {
                font.setVerticalAlign(va);
                hasFont = true;
            }
        }

        XNode sz = rPr.childByTag("w:sz");
        if (sz != null) {
            Integer value = sz.attrInt("w:val");
            if (value != null) {
                // w:sz is in half-points
                font.setFontSize(value / 2.0f);
                hasFont = true;
            }
        }

        XNode color = rPr.childByTag("w:color");
        if (color != null) {
            String val = color.attrText("w:val");
            if (!StringHelper.isEmpty(val) && !"auto".equalsIgnoreCase(val)) {
                font.setFontColor(val);
                hasFont = true;
            }
        }

        XNode rFonts = rPr.childByTag("w:rFonts");
        if (rFonts != null) {
            String fontName = rFonts.attrText("w:eastAsia");
            if (StringHelper.isEmpty(fontName)) {
                fontName = rFonts.attrText("w:ascii");
            }
            if (!StringHelper.isEmpty(fontName)) {
                font.setFontName(fontName);
                hasFont = true;
            }
        }

        return hasFont ? font : null;
    }

    // ------------------------------------------------------------------ styles.xml (P5)

    /**
     * P5: parse word/styles.xml directly into the doc.styles container (not via the
     * legacy WordStylesPart, whose field set cannot carry the five style kinds).
     */
    protected void parseStyles(WordOfficePackage pkg, OfficeDocModel doc) {
        IOfficePackagePart part = pkg.getFile(STYLES_PATH);
        if (part == null) {
            recordSkip("styles part missing: " + STYLES_PATH);
            return;
        }
        XNode root;
        try {
            root = part.buildXml(null);
        } catch (Exception e) {
            recordSkip("styles part xml invalid: " + STYLES_PATH);
            return;
        }

        for (XNode styleNode : root.getChildren()) {
            if (!"w:style".equals(styleNode.getTagName())) {
                continue;
            }
            String type = styleNode.attrText("w:type");
            String id = styleNode.attrText("w:styleId");
            if (StringHelper.isEmpty(id)) {
                recordSkip("style without styleId skipped: " + styleNode.getLocation());
                continue;
            }
            OfficeStyle style = parseStyleDef(styleNode, type, id);
            if (style != null) {
                doc.addStyle(style);
            }
        }
    }

    protected OfficeStyle parseStyleDef(XNode styleNode, String type, String id) {
        if ("paragraph".equals(type)) {
            return parseParagraphStyleDef(styleNode, id);
        }
        if ("character".equals(type)) {
            return parseRunStyleDef(styleNode, id);
        }
        if ("table".equals(type)) {
            return parseTableStyleDef(styleNode, id);
        }
        if ("numbering".equals(type)) {
            return parseListStyleDef(styleNode, id);
        }
        recordSkip("unsupported style type skipped: " + type);
        return null;
    }

    protected void applyCommonStyleAttrs(XNode styleNode, OfficeStyle style) {
        String name = ConvertHelper.toString(styleNode.childAttr("w:name", "w:val"));
        if (!StringHelper.isEmpty(name)) {
            setName(style, name);
        }
        String basedOn = ConvertHelper.toString(styleNode.childAttr("w:basedOn", "w:val"));
        if (!StringHelper.isEmpty(basedOn)) {
            setBasedOn(style, basedOn);
        }
        String defaultVal = styleNode.attrText("w:default");
        if ("1".equals(defaultVal) || "true".equalsIgnoreCase(defaultVal)) {
            setDefaultFlag(style, true);
        }
    }

    private void setName(OfficeStyle style, String name) {
        if (style instanceof WordParagraphStyle) {
            ((WordParagraphStyle) style).setName(name);
        } else if (style instanceof WordRunStyle) {
            ((WordRunStyle) style).setName(name);
        } else if (style instanceof WordCellStyle) {
            ((WordCellStyle) style).setName(name);
        } else if (style instanceof WordTableStyle) {
            ((WordTableStyle) style).setName(name);
        } else if (style instanceof WordListStyle) {
            ((WordListStyle) style).setName(name);
        }
    }

    private void setBasedOn(OfficeStyle style, String basedOn) {
        if (style instanceof WordParagraphStyle) {
            ((WordParagraphStyle) style).setBasedOn(basedOn);
        } else if (style instanceof WordRunStyle) {
            ((WordRunStyle) style).setBasedOn(basedOn);
        } else if (style instanceof WordCellStyle) {
            ((WordCellStyle) style).setBasedOn(basedOn);
        } else if (style instanceof WordTableStyle) {
            ((WordTableStyle) style).setBasedOn(basedOn);
        } else if (style instanceof WordListStyle) {
            ((WordListStyle) style).setBasedOn(basedOn);
        }
    }

    private void setDefaultFlag(OfficeStyle style, boolean def) {
        if (style instanceof WordParagraphStyle) {
            ((WordParagraphStyle) style).setDefault(def);
        } else if (style instanceof WordRunStyle) {
            ((WordRunStyle) style).setDefault(def);
        } else if (style instanceof WordCellStyle) {
            ((WordCellStyle) style).setDefault(def);
        } else if (style instanceof WordTableStyle) {
            ((WordTableStyle) style).setDefault(def);
        } else if (style instanceof WordListStyle) {
            ((WordListStyle) style).setDefault(def);
        }
    }

    protected WordParagraphStyle parseParagraphStyleDef(XNode styleNode, String id) {
        WordParagraphStyle style = new WordParagraphStyle();
        style.setId(id);
        style.setType("paragraphStyle");
        applyCommonStyleAttrs(styleNode, style);

        XNode pPr = styleNode.childByTag("w:pPr");
        if (pPr != null) {
            XNode jc = pPr.childByTag("w:jc");
            if (jc != null) {
                OfficeHorizontalAlignment align = OfficeHorizontalAlignment.fromWmlText(jc.attrText("w:val"));
                if (align != null) {
                    style.setAlign(align);
                }
            }
            XNode spacing = pPr.childByTag("w:spacing");
            if (spacing != null) {
                style.setSpaceBefore(twipsToPoints(spacing.attrInt("w:before")));
                style.setSpaceAfter(twipsToPoints(spacing.attrInt("w:after")));
            }
            XNode ind = pPr.childByTag("w:ind");
            if (ind != null) {
                style.setLeftIndent(twipsToPoints(ind.attrInt("w:left")));
                style.setRightIndent(twipsToPoints(ind.attrInt("w:right")));
                style.setFirstLineIndent(twipsToPoints(ind.attrInt("w:firstLine")));
            }
            XNode outlineLvl = pPr.childByTag("w:outlineLvl");
            if (outlineLvl != null) {
                Integer level = outlineLvl.attrInt("w:val");
                if (level != null && level >= 0 && level < 9) {
                    style.setTitleLevel(level + 1);
                }
            }
        }

        // Note: word-paragraph-style.xdef has no font property; the run font of a
        // named paragraph style stays on styles.xml and is not modeled (follow-up).
        return style;
    }

    protected WordRunStyle parseRunStyleDef(XNode styleNode, String id) {
        WordRunStyle style = new WordRunStyle();
        style.setId(id);
        style.setType("runStyle");
        applyCommonStyleAttrs(styleNode, style);

        OfficeFont font = parseFontFromRPr(styleNode.childByTag("w:rPr"));
        if (font != null) {
            style.setFont(font);
        }
        XNode rPr = styleNode.childByTag("w:rPr");
        XNode highlight = rPr == null ? null : rPr.childByTag("w:highlight");
        if (highlight != null) {
            String val = highlight.attrText("w:val");
            if (!StringHelper.isEmpty(val)) {
                style.setHighlightColor(val);
            }
        }
        return style;
    }

    protected WordTableStyle parseTableStyleDef(XNode styleNode, String id) {
        WordTableStyle style = new WordTableStyle();
        style.setId(id);
        style.setType("tableStyle");
        applyCommonStyleAttrs(styleNode, style);
        return style;
    }

    protected WordListStyle parseListStyleDef(XNode styleNode, String id) {
        WordListStyle style = new WordListStyle();
        style.setId(id);
        style.setType("listStyle");
        applyCommonStyleAttrs(styleNode, style);

        XNode numFmt = styleNode.find(node -> "w:numFmt".equals(node.getTagName()));
        if (numFmt != null && !StringHelper.isEmpty(numFmt.attrText("w:val"))) {
            style.setNumFmt(numFmt.attrText("w:val"));
        }
        XNode lvlText = styleNode.find(node -> "w:lvlText".equals(node.getTagName()));
        if (lvlText != null) {
            style.setLvlText(lvlText.attrText("w:val"));
        }
        XNode start = styleNode.find(node -> "w:start".equals(node.getTagName()));
        if (start != null) {
            Integer val = start.attrInt("w:val");
            if (val != null) {
                style.setStart(val);
            }
        }
        XNode ind = styleNode.find(node -> "w:ind".equals(node.getTagName()));
        if (ind != null) {
            style.setIndent(twipsToPoints(ind.attrInt("w:left")));
        }
        return style;
    }

    // ------------------------------------------------------------------ hyperlink resolution

    /**
     * Resolves a w:hyperlink node to {href, anchor}. External relationships map to
     * href, internal targets and w:anchor map to anchor. Missing relationships
     * degrade to null targets plus a skip record (P6).
     */
    protected String[] resolveHyperlinkTarget(XNode hyperlinkNode, OfficeRelsPart rels, String partPath) {
        String anchor = hyperlinkNode.attrText("w:anchor");
        if (!StringHelper.isEmpty(anchor)) {
            return new String[]{null, anchor};
        }
        String rid = hyperlinkNode.attrText("r:id");
        if (StringHelper.isEmpty(rid)) {
            recordSkip("hyperlink without r:id/anchor: " + hyperlinkNode.getLocation());
            return new String[]{null, null};
        }
        OfficeRelationship rel = rels == null ? null : rels.getRelationship(rid);
        if (rel == null) {
            recordSkip("hyperlink relationship missing: " + rid);
            return new String[]{null, null};
        }
        if (rel.getTargetMode() == TargetMode.External) {
            return new String[]{rel.getTarget(), null};
        }
        return new String[]{null, rel.getTarget()};
    }

    protected OfficeRelsPart currentRels() {
        return context == null ? null : context.rels;
    }

    protected String currentPartPath() {
        return context == null ? null : context.partPath;
    }

    // ------------------------------------------------------------------ helpers

    protected XNode getBodyNode(XNode docNode) {
        return docNode.childByTag("w:body");
    }

    protected Double twipsToPoints(Integer twips) {
        if (twips == null) {
            return null;
        }
        return twips / 20.0;
    }

    protected Double emuToPoints(Integer emu) {
        if (emu == null) {
            return null;
        }
        return emu / 12700.0;
    }

    protected boolean isEmptyParagraphStyle(WordParagraphStyle style) {
        return style.getAlign() == null && style.getSpaceBefore() == null
                && style.getSpaceAfter() == null && StringHelper.isEmpty(style.getId());
    }

    protected boolean isEmptyRunStyle(WordRunStyle style) {
        return style.getFont() == null && StringHelper.isEmpty(style.getHighlightColor())
                && StringHelper.isEmpty(style.getId());
    }

    protected static class ParseContext {
        OfficeDocModel doc;
        OfficeRelsPart rels;
        String partPath;
        int skipCount;
        final List<String> skips = new ArrayList<>();

        void recordSkip(String detail) {
            skipCount++;
            if (skips.size() < 50) {
                skips.add(detail);
            }
        }

        void logSummary(Logger log) {
            if (skipCount > 0) {
                log.warn("docx parse degraded: skipped {} unsupported/missing elements: {}", skipCount, skips);
            }
        }
    }

    protected static class BlockTemplateParseState {
        final Map<XNode, BlockTemplateBinding> blockBindings = new IdentityHashMap<>();
        final Set<XNode> consumedMarkerNodes = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        final BlockTemplateBinding pageBinding = new BlockTemplateBinding();
    }

    protected static class BlockTemplateBinding {
        IEvalPredicate testExpr;
        IEvalAction visibleExpr;
        IEvalAction beginLoop;
        String loopVarName;
        String loopIndexName;
        String loopItemsName;

        boolean isEmpty() {
            return testExpr == null && visibleExpr == null && beginLoop == null
                    && StringHelper.isEmpty(loopVarName) && StringHelper.isEmpty(loopIndexName)
                    && StringHelper.isEmpty(loopItemsName);
        }
    }

    protected static class BlockTemplatePair {
        final WordHyperlink begin;
        final WordHyperlink end;

        BlockTemplatePair(WordHyperlink begin, WordHyperlink end) {
            this.begin = begin;
            this.end = end;
        }
    }
}
