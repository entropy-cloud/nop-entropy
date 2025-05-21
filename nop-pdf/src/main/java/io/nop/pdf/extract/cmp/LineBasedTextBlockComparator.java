package io.nop.pdf.extract.cmp;

import java.awt.geom.Rectangle2D;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.pdf.extract.struct.TextBlock;

/**
 * 
 * 先比较y方向,
 *   block1完全处于block2上方，返回小于
 *   block1完全处于block2下方，返回大于
 * 再比较x方向,
 *   block1的左边界处于block2左边界的左侧，返回小于
 *   block1的左边界处于block2左边界的右侧，返回大于
 */
public class LineBasedTextBlockComparator implements Comparator<TextBlock> {
	static final Logger LOG = LoggerFactory.getLogger(LineBasedTextBlockComparator.class);

    @Override
    public int compare( TextBlock block1, TextBlock block2 ) {

    	int ret = cmp(block1, block2);
    	
    	//if(LOG.isInfoEnabled()){
    	//	LOG.info("compare_result:"+block1+ getOperator(ret) + block2);
    	//}
    	
    	return ret;
    }
    
    String getOperator(int ret){
    	if(ret == 0)
    		return "=";
    	if(ret < 0)
    		return "<";
    	return ">";
    }

    int cmp(TextBlock block1, TextBlock block2){
    	if(block1 == block2)
    		return 0;
    	Rectangle2D rect1 = block1.getViewBounding();
        Rectangle2D rect2 = block2.getViewBounding();
        
        if( rect1.getMinY() + rect1.getHeight()/2 <= rect2.getMinY() ) return -1;
        if( rect1.getMinY() >= rect2.getMinY() + rect2.getHeight()/2 ) return 1;
        
        if( rect1.getMinX() < rect2.getMinX() ) return -1;
        if( rect1.getMinX() > rect2.getMinX() ) return  1;
        
        return 0;
    }
}
