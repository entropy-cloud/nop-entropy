/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.tree.ITreeVisitor;
import io.nop.core.model.tree.TreeVisitResult;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelTable;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.gen.XplGenConfig;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.ooxml.docx.WordTemplate;
import io.nop.ooxml.docx.model.WordDrawing;
import io.nop.ooxml.docx.model.WordHyperlink;
import io.nop.ooxml.docx.model.WordHyperlinkTransformer;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.nop.ooxml.docx.DocxConstants.HEADER_XPL_GEN_CONFIG;
import static io.nop.ooxml.docx.DocxErrors.ARG_END_TAG_NAME;
import static io.nop.ooxml.docx.DocxErrors.ARG_LABEL;
import static io.nop.ooxml.docx.DocxErrors.ARG_LINK_NODE;
import static io.nop.ooxml.docx.DocxErrors.ARG_LINK_TARGET;
import static io.nop.ooxml.docx.DocxErrors.ARG_TAG_NAME;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_END_NOT_MATCH;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_NO_END;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_END_NO_BEGIN;

public class WordTemplateParser {
    static final Logger LOG = LoggerFactory.getLogger(WordTemplateParser.class);

    public WordTemplate parseFromResource(IResource resource) {
        WordOfficePackage pkg = new WordOfficePackage();
        try {
            pkg.loadFromFile(resource.toFile());

            XNode doc = pkg.getWordXml();
            XplGenConfig config = getGenConfig(doc);
            config.addImportLib(DocxConstants.LIB_DOCX_GEN);

            doc = config.checkDump(doc, "before-normalize");

            XLangCompileTool cp = config.newCompileTool();
            cp.getScope().setLocalValue(DocxConstants.VAR_OFC_PKG, pkg);

            IEvalAction beforeGen = config.compileBeforeGen(cp);

            pkg.removeFile(DocxConstants.PATH_WORD_DOCUMENT);
            ITextTemplateOutput output = compile(pkg, config, cp, doc, DocxConstants.PATH_WORD_DOCUMENT);

            Map<String, ITextTemplateOutput> outputs = compileOutputs(pkg, (path, xml) -> {
                return compile(pkg, config, cp, xml, path);
            });

            outputs.put(DocxConstants.PATH_WORD_DOCUMENT, output);

            IEvalAction afterGen = config.compileAfterGen(cp);

            return buildWordTemplate(pkg, beforeGen, outputs, afterGen, config);
        } catch (Exception e) {
            IoHelper.safeClose(pkg);
            throw NopException.adapt(e);
        }
    }

    protected WordTemplate buildWordTemplate(WordOfficePackage pkg, IEvalAction beforeGen,
                                             Map<String, ITextTemplateOutput> outputs,
                                             IEvalAction afterGen, XplGenConfig config) {
        return new WordTemplate(pkg, beforeGen, outputs, afterGen, config);
    }

    private ITextTemplateOutput compile(WordOfficePackage pkg, XplGenConfig config,
                                        XLangCompileTool cp, XNode doc, String path) {
        OfficeRelsPart rels = pkg.getRelsForPartPath(path);
        replaceHyperLinkExprs(doc);
        List<WordHyperlink> links = collectLinks(rels, doc, config);

        normalizeExprs(doc, links);
        normalizeLinks(links);

        processDrawings(pkg, doc);
        postProcessDocNode(pkg, config, cp, doc);

        boolean dumpToFile = path.equals(DocxConstants.PATH_WORD_DOCUMENT);
        if (config.isDump() && !dumpToFile) {
            doc.dump();
        }
        return config.compile(cp, doc, XLangOutputMode.xml, dumpToFile);
    }

    protected void postProcessDocNode(WordOfficePackage pkg, XplGenConfig config,
                                      XLangCompileTool cp, XNode doc) {

    }

    private Map<String, ITextTemplateOutput> compileOutputs(WordOfficePackage pkg,
                                                            BiFunction<String, XNode, ITextTemplateOutput> compile) {
        Map<String, ITextTemplateOutput> outputs = new TreeMap<>();

        Iterator<IOfficePackagePart> it = pkg.getFiles().iterator();
        while (it.hasNext()) {
            IOfficePackagePart part = it.next();
            String path = part.getPath();
            if (path.endsWith(".xml") && mayContainsExpr(path)) {
                String text = part.loadText();
                if (text.contains("${")) {
                    XNode node = part.loadXml();

                    ITextTemplateOutput output = compile.apply(path, node);
                    if (output != null) {
                        outputs.put(path, output);
                        it.remove();
                    }
                }
            }
        }
        return outputs;
    }

