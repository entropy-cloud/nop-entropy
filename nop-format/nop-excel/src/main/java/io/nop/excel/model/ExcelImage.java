/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.model;

import io.nop.api.core.convert.IByteArrayView;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;
import io.nop.excel.model._gen._ExcelImage;

public class ExcelImage extends _ExcelImage {
    /**
     * 嵌入图片的id
     */
    private String embedId;

    private double left;
    private double top;
    private double width;
    private double height;

    public ExcelImage() {

    }

    public String getMimeType() {
        String imgType = getImgType();
        if (StringHelper.isEmpty(imgType))
            return null;

        if (imgType.equals("jpg"))
            return "image/jpeg";

        if (imgType.equals("svg"))
            return "image/svg+xml";

        return "image/" + imgType;
    }

    public double getLeft() {
        return left;
    }

    public void setLeft(double left) {
        this.left = left;
    }

    public double getTop() {
        return top;
    }

    public ExcelClientAnchor makeAnchor() {
        ExcelClientAnchor anchor = getAnchor();
        if (anchor == null) {
            anchor = new ExcelClientAnchor();
            setAnchor(anchor);
        }
        return anchor;
    }

    public void setDataBytes(byte[] bytes) {
        this.setData(ByteString.of(bytes));
    }

    public ExcelImage data(Object data) {
        if (data instanceof ByteString) {
            setData((ByteString) data);
        } else if (data instanceof byte[]) {
            setDataBytes((byte[]) data);
        } else if (data instanceof IByteArrayView) {
            setDataBytes(((IByteArrayView) data).toByteArray());
        }
        return this;
    }

    public void calcSize(IExcelSheet sheet) {
        ExcelClientAnchor anchor = makeAnchor();
        left = sheet.getCellLeft(anchor.getCol1()) + anchor.getDx1();
        top = sheet.getCellTop(anchor.getRow1()) + anchor.getDy1();

        double x2 = sheet.getCellLeft(anchor.getCol2()) + anchor.getDx2();
        double y2 = sheet.getCellTop(anchor.getRow2()) + anchor.getDy2();

        width = x2 - left;
        height = y2 - top;
    }

    public void setTop(double top) {
        this.top = top;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getEmbedId() {
        return embedId;
    }

    public void setEmbedId(String embedId) {
        this.embedId = embedId;
    }
}
