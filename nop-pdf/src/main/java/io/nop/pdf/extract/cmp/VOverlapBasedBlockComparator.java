package io.nop.pdf.extract.cmp;

import java.awt.geom.Rectangle2D;
import java.util.Comparator;

import io.nop.pdf.extract.struct.Block;

public class VOverlapBasedBlockComparator implements Comparator<Block>{

    private double minOverlapRatio = 0.2;
    
    public VOverlapBasedBlockComparator() {
        
    }
    
    public VOverlapBasedBlockComparator( double minOverlapRatio ) {
        
        this.minOverlapRatio = minOverlapRatio;
    }
    
    public double getMinOverlapRatio() {
        return minOverlapRatio;
    }

    public void setMinOverlapRatio( double minOverlapRatio ) {
        this.minOverlapRatio = minOverlapRatio;
    }
    
    @Override
    public int compare( Block block1, Block block2 ) {

        Rectangle2D rect1 = block1.getViewBounding();
        Rectangle2D rect2 = block2.getViewBounding();
        
        //处理y方向完全不重叠的情况
        if( rect1.getMaxY() < rect2.getMinY() ) return -1;
        if( rect1.getMinY() > rect2.getMaxY() ) return 1;
        
        //计算y方向的重叠
        Rectangle2D r1 = new Rectangle2D.Double( 0, rect1.getMinX(), 100, rect1.getHeight() );
        Rectangle2D r2 = new Rectangle2D.Double( 0, rect2.getMinX(), 100, rect2.getHeight() );
        
        Rectangle2D overlap = new Rectangle2D.Double();
        Rectangle2D.intersect( r1, r2, overlap );
        
        double overlapHeight = overlap.getHeight();
        
        double ratio1 = overlapHeight / rect1.getHeight();
        double ratio2 = overlapHeight / rect2.getHeight();
        
        //y方向重叠较小
        if( ratio1 < minOverlapRatio && ratio2 < minOverlapRatio ) {
        
            if( rect1.getMinY() < rect2.getMinY() ) return -1;
            if( rect1.getMinY() > rect2.getMinY() ) return  1;
            return 0;
        }
        
        //根据x排序
        if( rect1.getMinX() < rect2.getMinX() ) return -1;
        if( rect1.getMinX() > rect2.getMinX() ) return  1;        
        return 0;
    }

}
