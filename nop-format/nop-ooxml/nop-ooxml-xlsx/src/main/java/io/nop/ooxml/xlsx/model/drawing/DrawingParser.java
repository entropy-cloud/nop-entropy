/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model.drawing;

import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.constants.ExcelAnchorType;
import io.nop.excel.util.UnitsHelper;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.xlsx.chart.DrawingChartParser;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.xlang.xpath.XPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrEnumValue;

/**
 * DrawingParser handles parsing of Excel drawing elements including images and charts.
 * Uses SELECTOR mechanism for complex node selection similar to WordDrawing.
 */
public class DrawingParser {
    private static final Logger LOG = LoggerFactory.getLogger(DrawingParser.class);

    // SELECTOR机制 - 只用于复杂嵌套节点选择，简单子节点直接用childByTag
    static final IXSelector<XNode> SELECTOR_CHART_REF = XPathHelper.parseXSelector("a:graphic/a:graphicData/c:chart");
    static final IXSelector<XNode> SELECTOR_PIC_CNVPR = XPathHelper.parseXSelector("xdr:nvPicPr/xdr:cNvPr");
    static final IXSelector<XNode> SELECTOR_PIC_LOCKS = XPathHelper.parseXSelector("xdr:nvPicPr/xdr:cNvPicPr/a:picLocks");
    static final IXSelector<XNode> SELECTOR_BLIP = XPathHelper.parseXSelector("xdr:blipFill/a:blip");
    static final IXSelector<XNode> SELECTOR_XFRM = XPathHelper.parseXSelector("xdr:spPr/a:xfrm");

    public List<ExcelImage> parseImages(XNode node) {
        KeyedList<ExcelImage> ret = new KeyedList<>(ExcelImage::getName);

        for (XNode child : node.getChildren()) {
            XNode graphicFrame = child.childByTag("xdr:graphicFrame");
            if (graphicFrame != null)
                continue;

            if (child.getTagName().equals("xdr:twoCellAnchor")) {
                ExcelImage image = parseImage(child);
                // name重复，需要重命名
                while (ret.getByKey(image.getName()) != null) {
                    image.setName(StringHelper.nextName(image.getName()));
                }
                ret.add(image);
            }
        }
        return ret;
    }

