package io.nop.ooxml.xlsx.model.drawing;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelImage;

import java.util.List;

public class DrawingBuilder {
    public XNode build(List<ExcelImage> images) {
        XNode node = XNode.makeDocNode("xdr:wsDr");
        node.setAttr("xmlns:xdr", "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing");
        node.setAttr("xmlns:a", "http://schemas.openxmlformats.org/drawingml/2006/main");

        for (int i = 0, n = images.size(); i < n; i++) {
            XNode anchor = buildAnchor(images.get(0), i);
            node.appendChild(anchor);
        }
        return node;
    }

    public XNode buildAnchor(ExcelImage image, int index) {
        XNode anchor = buildAnchor0(image.getAnchor());

        XNode pic = anchor.addChild("xdr:pic");
        XNode nvPicPr = pic.addChild("xdr:nvPicPr");
        XNode cNvPr = nvPicPr.addChild("xdr:cNvPr");
        cNvPr.setAttr("id", index);
        cNvPr.setAttr("name", image.getName());
        cNvPr.setAttr("descr", image.getDescription());

        XNode cNvPicPr = nvPicPr.addChild("xdr:cNvPicPr");
        cNvPicPr.addChild("a:picLocks").setAttr("noChangeAspect", image.isNoChangeAspect() ? 1 : 0);

        int rot = (int) (image.getRotateDegree() * 60000);
        XNode blipFill = pic.addChild("xdr:blipFill");
        if (rot > 0) {
            blipFill.setAttr("rotWithShape", 1);
        }
        XNode blip = blipFill.addChild("a:blip");
        blip.setAttr("r:embed", image.getEmbedId());
        blipFill.addChild("a:stretch");

        XNode spPr = pic.addChild("xdr:spPr");
        XNode xfrm = spPr.addChild("a:xfrm");
        if (rot > 0) {
            xfrm.setAttr("rot", rot);
        }
        XNode off = xfrm.addChild("a:off");
        off.setAttr("x", 0);
        off.setAttr("y", 0);
        XNode ext = xfrm.addChild("a:ext");
        ext.setAttr("cx", 0);
        ext.setAttr("cy", 0);

        XNode prstGeom = spPr.addChild("a:prstGeom");
        prstGeom.setAttr("prst", "rect");
        prstGeom.addChild("a:avLst");

        XNode clientData = anchor.addChild("xdr:clientData");
        clientData.setAttr("fPrintsWithSheet", image.isPrint() ? 0 : 1);
        return anchor;
    }

    private XNode buildAnchor0(ExcelClientAnchor anchor) {
        XNode node = XNode.make("xdr:twoCellAnchor");
        node.setAttr("editAs", anchor.getType());

        XNode from = XNode.make("xdr:from");
        from.addChild("xdr:col").content(anchor.getCol1());
        from.addChild("xdr:colOff").content(anchor.getDx1());
        from.addChild("xdr:row").content(anchor.getRow1());
        from.addChild("xdr:rowOff").content(anchor.getDy1());

        node.appendChild(from);

        XNode to = XNode.make("xdr:to");
        to.addChild("xdr:col").content(anchor.getCol2());
        to.addChild("xdr:colOff").content(anchor.getDx2());
        to.addChild("xdr:row").content(anchor.getRow2());
        to.addChild("xdr:rowOff").content(anchor.getDy2());
        node.appendChild(to);
        return node;
    }
}