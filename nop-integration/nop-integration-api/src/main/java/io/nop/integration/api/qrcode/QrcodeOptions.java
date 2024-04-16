package io.nop.integration.api.qrcode;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class QrcodeOptions {
    /**
     * 对应于 com.google.zxing.qrcode.decoder.ErrorCorrectionLevel中的值
     */
    public static final int ERROR_CORRECTION_L = 1;
    public static final int ERROR_CORRECTION_M = 0;

    public static final int ERROR_CORRECTION_Q = 3;

    public static final int ERROR_CORRECTION_H = 2;

    private String imgType = "png";

    private String content;

    private String barcodeFormat;

    private int margin;

    private String encoding = "UTF-8";

    /**
     * 二维码宽度
     */
    private double width;

    private double height;

    private int errorCorrection = ERROR_CORRECTION_M;

    public String getImgType() {
        return imgType;
    }

    public void setImgType(String imgType) {
        this.imgType = imgType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBarcodeFormat() {
        return barcodeFormat;
    }

    public void setBarcodeFormat(String barcodeFormat) {
        this.barcodeFormat = barcodeFormat;
    }

    public int getMargin() {
        return margin;
    }

    public void setMargin(int margin) {
        this.margin = margin;
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

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getErrorCorrection() {
        return errorCorrection;
    }

    public void setErrorCorrection(int errorCorrection) {
        this.errorCorrection = errorCorrection;
    }
}
