/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.docx.model;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.docx.DocxConstants;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xpath.XPathHelper;

/*
<w:p w14:paraId="33D7C177" w14:textId="232C0585" w:rsidR="00CF04C6" w:rsidRDefault="00CF04C6" w:rsidP="00FA2BB8">
        <w:pPr>
          <w:jc w:val="right"/>
          <w:rPr>
            <w:noProof/>
          </w:rPr>
        </w:pPr>
        <w:r>
          <w:rPr>
            <w:noProof/>
          </w:rPr>
          <w:drawing>
            <wp:anchor distT="0" distB="0" distL="114300" distR="114300" simplePos="0" relativeHeight="251661312" behindDoc="0"
               locked="0" layoutInCell="1" allowOverlap="1" wp14:anchorId="782EDF39" wp14:editId="010444D5">
              <wp:simplePos x="0" y="0"/>
              <wp:positionH relativeFrom="column">
                <wp:posOffset>1568450</wp:posOffset>
              </wp:positionH>
              <wp:positionV relativeFrom="paragraph">
                <wp:posOffset>45720</wp:posOffset>
              </wp:positionV>
              <wp:extent cx="1281430" cy="1165860"/>
              <wp:effectExtent l="0" t="0" r="0" b="0"/>
              <wp:wrapThrough wrapText="bothSides">
                <wp:wrapPolygon edited="0">
                  <wp:start x="0" y="0"/>
                  <wp:lineTo x="0" y="21176"/>
                  <wp:lineTo x="21193" y="21176"/>
                  <wp:lineTo x="21193" y="0"/>
                  <wp:lineTo x="0" y="0"/>
                </wp:wrapPolygon>
              </wp:wrapThrough>
              <wp:docPr id="2" name="图片 2">
                <a:hlinkClick xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" r:id="rId40"/>
              </wp:docPr>
              <wp:cNvGraphicFramePr>
                <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
              </wp:cNvGraphicFramePr>
              <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                    <pic:nvPicPr>
                      <pic:cNvPr id="2" name="图片 2">
                        <a:hlinkClick r:id="rId40"/>
                      </pic:cNvPr>
                      <pic:cNvPicPr/>
                    </pic:nvPicPr>
                    <pic:blipFill>
                      <a:blip r:embed="rId41"/>
                      <a:stretch>
                        <a:fillRect/>
                      </a:stretch>
                    </pic:blipFill>
                    <pic:spPr>
                      <a:xfrm>
                        <a:off x="0" y="0"/>
                        <a:ext cx="1281430" cy="1165860"/>
                      </a:xfrm>
                      <a:prstGeom prst="rect">
                        <a:avLst/>
                      </a:prstGeom>
                    </pic:spPr>
                  </pic:pic>
                </a:graphicData>
              </a:graphic>
              <wp14:sizeRelH relativeFrom="margin">
                <wp14:pctWidth>0</wp14:pctWidth>
              </wp14:sizeRelH>
              <wp14:sizeRelV relativeFrom="margin">
                <wp14:pctHeight>0</wp14:pctHeight>
              </wp14:sizeRelV>
            </wp:anchor>
          </w:drawing>
        </w:r>
      </w:p>
 */
public class WordDrawing {
    static final IXSelector<XNode> SELECTOR_HLINK = XPathHelper.parseXSelector("wp:docPr/a:hlinkClick");

    static final IXSelector<XNode> SELECTOR_GRAPHIC_HLINK = XPathHelper
            .parseXSelector("a:graphic/a:graphicData/pic:pic/pic:nvPicPr/pic:cNvPr/a:hlinkClick");

    static final IXSelector<XNode> SELECTOR_GRAPHIC_BLIP = XPathHelper
            .parseXSelector("a:graphic/a:graphicData/pic:pic/pic:blipFill/a:blip");

    private final XNode node;
    private XNode docLink;

    private XNode graphicLink;
    private XNode graphicBlip;

    private XNode picNode;
    private XNode docPrNode;
    private String expr;

    public WordDrawing(XNode node, OfficePackage pkg) {
        this.node = node;
        XNode child = node.childByTag("wp:inline");
        if (child == null)
            child = node.child(0);

        this.docLink = (XNode) child.selectOne(SELECTOR_HLINK);
        if (docLink != null) {
            String rid = docLink.attrText("r:id");
            if (!StringHelper.isEmpty(rid)) {
                OfficeRelsPart rels = pkg.getRels(DocxConstants.PATH_WORD_RELS);
                OfficeRelationship rel = rels.getRelationship(rid);
                if (rel != null) {
                    if (rel.isExternalLink()) {
                        if (StringHelper.startsWithNamespace(rel.getTarget(), WordHyperlink.LinkType.expr.name())) {
                            expr = rel.getTarget().substring(WordHyperlink.LinkType.expr.name().length() + 1);
                            expr = WordHyperlink.decodeUrl(expr);

                            graphicLink = (XNode) child.selectOne(SELECTOR_GRAPHIC_HLINK);
                            graphicBlip = (XNode) child.selectOne(SELECTOR_GRAPHIC_BLIP);
                            docPrNode = docLink.getParent();
                            if (graphicLink != null) {
                                picNode = graphicLink.getParent();
                            }
                        }
                    }
                }
            }
        }
    }

    public XNode getNode() {
        return node;
    }

    public boolean isExprLink() {
        return expr != null;
    }

    public void prepare() {
        if (!isExprLink()) {
            return;
        }

        node.setAttr(XLangConstants.ATTR_XPL_IS, DocxConstants.TAG_DRAWING);
        node.setAttr("resource", "${" + expr + "}");
        if (docLink != null)
            docLink.detach();

        if (graphicLink != null)
            graphicLink.detach();

        if (docPrNode != null) {
            docPrNode.setAttr("id", "${rel.idNoPrefix}");
        }

        if (picNode != null) {
            picNode.setAttr("id", "${rel.idNoPrefix}");
        }

        // imgId参数由docx-gen:Drawing标签提供
        graphicBlip.setAttr("r:embed", "${rel.id}");
    }
}