    private boolean mayContainsExpr(String path) {
        return path.startsWith(DocxConstants.PATH_PREFIX_HEADER)
                || path.startsWith(DocxConstants.PATH_PREFIX_FOOTER);
    }

    void replaceHyperLinkExprs(XNode doc) {
        List<XNode> instrNodes = new ArrayList<>();
        doc.forEachNode(node -> {
            if (node.getTagName().equals("w:instrText")) {
                String content = node.contentText();
                if (content != null) {
                    if (content.startsWith("HYPERLINK ") || content.startsWith(" HYPERLINK ")) {
                        instrNodes.add(node);
                    }
                }
            }
        });
        instrNodes.forEach(WordHyperlinkTransformer::transformNode);
    }

    /*
     * 将超链接标注之外的文本内容中的$符号替换为 ${'$'}，避免它们被误识别为表达式。
     */
    void normalizeExprs(XNode doc, List<WordHyperlink> links) {
        Set<XNode> linkNodes = links.stream().map(WordHyperlink::getLinkNode).collect(Collectors.toSet());

        doc.visit(new ITreeVisitor<>() {
            @Override
            public TreeVisitResult beginNode(XNode node) {
                if (linkNodes.contains(node)) {
                    return TreeVisitResult.SKIP_CHILD;
                } else if (node.getTagName().equals("w:t")) {
                    node.normalizeExprInContent();
                }
                return TreeVisitResult.CONTINUE;
            }
        });
    }

    List<WordHyperlink> collectLinks(OfficeRelsPart rels, XNode doc, XplGenConfig config) {
        List<WordHyperlink> links = new ArrayList<>();
        doc.findAll(WordXmlHelper::isHyperlink)
                .forEach(node -> collectLink(rels, node, links, config));
        return links;
    }

    void collectLink(OfficeRelsPart rels, XNode node, List<WordHyperlink> links, XplGenConfig config) {
        WordHyperlink link = WordHyperlink.build(rels, node, config);
        if (link != null && link.getLinkType() != WordHyperlink.LinkType.link) {
            links.add(link);
            rels.removeRelationshipById(link.getId());
        }
    }

    XplGenConfig getGenConfig(XNode doc) {
        XNode tableN = doc.find(node -> {
            if (WordXmlHelper.isTable(node)) {
                WordTableParser parser = new WordTableParser();
                if (parser.isMatchHeader(node, HEADER_XPL_GEN_CONFIG)) {
                    return true;
                }
            }
            return false;
        });

        if (tableN == null)
            return new XplGenConfig();

        ExcelTable table = new WordTableParser().parseTable(tableN);
        XplGenConfig config = XplGenConfig.parseFromTable(table);
        config.setLocation(tableN.getLocation());

        // 删除XplGenConfig表格后的所有内容，它们仅存在于配置模板中，不属于模板输出的内容。因此在XplGenConfig配置表格的后面可以写一些说明性文字
        if (config.isDeleteAllAfterConfigTable()) {
            removeTail(tableN);
        } else {
            // 只删除表格节点
            tableN.detach();
        }
        return config;
    }

    void removeTail(XNode node) {
        XNode parent = node.getParent();
        int childIndex = node.childIndex();
        for (int i = childIndex, n = parent.getChildCount(); i < n; i++) {
            XNode child = parent.child(i);
            // 删除所有除w:sectPr之外的节点
            if (!child.getTagName().equals("w:sectPr")) {
                parent.removeChildByIndex(i);
                i--;
                n--;
            }
        }
    }

