package io.nop.pdf.extract.cmp;

import java.util.Comparator;

import io.nop.pdf.extract.struct.TextBlock;

/**
 * 
 * 先比较Top，两个对象的top差绝对值小于maxYDiff，认为相等
 * 再比较Left，直接按数值比较
 */
public class YDiffBasedTextBlockComparator implements Comparator<TextBlock> {

    private double maxYDiff = 1;
    
    public YDiffBasedTextBlockComparator( double maxYDiff ) {
        
        this.maxYDiff = maxYDiff;
    }
    
    @Override
    public int compare( TextBlock o1, TextBlock o2 ) {

        double dy = o1.getViewBounding().getMinY() - o2.getViewBounding().getMinY();

        if( dy < 0 - maxYDiff ) return -1;
        if( dy > maxYDiff ) return 1;
        
        double dx = o1.getViewBounding().getMinX() - o2.getViewBounding().getMinX();
        
        if( dx < 0 ) return -1;
        if( dx > 0 ) return 1;
        
        return 0;
    }

}
