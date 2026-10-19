package io.nop.pdf.core;

import java.io.File;

/**
 * WI12：盖章/水印/页眉页脚文本的定位与样式参数（坐标为 PDF 点，左下原点）。
 * null 字段走各 API 的缺省裁定（水印：45°/opacity 0.25/居中/36pt；页码：页脚居中 y=20；
 * 页眉 y=pageHeight-20；文本字号 12pt；颜色黑色；图片宽高=像素/72pt）。
 */
public class PdfStampOptions {
    private Float x;
    private Float y;
    private Float degrees;
    private Float opacity;
    private Float fontSize;
    private String fontColor;
    private File fontFile;
    private Float imageWidth;
    private Float imageHeight;

    public Float getX() {
        return x;
    }

    public PdfStampOptions setX(Float x) {
        this.x = x;
        return this;
    }

    public Float getY() {
        return y;
    }

    public PdfStampOptions setY(Float y) {
        this.y = y;
        return this;
    }

    /**
     * 逆时针旋转角度（度）
     */
    public Float getDegrees() {
        return degrees;
    }

    public PdfStampOptions setDegrees(Float degrees) {
        this.degrees = degrees;
        return this;
    }

    /**
     * 不透明度 0-1（1=不透明）
     */
    public Float getOpacity() {
        return opacity;
    }

    public PdfStampOptions setOpacity(Float opacity) {
        this.opacity = opacity;
        return this;
    }

    public Float getFontSize() {
        return fontSize;
    }

    public PdfStampOptions setFontSize(Float fontSize) {
        this.fontSize = fontSize;
        return this;
    }

    /**
     * 文本颜色 hex（RRGGBB 或 #RRGGBB），null=黑色
     */
    public String getFontColor() {
        return fontColor;
    }

    public PdfStampOptions setFontColor(String fontColor) {
        this.fontColor = fontColor;
        return this;
    }

    /**
     * TTF/OTF 字体文件（CJK 等非缺省字体字符必需）；在目标文档上按次加载，禁止跨文档缓存
     */
    public File getFontFile() {
        return fontFile;
    }

    public PdfStampOptions setFontFile(File fontFile) {
        this.fontFile = fontFile;
        return this;
    }

    public Float getImageWidth() {
        return imageWidth;
    }

    public PdfStampOptions setImageWidth(Float imageWidth) {
        this.imageWidth = imageWidth;
        return this;
    }

    public Float getImageHeight() {
        return imageHeight;
    }

    public PdfStampOptions setImageHeight(Float imageHeight) {
        this.imageHeight = imageHeight;
        return this;
    }
}
