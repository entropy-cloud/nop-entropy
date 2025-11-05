package io.nop.pdf.extract.parser;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.util.Matrix;

import io.nop.pdf.extract.IResourceImageHandler;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ShapeBlock;

/**
 * 自定义PageDrawer，主要用途为收集页内的图形对象和图片对象
 * 
 */
public class ExtractPageDrawer extends PageDrawer {

    private int pageIndex = -1;

    /**
     * callback
     */
    private IExtractStripperCallback stripperListener;

    private ResourceParseConfig config;

    public ExtractPageDrawer(int pageIndex, IExtractStripperCallback listener,
            PageDrawerParameters parameters, ResourceParseConfig config)
            throws IOException {

        super(parameters);

        this.pageIndex = pageIndex;
        this.stripperListener = listener;
        this.config = config;
    }

    @Override
    public void fillPath(int windingRule) throws IOException {
        Shape bbox = getLinePath().getBounds2D();

        Shape shape = this.getGraphics().getTransform().createTransformedShape(bbox);

        ShapeBlock block = new ShapeBlock();
        //block.setId(LocalSequence.next());
        block.setPageNo(this.pageIndex + 1);
        block.setViewBounding(shape.getBounds2D());
        this.stripperListener.onFillShape(this.pageIndex, block);

        super.fillPath(windingRule);
    }
    
    @Override
    public void strokePath() throws IOException {
        
        Rectangle2D bbox = getLinePath().getBounds2D();
        double w = bbox.getWidth();
        double h = bbox.getHeight();
        if (w < 0.25 ) w = 0.25;
        if (h < 0.25 ) h = 0.25;
        bbox.setRect( bbox.getMinX(), bbox.getMinY(), w, h );
        
        Shape shape = this.getGraphics().getTransform().createTransformedShape( bbox );
        
        ShapeBlock block = new ShapeBlock();
        //block.setId( LocalSequence.next() );
        block.setPageNo( this.pageIndex + 1 );
        block.setViewBounding( shape.getBounds2D() );
        
        this.stripperListener.onFillShape( this.pageIndex,  block );
        
        super.strokePath();
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {

        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        AffineTransform at = ctm.createAffineTransform();

        if (pdImage.isStencil()) {

            BufferedImage image = pdImage
                    .getStencilImage(getPaint(getGraphicsState()
                            .getNonStrokingColor()));
            drawBufferedImage(image, at);
        } else {
            drawBufferedImage(pdImage.getImage(), at);
        }
    }

    private void drawBufferedImage(BufferedImage image, AffineTransform at) {

        Rectangle2D unitRect = new Rectangle2D.Float(0.0F, 0.0F, 1.0F, 1.0F);
        Shape shape = at.createTransformedShape(unitRect);

        Shape shape2 = this.getGraphics().getTransform().createTransformedShape( shape );
        Rectangle2D rect = shape2.getBounds2D();
        
        BufferedImage bi = this.procImage( image );
        
        ImageBlock block = new ImageBlock();
        block.setWidth( bi.getWidth() );
        block.setHeight( bi.getHeight() );
        block.setViewBounding( rect );
        
        if( block.getHeight() * block.getWidth() > config.getMinExportImageArea() ){
            IResourceImageHandler.IResourceImageReference imageRef = 
                config.getImageHandler().storeImage( config.getWorkDir(), bi );
            block.setReference( imageRef );
        }
        
        this.stripperListener.onDrawImage( this.pageIndex, block );
    }
    
    @Override
    protected void showText(byte[] string) throws IOException{
    	if(config.isDrawText())
    		super.showText(string);
    }
    
    
    /**
     * 处理图片
     * 
     * @param src
     * @return
     */
    private BufferedImage procImage( BufferedImage src ) {
        
        if( src == null ) return null;
        
        int srcw = src.getWidth();
        int srch = src.getHeight();
        int area = srcw * srch;
        
        boolean resizing = this.config.isResizeImageEnabled() && ( area > this.config.getResizeImagePixels() );
        boolean graying  = this.config.isGrayImageEnabled() && ( area > this.config.getGrayImagePixels() );
        
        if( resizing ) {
            return this.resizeImage( src, this.config.getResizeImagePixels(), graying );
        }
        
        if( graying ) {
            return this.convToGrayImage( src );
        }
        
        return src;
    }
    /**
     * 缩小图片
     * 
     * @param src
     * @param maxPixels
     * @return
     */
    private BufferedImage resizeImage( BufferedImage src, int maxPixels, boolean toGrayImage ) {

        if( src == null ) return null;

        int w = src.getWidth();
        int h = src.getHeight();

        if( w == 0 || h == 0 ) {
            return src;
        }

        if( w * h < maxPixels ) {
            return src;
        }

        double scale = Math.sqrt( (double)( w * h )/ maxPixels );

        int neww = (int)( w / scale + 0.5 );
        int newh = (int) (h / scale + 0.5 );
        if( neww < 1 ) neww = 1;
        if( newh < 1 ) newh = 1;

        BufferedImage bi = new BufferedImage( neww, newh, BufferedImage.TYPE_BYTE_GRAY );
        
        Graphics2D g = bi.createGraphics();
        g.setComposite( AlphaComposite.Src );
        g.setRenderingHint( RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR );
        g.setRenderingHint( RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.drawImage( src, 0, 0, neww, newh, null );
        g.dispose();
        
        return bi;
    }
    
    /**
     * 灰度化图片
     * @param src
     * @return
     */
    private BufferedImage convToGrayImage( BufferedImage src ) {
        
        int srcw = src.getWidth();
        int srch = src.getHeight();
        
        BufferedImage bi = new BufferedImage( srcw, srch, BufferedImage.TYPE_BYTE_GRAY );
        
        Graphics2D g = bi.createGraphics();
        
        g.setComposite( AlphaComposite.Src );
        g.setRenderingHint( RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR );
        g.setRenderingHint( RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.drawImage( src, 0, 0, null );
        
        g.dispose();
        
        return bi;
    }
}
