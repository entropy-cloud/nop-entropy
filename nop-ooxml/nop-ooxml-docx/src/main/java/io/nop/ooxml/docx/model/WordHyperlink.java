/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.docx.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.ooxml.common.OfficeErrors.ARG_LABEL;
import static io.nop.ooxml.common.OfficeErrors.ARG_NODE;
import static io.nop.ooxml.common.OfficeErrors.ARG_SOURCE;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_LINK_SOURCE_NOT_XML;

/**
 * <pre>
 * {@code
 * <w:p w14:paraId="0D781950" w14:textId="601E51A8" w:rsidR="00F64E3C" w:rsidRDefault="00FF7362">
 * <w:pPr>
 * <w:spacing w:line="360" w:lineRule="auto"/>
 * <w:jc w:val="center"/>
 * <w:rPr>
 * <w:rFonts w:ascii="Source Han Sans CN Regular" w:eastAsia="Source Han Sans CN Regular"
 * w:hAnsi="Source Han Sans CN Regular" w:cs="Source Han Sans CN Regular"/>
 * <w:sz w:val="52"/>
 * <w:szCs w:val="44"/>
 * </w:rPr>
 * </w:pPr>
 * <w:hyperlink r:id="rId8" w:history="1">
 * <w:r w:rsidR="00102324">
 * <w:rPr>
 * <w:rStyle w:val="af3"/>
 * <w:rFonts w:ascii="Source Han Sans CN Regular" w:eastAsia="Source Han Sans CN Regular"
 * w:hAnsi="Source Han Sans CN Regular" w:cs="Source Han Sans CN Regular" w:hint="eastAsia"/>
 * <w:sz w:val="52"/>
 * <w:szCs w:val="44"/>
 * <w:lang w:eastAsia="zh-Hans"/>
 * </w:rPr>
 * <w:t>${model.displayName}</w:t>
 * </w:r>
 * </w:hyperlink>
 * </w:p>
 * <w:p w14:paraId="536FD8F2" w14:textId="77777777" w:rsidR="00F64E3C" w:rsidRDefault="00BC3EB6">
 * <w:pPr>
 * <w:spacing w:line="360" w:lineRule="auto"/>
 * <w:jc w:val="center"/>
 * <w:rPr>
 * <w:rFonts w:ascii="Source Han Sans CN Regular" w:eastAsia="Source Han Sans CN Regular"
 * w:hAnsi="Source Han Sans CN Regular" w:cs="Source Han Sans CN Regular"/>
 * <w:sz w:val="44"/>
 * <w:szCs w:val="44"/>
 * </w:rPr>
 * </w:pPr>
 * <w:r>
 * <w:rPr>
 * <w:rFonts w:ascii="Source Han Sans CN Regular" w:eastAsia="Source Han Sans CN Regular"
 * w:hAnsi="Source Han Sans CN Regular" w:cs="Source Han Sans CN Regular" w:hint="eastAsia"/>
 * <w:sz w:val="44"/>
 * <w:szCs w:val="44"/>
 * <w:lang w:eastAsia="zh-Hans"/>
 * </w:rPr>
 * <w:t>[元数据建模信息</w:t>
 * </w:r>
 * <w:r>
 * <w:rPr>
 * <w:rFonts w:ascii="Source Han Sans CN Regular" w:eastAsia="Source Han Sans CN Regular"
 * w:hAnsi="Source Han Sans CN Regular" w:cs="Source Han Sans CN Regular" w:hint="eastAsia"/>
 * <w:sz w:val="44"/>
 * <w:szCs w:val="44"/>
 * </w:rPr>
 * <w:t>有限公司]</w:t>
 * </w:r>
 * </w:p>
 * }
 * </pre>
 */
public class WordHyperlink {
    static final Logger LOG = LoggerFactory.getLogger(WordHyperlink.class);

    public enum LinkType {
        link, // 对应普通超链接
        expr, // 对应expr:xxx这种形式
        tpl_expr, // 对应 xpl: a${b}c 这种模板输出表达式形式
        xpl_begin, // 对应xpl: <c:for> 这种起始标签形式
        xpl_end, // 对应 xpl: </c:for> 这种标签结束节点形式
        xpl; // 对应 xpl: <my:MyTag a="xx" />这种完整标签调用形式
    }

    private String id;
    private LinkType linkType;
    private String linkLabel;
    private String linkTarget;
    private XNode linkNode;
    private XNode sourceNode;

    public static WordHyperlink build(OfficeRelsPart rels, XNode node) {
        String id = node.attrText("r:id");
        OfficeRelationship rel = rels.getRelationship(id);
        if (rel == null) {
            if (rel == null)
                LOG.warn("nop.word.ignore-link-ref:id={},node={}", id, node);
            return null;
        }

        String label = node.text();
        return build(rel, label, node);
    }