    /**
     * 根据超链接标注将 expr:xx以及 xpl:yyy等格式的超链接节点替换为xpl标签节点
     */
    void normalizeLinks(List<WordHyperlink> links) {
        List<WordHyperlink> stack = new ArrayList<>();
        for (int i = 0, n = links.size(); i < n; i++) {
            WordHyperlink link = links.get(i);
            if (link.isXplBegin()) {
                LOG.info("nop.word.xpl-begin:label={},target={},node={}",
                        link.getLinkLabel(), link.getLinkTarget(), link.getLinkNode());
                stack.add(link);
            } else if (link.isXplEnd()) {
                LOG.info("nop.word.xpl-end:label={},target={},node={}",
                        link.getLinkLabel(), link.getLinkTarget(), link.getLinkNode());

                if (stack.isEmpty())
                    throw new NopException(ERR_DOCX_XPL_END_NO_BEGIN).param(ARG_TAG_NAME, link.getTagName())
                            .param(ARG_LABEL, link.getLinkLabel()).param(ARG_LINK_TARGET, link.getLinkTarget());

                WordHyperlink begin = stack.remove(stack.size() - 1);
                if (!begin.getTagName().equals(link.getTagName()))
                    throw new NopException(ERR_DOCX_XPL_BEGIN_END_NOT_MATCH).param(ARG_TAG_NAME, begin.getTagName())
                            .param(ARG_END_TAG_NAME, link.getTagName()).param(ARG_LABEL, link.getLinkLabel())
                            .param(ARG_LINK_TARGET, link.getLinkTarget());

                XNode parent = begin.getLinkNode().commonAncestor(link.getLinkNode());

                int d0 = parent.depth();
                int d1 = begin.getLinkNode().depth();
                int d2 = link.getLinkNode().depth();
                XNode r1 = begin.getLinkNode().parent(d1 - d0 - 2);
                XNode r2 = link.getLinkNode().parent(d2 - d0 - 2);
                XNode newNode = begin.getSourceNode();
                int index1 = r1.childIndex();
                int index2 = r2.childIndex();
                // tr的情况需要特殊处理。在第一个和最后一个单元格处设置的标签表示整个tr需要被嵌入。 tr的第一个节点可能是w:trPr而不是w:tc
                if (index2 == parent.getChildCount() - 1 && parent.getTagName().equals("w:tr")
                        && r1.childIndexOfSameTag() == 0) {
                    parent.replaceBy(newNode);
                    parent.detach();
                    newNode.appendChild(parent);
                    begin.getLinkNode().detach();
                    link.getLinkNode().detach();
                } else {
                    for (int k = 0; k <= index2 - index1; k++) {
                        XNode child = parent.removeChildByIndex(index1);
                        child.detach();
                        newNode.appendChild(child);
                    }

                    begin.getLinkNode().detach();
                    link.getLinkNode().detach();
                    parent.insertChild(index1, newNode);
                }
            } else if (link.getLinkType() == WordHyperlink.LinkType.expr) {
                link.getLinkNode().replaceBy(link.getSourceNode());
            } else if (link.getLinkType() == WordHyperlink.LinkType.xpl) {
                XNode node = link.getSourceNode();
                if (node.getTagName().startsWith("docx-gen:p-")) {
                    link.getLinkNode().closest("w:p").replaceBy(link.getSourceNode());
                } else if (node.getTagName().startsWith("docx-gen:r-")) {
                    XNode rPr = link.getLinkNode().findByTag("w:rPr");
                    XNode sourceNode = link.getSourceNode();
                    if (rPr != null) {
                        XNode rPrCopy = rPr.cloneInstance();
                        rPrCopy.setTagName("rPr");
                        sourceNode.appendChild(rPrCopy);
                    }
                    link.getLinkNode().replaceBy(link.getSourceNode());
                } else {
                    link.getLinkNode().replaceBy(link.getSourceNode());
                }
            } else if (link.getLinkType() == WordHyperlink.LinkType.tpl_expr) {
                link.getLinkNode().replaceBy(link.getSourceNode());
            }
        }

        if (!stack.isEmpty()) {
            WordHyperlink link = stack.get(0);
            throw new NopException(ERR_DOCX_XPL_BEGIN_NO_END).param(ARG_LABEL, link.getLinkLabel())
                    .param(ARG_TAG_NAME, link.getTagName()).param(ARG_LINK_TARGET, link.getLinkTarget())
                    .param(ARG_LINK_NODE, link.getLinkNode());
        }
    }

    void processDrawings(WordOfficePackage pkg, XNode doc) {
        doc.forEachNode(node -> {
            if (node.getTagName().equals("w:drawing")) {
                new WordDrawing(node, pkg).prepare();
            }
        });
    }
}