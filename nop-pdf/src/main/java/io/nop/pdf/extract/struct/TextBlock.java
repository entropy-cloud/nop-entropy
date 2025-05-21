package io.nop.pdf.extract.struct;

import java.util.ArrayList;
import java.util.List;


public class TextBlock extends Block {
    
   
    /**
     * 文本内容
     */
    private String text;
    
    /**
     * 在视图中显示的字体尺寸
     */
    private float viewFontSize;
    
    /**
     * 组成文本的字符的数据
     */
    private List<TextBlock> charBlocks;
    
    public String toString(){
    	return "T["+text+"]@"+this.getViewBounding()+"#"+this.getPageBlockIndex();
    }
 
    public String getText()
    {
        return text;
    }

    public void setText( String text )
    {
        this.text = text;
    }

    public float getViewFontSize()
    {
        return viewFontSize;
    }

    public void setViewFontSize( float viewFontSize )
    {
        this.viewFontSize = viewFontSize;
    }

    public List<TextBlock> getCharBlocks()
    {
        return charBlocks;
    }

    public void setCharBlocks( List<TextBlock> charBlocks )
    {
        this.charBlocks = charBlocks;
    }
    
    public void addCharBlock( TextBlock block ) {
        
        if( this.charBlocks == null ) {
            
            this.charBlocks = new ArrayList<TextBlock>();
        }
        
        this.charBlocks.add( block );
    }
}