    public static WordHyperlink build(OfficeRelationship rel, String linkLabel, XNode linkNode) {
        WordHyperlink link = new WordHyperlink();
        String target = rel.getTarget();

        link.setId(rel.getId());
        link.setLinkLabel(linkLabel);
        link.setLinkTarget(target);
        link.setLinkNode(linkNode);

        if (StringHelper.startsWithNamespace(target, LinkType.expr.name())) {
            String source = decodeUrl(target.substring(LinkType.expr.name().length() + 1));
            if (StringHelper.isEmpty(source)) {
                source = linkLabel;
            }
            link.setLinkType(LinkType.expr);
            XNode sourceNode = buildSourceNode(linkNode);
            sourceNode.makeChild("w:t").content(sourceNode.getLocation(), "${" + source + "}");
            link.setSourceNode(sourceNode);
        } else if (StringHelper.startsWithNamespace(target, LinkType.xpl.name())) {
            String source = decodeUrl(target.substring(LinkType.xpl.name().length() + 1));
            if (StringHelper.isEmpty(source)) {
                source = linkLabel;
            }
            parseLinkSource(source, link, linkNode);
        } else {
            // 普通超链接
            link.setLinkType(LinkType.link);
        }

        return link;
    }

    static XNode buildSourceNode(XNode linkNode) {
        XNode sourceNode = linkNode.childByTag("w:r").cloneInstance();
        // 删除超链接增加的特殊样式
        sourceNode.makeChild("w:rPr").removeChildByTag("w:rStyle");
        return sourceNode;
    }

    public static String decodeUrl(String url) {
        // 表达式中的+需要保留，否则被转换为空格
        url = StringHelper.replace(url, "+", "%2B");
        return StringHelper.decodeURL(url);
    }

    static void parseLinkSource(String source, WordHyperlink link, XNode linkNode) {
        source = source.trim();
        if (source.startsWith("</")) {
            if (source.endsWith(">")) {
                String tagName = source.substring(2, source.length() - 1);
                if (StringHelper.isValidXmlName(tagName)) {
                    link.setLinkType(LinkType.xpl_end);
                    link.setSourceNode(XNode.make(tagName));
                    return;
                }
            }
            throw new NopException(ERR_OOXML_LINK_SOURCE_NOT_XML).param(ARG_SOURCE, source)
                    .param(ARG_LABEL, link.getLinkLabel()).param(ARG_NODE, link.getLinkNode());
        } else if (!source.startsWith("<")) {
            link.setLinkType(LinkType.tpl_expr);
            XNode sourceNode = buildSourceNode(linkNode);
            sourceNode.makeChild("w:t").content(sourceNode.getLocation(), source);
            link.setSourceNode(sourceNode);
        } else {
            if (!source.endsWith(">"))
                throw new NopException(ERR_OOXML_LINK_SOURCE_NOT_XML).param(ARG_SOURCE, source)
                        .param(ARG_LABEL, link.getLinkLabel()).param(ARG_NODE, link.getLinkNode());

            try {
                if (source.endsWith("/>")) {
                    XNode node = XNodeParser.instance().parseFromText(linkNode.getLocation(), source);
                    link.setLinkType(LinkType.xpl);
                    link.setSourceNode(node);
                } else {
                    TextScanner sc = TextScanner.fromString(linkNode.getLocation(), source);
                    sc.match('<');
                    String tagName = sc.nextXmlName();
                    if (source.endsWith("</" + tagName + ">")) {
                        XNode node = XNodeParser.instance().parseFromText(linkNode.getLocation(), source);
                        link.setLinkType(LinkType.xpl);
                        link.setSourceNode(node);
                        XNode child = node.childByTag("p");
                        if (child != null) {
                            XNode pNode = linkNode.closest("w:p").cloneInstance();
                            pNode.setTagName("p");
                            child.replaceBy(pNode);
                        } else {
                            child = node.childByTag("hyperlink");
                            if (child != null) {
                                XNode hyperLink = linkNode.cloneInstance();
                                hyperLink.setTagName("hyperlink");
                                child.replaceBy(hyperLink);
                            }
                        }
                    } else {
                        String fullSource = source + "</" + tagName + ">";
                        XNode node = XNodeParser.instance().parseFromText(linkNode.getLocation(), fullSource);
                        link.setLinkType(LinkType.xpl_begin);
                        link.setSourceNode(node);
                    }
                }
            } catch (Exception e) {
                throw new NopException(ERR_OOXML_LINK_SOURCE_NOT_XML, e).param(ARG_SOURCE, source)
                        .param(ARG_LABEL, link.getLinkLabel()).param(ARG_NODE, link.getLinkNode());
            }
        }
    }

    public boolean isXplBegin() {
        return linkType == LinkType.xpl_begin;
    }

    public boolean isXplEnd() {
        return linkType == LinkType.xpl_end;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTagName() {
        if (sourceNode == null)
            return null;
        return sourceNode.getTagName();
    }

    public LinkType getLinkType() {
        return linkType;
    }

    public void setLinkType(LinkType linkType) {
        this.linkType = linkType;
    }

    public String getLinkLabel() {
        return linkLabel;
    }

    public void setLinkLabel(String linkLabel) {
        this.linkLabel = linkLabel;
    }

    public String getLinkTarget() {
        return linkTarget;
    }

    public void setLinkTarget(String linkTarget) {
        this.linkTarget = linkTarget;
    }

    public XNode getLinkNode() {
        return linkNode;
    }

    public void setLinkNode(XNode linkNode) {
        this.linkNode = linkNode;
    }

    public XNode getSourceNode() {
        return sourceNode;
    }

    public void setSourceNode(XNode sourceNode) {
        this.sourceNode = sourceNode;
    }
}