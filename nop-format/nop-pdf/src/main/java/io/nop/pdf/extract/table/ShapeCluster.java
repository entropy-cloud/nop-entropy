package io.nop.pdf.extract.table;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import io.nop.pdf.extract.struct.ShapeBlock;

public class ShapeCluster {

    /**
     * 最小外框
     */
    private Rectangle2D bounding = new Rectangle2D.Double();
    
    /**
     * 包含的图形
     */
    private List<ShapeBlock> blocks = new ArrayList<ShapeBlock>();
    
    public Rectangle2D getBounding() {
        
        return this.bounding;
    }
    
    public List<ShapeBlock> getBlocks() {
        
        return this.blocks;
    }
    
    public void add( ShapeBlock block ) {
        
        if( block == null ) return;
        
        this.blocks.add( block );
        
        //注意：必须检测空矩形(0,0,0,0)的情况，否则createUnion会误算
        if( this.bounding.isEmpty() ) {
            this.bounding = (Rectangle2D)block.getViewBounding().clone();
            return;
        }
        
        this.bounding = this.bounding.createUnion( block.getViewBounding() );   
    }
    
    public void add( List<ShapeBlock> blocks ) {
        
        if( blocks == null ) {
            return;
        }
        
        for( int i = 0; i < blocks.size(); i++ ) {
            
            this.add( blocks.get( i ) );
        }
    }
}
