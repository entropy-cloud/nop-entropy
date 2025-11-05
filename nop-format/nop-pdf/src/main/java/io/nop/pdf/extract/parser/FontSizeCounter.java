package io.nop.pdf.extract.parser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 字号统计, 根据一组字符的字号统计出一个合理的字号
 */
public class FontSizeCounter {

    private Map<Integer,Integer> counters = new HashMap<Integer,Integer>();
    
    /**
     * 最大字号
     */
    private int mFontSizeMax = 0;
    
    /**
     * 最小字号
     */
    private int mFontSizeMin = 0;
    
    /**
     * 出现次数最多的字号
     */
    private int mMajorFontSize = 0;
    
    /**
     * 统计是否有效
     */
    private boolean mStatValid = false;
    
    /**
     * 记录一次字号引用
     * @param fontSize
     */
    public void onFontSize( double fontSize ) {
        
        mStatValid = false;
        
        int size =(int)( fontSize + 0.5 );
        
        if( this.counters.containsKey( size ) ) {
            int cnt = this.counters.get( size );
            this.counters.put( size, cnt + 1 );
        }
        else this.counters.put( size, 1 );
    }
    
    /**
     * 统计并返回最合理的字号
     * @return
     */
    public int getFontSize() {
        
        if( !mStatValid ) this.exec();
        
        //优先使用出现次数最多的字号
        if( this.mMajorFontSize > 0 ) {
            return this.mMajorFontSize;
        }
        
        return this.mFontSizeMax;
    }
    
    /**
     * 统计并返回最大的字号
     * @return
     */
    public int getFontSizeMax() {
        
        if( !mStatValid ) this.exec();
        
        return this.mFontSizeMax;
    }
    
    /**
     * 统计并返回最小的字号
     * @return
     */
    public int getFontSizeMin() {
        
        if( !mStatValid ) this.exec();
        
        return  this.mFontSizeMin;
    }
    
    /**
     * 执行统计
     */
    private void exec() {
        
        this.mFontSizeMax = 0;
        this.mFontSizeMin = 0;
        this.mMajorFontSize = 0;
        
        int majorFontSizeCount = 0;
        int majorFontSize = 0;
        
        int maxFontSize = 0;
        int minFontSize = Integer.MAX_VALUE;
        
        Iterator<Integer> iterator = this.counters.keySet().iterator();
        while( iterator.hasNext() ) {
            
            int key = iterator.next();
            int count = this.counters.get( key );
            
            if( count > majorFontSizeCount ) {
                majorFontSizeCount = count;
            }
            
            if( key > maxFontSize ) maxFontSize = key;
            if( key < minFontSize ) minFontSize = key;
        }
        
        if( maxFontSize != 0 ) this.mFontSizeMax = maxFontSize;
        if( minFontSize != Integer.MAX_VALUE ) this.mFontSizeMin = minFontSize;

        if( majorFontSizeCount > this.counters.size() * 0.333 ) {
            
            //取多数中的最大字号
            iterator = this.counters.keySet().iterator();
            while( iterator.hasNext() ) {
                
                int fntsize = iterator.next();
                int count = this.counters.get( fntsize );
                if( count == majorFontSizeCount ) {
                    
                    if( fntsize > majorFontSize ) majorFontSize = fntsize;
                }
            }
        }
        
        if( majorFontSize != 0 ) this.mMajorFontSize = majorFontSize;
        
        mStatValid = true;
    }
}
