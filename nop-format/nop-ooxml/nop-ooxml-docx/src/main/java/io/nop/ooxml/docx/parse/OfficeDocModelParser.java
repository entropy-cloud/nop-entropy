/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.gen.XplGenConfig;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.model.WordHyperlink;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeDocTemplateModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeRunTemplateModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.model.OfficeFont;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.constants.OfficeFontUnderline;
import io.nop.office.model.constants.OfficeHorizontalAlignment;
import io.nop.xlang.api.EvalCode;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.api.source.IWithSourceCode;
import io.nop.xlang.api.source.SourceEvalPredicate;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xdef.domain.XplStdDomainHandlers;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.ooxml.docx.DocxErrors.ARG_END_TAG_NAME;
import static io.nop.ooxml.docx.DocxErrors.ARG_LABEL;
import static io.nop.ooxml.docx.DocxErrors.ARG_LINK_NODE;
import static io.nop.ooxml.docx.DocxErrors.ARG_LINK_TARGET;
import static io.nop.ooxml.docx.DocxErrors.ARG_TAG_NAME;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_END_NOT_MATCH;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_NO_END;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_END_NO_BEGIN;

public class OfficeDocModelParser {
    private final WordTemplateParser templateParser = new WordTemplateParser();

    public OfficeDocModel parseFromResource(IResource resource) {
        WordOfficePackage pkg = new WordOfficePackage();
        try {
            pkg.loadFromFile(resource.toFile());

            XNode docNode = pkg.getWordXml();
            XplGenConfig config = templateParser.getGenConfig(docNode);
            config.addImportLib(DocxConstants.LIB_DOCX_GEN);

            XLangCompileTool cp = config.newCompileTool();
            cp.getScope().setLocalValue(null, DocxConstants.VAR_OFC_PKG, pkg);

            OfficeDocModel doc = new OfficeDocModel();
            doc.setLocation(docNode.getLocation());
            applyConfig(doc.makeModel(), config, cp);

            OfficeDocPageModel page = new OfficeDocPageModel();
            page.setLocation(docNode.getLocation());
            page.setName("page1");
            page.setBody(parseBlocks(pkg, DocxConstants.PATH_WORD_DOCUMENT, getBodyNode(docNode), page, config, cp));

            List<XNode> sectPrNodes = collectSectPrNodes(docNode);
            page.setHeader(parseRelatedParts(pkg, DocxConstants.PATH_WORD_DOCUMENT, sectPrNodes,
                    "w:headerReference", OfficeConstants.NS_HEADER, DocxConstants.PATH_PREFIX_HEADER, config, cp));
            page.setFooter(parseRelatedParts(pkg, DocxConstants.PATH_WORD_DOCUMENT, sectPrNodes,
                    "w:footerReference", OfficeConstants.NS_FOOTER, DocxConstants.PATH_PREFIX_FOOTER, config, cp));

            applyPageSize(doc, sectPrNodes);
            applyPageOrientation(page, sectPrNodes);
            doc.addPage(page);
            return doc;
        } finally {
            pkg.close();
        }
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
                continue;
            }
            XNode xml = part.buildXml(null);
            blocks.addAll(parseBlocks(pkg, partPath, xml, null, config, cp));
        }
        return blocks;
    }

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

        int paraIndex = 0;
        int tableIndex = 0;
        for (XNode child : container.getChildren()) {
            String tagName = child.getTagName();
            if ("w:p".equals(tagName)) {
                OfficeParagraphModel para = parseParagraph(child, links, config, cp, paraIndex++);
                applyBlockTemplateBinding(para, templateState.blockBindings.get(child));
                blocks.add(para);
            } else if ("w:tbl".equals(tagName)) {
                WordTable table = new WordTableParser().parseTable(child);
                table.setLocation(child.getLocation());
                table.setId("tbl" + (++tableIndex));
                applyBlockTemplateBinding(table, templateState.blockBindings.get(child));
                blocks.add(table);
            }
        }
        return blocks;
    }

    protected OfficeParagraphModel parseParagraph(XNode paraNode, List<WordHyperlink> links,
                                                  XplGenConfig config, XLangCompileTool cp,
                                                  int paraIndex) {
        OfficeParagraphModel para = new OfficeParagraphModel();
        para.setLocation(paraNode.getLocation());
        para.setId(getParaId(paraNode, paraIndex));

        WordParagraphStyle style = parseParagraphStyle(paraNode);
        if (style != null) {
            para.setStyle(style);
        }

        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr != null) {
            String pStyleId = ConvertHelper.toString(pPr.childAttr("w:pStyle", "w:val"));
            if (!StringHelper.isEmpty(pStyleId)) {
                para.setType(pStyleId);
            }
        }

        int runIndex = 0;
        for (XNode child : paraNode.getChildren()) {
            String tagName = child.getTagName();
            if ("w:r".equals(tagName)) {
                para.addR(parseRun(child, null, config, cp, para.getId(), runIndex++));
            } else if ("w:hyperlink".equals(tagName)) {
                WordHyperlink link = findLink(links, child);
                if (link == null || link.getLinkType() == WordHyperlink.LinkType.link
                        || link.isXplBegin() || link.isXplEnd()) {
                    for (XNode runNode : child.getChildren()) {
                        if ("w:r".equals(runNode.getTagName())) {
                            para.addR(parseRun(runNode, null, config, cp, para.getId(), runIndex++));
                        }
                    }
                } else {
                    para.addR(parseRun(link.getLinkNode().childByTag("w:r"), link, config, cp, para.getId(), runIndex++));
                }
            }
        }
        return para;
    }

    protected OfficeRunModel parseRun(XNode runNode, WordHyperlink link,
                                      XplGenConfig config, XLangCompileTool cp,
                                      String paraId, int runIndex) {
        OfficeRunModel run = new OfficeRunModel();
        SourceLocation loc = runNode == null ? null : runNode.getLocation();
        run.setLocation(loc);
        run.setId((paraId == null ? "p" : paraId) + "-r" + runIndex);

        if (runNode != null) {
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

    protected WordParagraphStyle parseParagraphStyle(XNode paraNode) {
        XNode pPr = paraNode.childByTag("w:pPr");
        if (pPr == null) {
            return null;
        }

        WordParagraphStyle style = new WordParagraphStyle();
        style.setLocation(pPr.getLocation());
        String styleId = ConvertHelper.toString(pPr.childAttr("w:pStyle", "w:val"));
        if (!StringHelper.isEmpty(styleId)) {
            style.setId(styleId);
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

        return isEmptyParagraphStyle(style) ? null : style;
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

        XNode sz = rPr.childByTag("w:sz");
        if (sz != null) {
            Integer value = sz.attrInt("w:val");
            if (value != null) {
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

        if (hasFont) {
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

    protected void applyPageSize(OfficeDocModel doc, List<XNode> sectPrNodes) {
        XNode sectPr = getLast(sectPrNodes);
        if (sectPr == null) {
            return;
        }
        XNode pgSz = sectPr.childByTag("w:pgSz");
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

    protected void applyPageOrientation(OfficeDocPageModel page, List<XNode> sectPrNodes) {
        XNode sectPr = getLast(sectPrNodes);
        if (sectPr == null) {
            return;
        }
        XNode pgSz = sectPr.childByTag("w:pgSz");
        if (pgSz != null) {
            String orient = pgSz.attrText("w:orient");
            if (!StringHelper.isEmpty(orient)) {
                page.setOrientation(orient);
            }
        }
    }

    protected XNode getBodyNode(XNode docNode) {
        return docNode.childByTag("w:body");
    }

    protected List<XNode> collectSectPrNodes(XNode docNode) {
        List<XNode> sectPrNodes = new ArrayList<>();
        docNode.forEachNode(node -> {
            if ("w:sectPr".equals(node.getTagName())) {
                sectPrNodes.add(node);
            }
        });
        return sectPrNodes;
    }

    protected XNode getLast(List<XNode> nodes) {
        return nodes.isEmpty() ? null : nodes.get(nodes.size() - 1);
    }

    protected Double twipsToPoints(Integer twips) {
        if (twips == null) {
            return null;
        }
        return twips / 20.0;
    }

    protected boolean isEmptyParagraphStyle(WordParagraphStyle style) {
        return style.getAlign() == null && style.getSpaceBefore() == null
                && style.getSpaceAfter() == null && StringHelper.isEmpty(style.getId());
    }

    protected boolean isEmptyRunStyle(WordRunStyle style) {
        return style.getFont() == null && StringHelper.isEmpty(style.getHighlightColor())
                && StringHelper.isEmpty(style.getId());
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
