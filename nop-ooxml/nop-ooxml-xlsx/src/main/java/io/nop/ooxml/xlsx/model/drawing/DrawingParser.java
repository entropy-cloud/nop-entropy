package io.nop.ooxml.xlsx.model.drawing;

import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.constants.ExcelAnchorType;
import io.nop.excel.util.UnitsHelper;

import java.util.List;

import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrEnumValue;

/**
 * -  <xdr:wsDr> 是一个命名空间声明，指定了XML片段中使用的命名空间。
 * -  <xdr:twoCellAnchor> 是一个单元格锚定的元素，表示图片在电子表格中的位置。 editAs 属性设置为"oneCell"，表示将整个图片作为一个单元格处理。
 * -  <xdr:from> 标签表示起始单元格的位置，其中 <xdr:col> 表示列索引， <xdr:row> 表示行索引。
 * -  <xdr:to> 标签表示结束单元格的位置，其中 <xdr:col> 表示列索引， <xdr:row> 表示行索引。
 * -  <xdr:pic> 包含有关图片的信息。
 * -  <xdr:nvPicPr> 包含有关图片的非可视化属性。
 * -  <xdr:cNvPr> 指定图片的非可视化属性，如id和名称。
 * -  <xdr:cNvPicPr> 包含有关图片的非可视化图像属性，如图像锁定。
 * -  <xdr:blipFill> 指定图片的填充属性。
 * -  <a:blip> 指定图片的图像数据，使用 r:embed 属性指定了图片的关联ID。
 * -  <a:stretch> 指定图片的拉伸属性。
 * -  <xdr:spPr> 指定图片的形状属性，如位置和尺寸。
 * -  <a:xfrm> 指定图片的转换属性，如偏移量和扩展。
 * -  <a:prstGeom> 指定图片的预设几何属性，如形状类型。
 * -  <xdr:clientData> 包含有关图片的客户端数据。
 */
public class DrawingParser {
    public List<ExcelImage> parseDrawing(XNode node) {
        KeyedList<ExcelImage> ret = new KeyedList<>(ExcelImage::getName);

        for (XNode child : node.getChildren()) {
            if (child.getTagName().equals("xdr:twoCellAnchor")) {
                ExcelImage image = parseAnchor(child);
                // name重复，需要重命名
                while (ret.getByKey(image.getName()) != null) {
                    image.setName(StringHelper.nextName(image.getName()));
                }
                ret.add(image);
            }
        }
        return ret;
    }


    public ExcelImage parseAnchor(XNode anchorNode) {
        XNode clientData = anchorNode.childByTag("xdr:clientData");
        XNode pic = anchorNode.childByTag("xdr:pic");
        XNode picPr = pic.childByTag("xdr:nvPicPr");
        XNode cNvPr = picPr != null ? picPr.childByTag("xdr:cNvPr") : null;
        XNode cNvPicPr = picPr != null ? picPr.childByTag("xdr:cNvPicPr") : null;
        XNode picLocks = cNvPicPr != null ? cNvPicPr.childByTag("a:picLocks") : null;

        XNode blipFill = pic.childByTag("xdr:blipFill");
        XNode blip = blipFill != null ? blipFill.childByTag("a:blip") : null;

        XNode spPr = pic.childByTag("xdr:spPr");
        XNode xfrm = spPr != null ? spPr.childByTag("a:xfrm") : null;

        ExcelImage image = new ExcelImage();
        ExcelClientAnchor anchor = parseAnchor0(anchorNode);
        image.setAnchor(anchor);

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

    ExcelClientAnchor parseAnchor0(XNode anchor) {
        ExcelAnchorType anchorType = parseAttrEnumValue(anchor, "editAs", ExcelAnchorType.class);

        XNode from = anchor.childByTag("xdr:from");
        int col1 = from.childByTag("xdr:col").contentAsInt(0);
        int row1 = from.childByTag("xdr:row").contentAsInt(0);
        int colOff1 = from.childByTag("xdr:colOff").contentAsInt(0);
        int rowOff1 = from.childByTag("xdr:rowOff").contentAsInt(0);

        XNode to = anchor.childByTag("xdr:to");
        int col2 = to.childByTag("xdr:col").contentAsInt(0);
        int row2 = to.childByTag("xdr:row").contentAsInt(0);
        int colOff2 = to.childByTag("xdr:colOff").contentAsInt(0);
        int rowOff2 = to.childByTag("xdr:rowOff").contentAsInt(0);

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
