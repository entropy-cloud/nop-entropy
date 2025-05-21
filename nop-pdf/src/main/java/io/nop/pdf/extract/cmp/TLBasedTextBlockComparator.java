package io.nop.pdf.extract.cmp;

import java.util.Comparator;

import io.nop.pdf.extract.struct.TextBlock;

/**
 * 
 * 先比较Top，直接按数值比较
 * 再比较Left，直接按数值比较
 */
public class TLBasedTextBlockComparator implements Comparator<TextBlock> {

    @Override
    public int compare( TextBlock o1, TextBlock o2 ) {

        if( o1.getViewBounding().getMinY() < o2.getViewBounding().getMinY() ) return -1;
        if( o1.getViewBounding().getMinY() > o2.getViewBounding().getMinY() ) return 1;
        
        if( o1.getViewBounding().getMinX() < o2.getViewBounding().getMinX() ) return -1;
        if( o1.getViewBounding().getMinX() > o2.getViewBounding().getMinX() ) return 1;
        
        return 0;
    }

}
