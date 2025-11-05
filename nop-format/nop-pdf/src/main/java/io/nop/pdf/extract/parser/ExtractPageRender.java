package io.nop.pdf.extract.parser;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;

import io.nop.pdf.extract.ResourceParseConfig;

/**
 * 自定义PDFRenderer
 *
 */
public class ExtractPageRender extends PDFRenderer {
    
    /**
     * callback
     */
    private IExtractStripperCallback stripperListener;
    private ResourceParseConfig config;
    
    public ExtractPageRender( IExtractStripperCallback builder, PDDocument document,ResourceParseConfig config) {
        
        super( document );
        
        this.stripperListener = builder;
        this.config = config;
    }
    
    @Override
    protected PageDrawer createPageDrawer( PageDrawerParameters parameters ) throws IOException {

        int pageIndex = this.document.getPages().indexOf( parameters.getPage() );
        return new ExtractPageDrawer( pageIndex, this.stripperListener, parameters,  config);
    }
}