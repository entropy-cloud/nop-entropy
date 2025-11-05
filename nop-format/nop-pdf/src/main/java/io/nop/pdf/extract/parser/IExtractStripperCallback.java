package io.nop.pdf.extract.parser;

import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ShapeBlock;
import io.nop.pdf.extract.struct.TextBlock;

public interface IExtractStripperCallback {
    
    public void onPageBegin( int pageIndex, float pageWidth, float pageHeight, float viewScale );
    public void onPageEnd( int pageIndex );
    
    public void onTextBlockBegin( int pageIndex, TextBlock textBlock );
    public void onCharBlock( int pageIndex, TextBlock charBlock );
    public void onTextBlockEnd( int pageIndex, TextBlock textBlock );
    
    public void onFillShape( int pageIndex, ShapeBlock shapeBlock );
    
    public void onDrawImage( int pageIndex, ImageBlock imageBlock );
}