    public List<ExcelChartModel> parseCharts(XNode node, ExcelOfficePackage pkg, IOfficePackagePart drawingPart) {
        KeyedList<ExcelChartModel> ret = new KeyedList<>(ExcelChartModel::getName);

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("xdr:twoCellAnchor")) {
                XNode graphicFrame = child.childByTag("xdr:graphicFrame");
                if (graphicFrame != null) {
                    ExcelClientAnchor anchor = parseAnchor(child);
                    String description = parseDescription(graphicFrame);

                    // 使用SELECTOR选择复杂嵌套的chart引用节点
                    XNode chartRef = (XNode) graphicFrame.selectOne(SELECTOR_CHART_REF);
                    if (chartRef != null) {
                        ExcelChartModel excelChart = new ExcelChartModel();
                        excelChart.setAnchor(anchor);
                        excelChart.setDescription(description);


                        DrawingChartParser.INSTANCE.parseChartRef(chartRef, pkg, drawingPart, excelChart);

                        while (ret.getByKey(excelChart.getName()) != null) {
                            excelChart.setName(StringHelper.nextName(excelChart.getName()));
                        }
                        ret.add(excelChart);
                    }
                }
            }
        }
        return ret;
    }

    private String parseDescription(XNode graphicFrame) {

        // 查找 xdr:nvGraphicFramePr/xdr:cNvPr 节点
        XNode nvGraphicFramePr = graphicFrame.childByTag("xdr:nvGraphicFramePr");
        if (nvGraphicFramePr != null) {
            XNode cNvPr = nvGraphicFramePr.childByTag("xdr:cNvPr");
            if (cNvPr != null) {
                String descr = cNvPr.attrText("descr");
                if (!StringHelper.isEmpty(descr)) {
                    return descr;
                }
            }
        }
        return null;
    }

    public ExcelImage parseImage(XNode anchorNode) {
        XNode clientData = anchorNode.childByTag("xdr:clientData");
        XNode pic = anchorNode.childByTag("xdr:pic");

        // 使用SELECTOR选择复杂嵌套节点
        XNode cNvPr = pic != null ? pic.selectNode(SELECTOR_PIC_CNVPR) : null;
        XNode picLocks = pic != null ? pic.selectNode(SELECTOR_PIC_LOCKS) : null;
        XNode blip = pic != null ? pic.selectNode(SELECTOR_BLIP) : null;
        XNode xfrm = pic != null ? pic.selectNode(SELECTOR_XFRM) : null;

        ExcelImage image = new ExcelImage();
        ExcelClientAnchor anchor = parseAnchor(anchorNode);
        image.setAnchor(anchor);

        XNode xdrShape = anchorNode.childByTag("xdr:sp");
        if (xdrShape != null) {
            image.setShape(xdrShape.cloneInstance());
        }

        if (cNvPr != null) {
            String name = cNvPr.attrText("name");
            String desc = cNvPr.attrText("descr");
            image.setDescription(desc);
            image.setName(name);
        }

        if (image.getName() == null)
            image.setName(StringHelper.generateUUID());

        if (blip != null) {
            String embedId = blip.attrText("r:embed");
            image.setEmbedId(embedId);
        }

        if (xfrm != null) {
            Integer rot = xfrm.attrInt("rot");
            if (rot != null) {
                image.setRotateDegree(rot / 60000.0);
            }
        }

        if (picLocks != null) {
            image.setNoChangeAspect(picLocks.attrInt("noChangeAspect", 1) == 1);
        }

        if (clientData != null) {
            image.setPrint(clientData.attrInt("fPrintsWithSheet", 1) == 1);
        }
        return image;
    }

    ExcelClientAnchor parseAnchor(XNode anchor) {
        ExcelAnchorType anchorType = parseAttrEnumValue(anchor, "editAs", ExcelAnchorType.class);

        XNode from = anchor.childByTag("xdr:from");
        XNode to = anchor.childByTag("xdr:to");

        int col1 = 0, row1 = 0, colOff1 = 0, rowOff1 = 0;
        if (from != null) {
            XNode colNode = from.childByTag("xdr:col");
            XNode rowNode = from.childByTag("xdr:row");
            XNode colOffNode = from.childByTag("xdr:colOff");
            XNode rowOffNode = from.childByTag("xdr:rowOff");

            col1 = colNode != null ? colNode.contentAsInt(0) : 0;
            row1 = rowNode != null ? rowNode.contentAsInt(0) : 0;
            colOff1 = colOffNode != null ? colOffNode.contentAsInt(0) : 0;
            rowOff1 = rowOffNode != null ? rowOffNode.contentAsInt(0) : 0;
        }

        int col2 = 0, row2 = 0, colOff2 = 0, rowOff2 = 0;
        if (to != null) {
            XNode colNode = to.childByTag("xdr:col");
            XNode rowNode = to.childByTag("xdr:row");
            XNode colOffNode = to.childByTag("xdr:colOff");
            XNode rowOffNode = to.childByTag("xdr:rowOff");

            col2 = colNode != null ? colNode.contentAsInt(0) : 0;
            row2 = rowNode != null ? rowNode.contentAsInt(0) : 0;
            colOff2 = colOffNode != null ? colOffNode.contentAsInt(0) : 0;
            rowOff2 = rowOffNode != null ? rowOffNode.contentAsInt(0) : 0;
        }

        ExcelClientAnchor ret = new ExcelClientAnchor();
        ret.setType(anchorType);
        ret.setCol1(col1);
        ret.setRow1(row1);
        ret.setColDelta(col2 - col1);
        ret.setRowDelta(row2 - row1);
        ret.setDx1(UnitsHelper.emuToPoints(colOff1));
        ret.setDy1(UnitsHelper.emuToPoints(rowOff1));
        ret.setDx2(UnitsHelper.emuToPoints(colOff2));
        ret.setDy2(UnitsHelper.emuToPoints(rowOff2));

        return ret;
    }
}
