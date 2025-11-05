package io.nop.pdf.extract.parser;

import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.struct.TextBlock;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType3Font;
import org.apache.pdfbox.pdmodel.interactive.pagenavigation.PDThreadBead;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

/**
 * PDF文本，图形，图像资源提取
 */
public class ExtractStripper extends PDFTextStripper {

    private boolean enableBeadDebug = false;
    private boolean enableImageDebug = false;

    private IExtractStripperCallback stripperCallback = null;

    private int curPageIndex;
    private BufferedImage image;
    private AffineTransform flipAT;
    private AffineTransform rotateAT;
    private AffineTransform transAT;

    /**
     * 坐标系缩放比例
     */
    private float scale; // config.getImageScale()

    /**
     * 临时Graphics2D
     */
    private Graphics2D g2d;

    private ResourceParseConfig config;

    public ExtractStripper(IExtractStripperCallback stripperCallback, ResourceParseConfig config) throws IOException {

        super();
        this.stripperCallback = stripperCallback;
        this.setSortByPosition(true);
        this.config = config;
        this.scale = config.getImageScale();
    }

    /**
     * 提取指定页码的资源
     *
     * @param document
     * @param pageNo
     * @param pageImgFile
     * @throws IOException
     */
    public void stripPage(PDDocument document, int pageNo, String pageImgFile) throws IOException {

        curPageIndex = pageNo - 1;

        int pageIndex = pageNo - 1;
        PDPage pdPage = document.getPage(pageIndex);
        PDRectangle cropBox = pdPage.getCropBox();

        this.stripperCallback.onPageBegin(this.curPageIndex, cropBox.getWidth(), cropBox.getHeight(), scale);

        PDFRenderer pdfRenderer = new ExtractPageRender(this.stripperCallback, document, config);
        image = pdfRenderer.renderImage(pageIndex, scale);

        // flip y-axis
        flipAT = new AffineTransform();
        flipAT.translate(0, pdPage.getBBox().getHeight());
        flipAT.scale(1, -1);

        // page may be rotated
        rotateAT = new AffineTransform();
        int rotation = pdPage.getRotation();
        if (rotation != 0) {

            PDRectangle mediaBox = pdPage.getMediaBox();
            switch (rotation) {
                case 90:
                    rotateAT.translate(mediaBox.getHeight(), 0);
                    break;
                case 270:
                    rotateAT.translate(0, mediaBox.getWidth());
                    break;
                case 180:
                    rotateAT.translate(mediaBox.getWidth(), mediaBox.getHeight());
                    break;
                default:
                    break;
            }
            rotateAT.rotate(Math.toRadians(rotation));
        }

        // cropbox
        transAT = AffineTransform.getTranslateInstance(-cropBox.getLowerLeftX(), cropBox.getLowerLeftY());

        g2d = image.createGraphics();
        g2d.setStroke(new BasicStroke(0.1f));
        g2d.scale(scale, scale);

        setStartPage(pageNo);
        setEndPage(pageNo);

        Writer dummy = new StringWriter();
        writeText(document, dummy);

        if (this.enableBeadDebug) {

            g2d.setStroke(new BasicStroke(0.4f));
            List<PDThreadBead> pageArticles = pdPage.getThreadBeads();
            for (PDThreadBead bead : pageArticles) {
                PDRectangle r = bead.getRectangle();
                Shape s = r.toGeneralPath().createTransformedShape(transAT);
                s = flipAT.createTransformedShape(s);
                s = rotateAT.createTransformedShape(s);
                g2d.setColor(Color.green);
                g2d.draw(s);
            }
        }

        g2d.dispose();
        g2d = null;

        if (enableImageDebug && pageImgFile != null) {
            ImageIO.write(image, "png", new File(pageImgFile));
        }

        this.stripperCallback.onPageEnd(this.curPageIndex);
    }

    protected void writeWordSeparator() throws IOException {
        this.output.write(this.getWordSeparator());
    }

    /**
     * Override the default functionality of PDFTextStripper.
     */
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        TextBlock block = new TextBlock();
        // block.setId( LocalSequence.next() );
        block.setPageNo(this.curPageIndex + 1);
        block.setText(string + " ");

        this.stripperCallback.onTextBlockBegin(this.curPageIndex, block);

        for (int i = 0, n = textPositions.size(); i < n; i++) {
            TextPosition text = textPositions.get(i);
            // glyph space -> user space
            // note: text.getTextMatrix() is *not* the Text Matrix, it's the Text Rendering Matrix
            AffineTransform at = text.getTextMatrix().createAffineTransform();

            // show rectangle with the real vertical bounds, based on the font bounding box y values
            // usually, the height is identical to what you see when marking text in Adobe Reader
            PDFont font = text.getFont();
            BoundingBox bbox = font.getBoundingBox();

            // advance width, bbox height (glyph space)
            float xadvance = font.getWidth(text.getCharacterCodes()[0]); // todo: should iterate all chars
            Rectangle2D.Float rect = new Rectangle2D.Float(0, bbox.getLowerLeftY(), xadvance, bbox.getHeight());

            if (font instanceof PDType3Font) {
                // bbox and font matrix are unscaled
                at.concatenate(font.getFontMatrix().createAffineTransform());
            } else {
                // bbox and font matrix are already scaled to 1000
                at.scale(1 / 1000f, 1 / 1000f);
            }
            Shape s = at.createTransformedShape(rect);
            s = flipAT.createTransformedShape(s);
            s = rotateAT.createTransformedShape(s);

            g2d.setColor(Color.blue);
            g2d.draw(s);

            s = g2d.getTransform().createTransformedShape(s);
            Rectangle2D cbounding = s.getBounds2D();

            TextBlock cb = new TextBlock();
            // cb.setId( LocalSequence.next() );
            if(i == n-1){
                cb.setText(text.getUnicode()+" ");
            }else {
                cb.setText(text.getUnicode());
            }
            cb.setPageNo(curPageIndex + 1);
            cb.setViewBounding(cbounding);
            cb.setViewFontSize(text.getFontSizeInPt() * this.scale);

            block.addCharBlock(cb);
            block.increateViewBounding(cbounding);

            this.stripperCallback.onCharBlock(this.curPageIndex, cb);
        }
        this.stripperCallback.onTextBlockEnd(this.curPageIndex, block);
    }
}
