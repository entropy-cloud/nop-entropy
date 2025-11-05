package io.nop.pdf.extract.parser;

import java.util.ArrayList;
import java.util.List;

import io.nop.pdf.extract.ITocDetector;
import io.nop.pdf.extract.processor.StringProcessor;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.pdf.extract.struct.TocItem;
import io.nop.pdf.extract.struct.TocTable;

/**
 * 最简单的目录检测实现类，支持的目录结构形式为：
 * 
 *   目录
 *   
 *   章节标题1............1
 *   章节标题2............3
 *   章节标题3............5
 *   章节标题4............7
 *   
 *   正文起始标识
 */
public class SimpleTocDetector implements ITocDetector {

    /**
     * 起始符
     */
    private String mStartMarker = null;
    
    /**
     * 结束符
     */
    private String mEndMarker = null;
    
    /**
     * 填充字符
     */
    private char mPaddingChar = '.';
    
    /**
     * 最小的填充字符个数
     */
    private int mMinPaddingCount = 3;
    
    /**
     * 最多搜索文档的前多少页
     */
    private int maxSearchPageNo = 10;
    
    public String getStartMarker() {
        return mStartMarker;
    }

    public void setStartMarker( String startMarker ) {
        this.mStartMarker = startMarker;
    }

    public String getEndMarker() {
        return mEndMarker;
    }

    public void setEndMarker( String endMarker ) {
        this.mEndMarker = endMarker;
    }

    public char getPaddingChar() {
        return mPaddingChar;
    }

    public void setPaddingChar( char paddingChar ) {
        this.mPaddingChar = paddingChar;
    }

    public int getMinPaddingCount() {
        return mMinPaddingCount;
    }

    public void setMinPaddingCount( int minPaddingCount ) {
        
        this.mMinPaddingCount = minPaddingCount;
    }

    @Override
    public TocTable detect( ResourceDocument doc ) {

        List<TextlineBlock> textlines = selectTextlines( doc );
        return this.parseTocFromTextlines( textlines );
    }

    /**
     * 根据起止符选择文本行
     * @param doc
     * @return
     */
    private List<TextlineBlock> selectTextlines( ResourceDocument doc ) {
        
        List<TextlineBlock> selectBlocks = new ArrayList<TextlineBlock>();
        
        boolean startMarkerFound = false;
        boolean endMarkerFound = false;
        boolean exitSelect = false;
        
        List<ResourcePage> pages = doc.getPages();
        
        // 前一页是否是目录页, 用于判断目录页是否接续
        boolean inPageToc = false;
        
        for( ResourcePage page : pages ) {
        	// 假设目录在一定页数范围内必然存在
        	if(page.getPageNo() > maxSearchPageNo)
        		break;
            
            List<Block> blocks = page.getSortedBlocks();
            int invalidCount = 0;
            int candidateCount = 0;
            
            for( Block block : blocks ) {
                
                if( block == null ) continue;
                
                if( block instanceof TextlineBlock ) {
                    TextlineBlock txtblock = (TextlineBlock)block;
                    
                    String text = txtblock.getContent();
                    if( text == null ) continue;
                    
                    StringProcessor sp = new StringProcessor( text );
                    sp.removeWhitespace();
                    text = sp.toString();
                    
                    // 跳过空行
                    if(text.length() <= 0)
                    	continue;
                    
                    if( this.mStartMarker != null &&  startMarkerFound == false ) {
                        
                        if( this.mStartMarker.equalsIgnoreCase( text ) ) {
                            startMarkerFound = true;
                        }
                        continue;
                    }
                    
                    boolean hasRepeat = this.hasRepeatChar(text, mPaddingChar, mMinPaddingCount);
                    // 如果没有指定起始符, 则检测是否发现目录行特定的重复字符
                    if(this.mStartMarker == null){
                    	if(hasRepeat){
                    		candidateCount++;
                    		if(candidateCount > 3 && !inPageToc){
                    			inPageToc = true;
                    		}
                    	}else{
                    		// 发现非法行, 则重置计数器
                    		candidateCount = 0;
                    		invalidCount ++;
                    		// 发现很多行都不是目录, 且前一页是目录, 则终止搜速
                    		if(invalidCount > 8){
                    			if(inPageToc){
                    				exitSelect = true;
                    				break;
                    			}
                    		}
                    	}
                    }
                    
                    if( this.mEndMarker != null && endMarkerFound == false ) {
                        
                        if( this.mEndMarker.equalsIgnoreCase( text ) ) {
                            endMarkerFound = true;
                            exitSelect = true;
                            break;
                        }
                    }
                    
                    if(hasRepeat)
                    	selectBlocks.add( txtblock );
                }
            }
            if( exitSelect ) break;
        }
        
        return selectBlocks;
    }
    
    /**
     * 从指定的文本行中解析目录表
     * @param textlines
     * @return
     */
    private TocTable parseTocFromTextlines( List<TextlineBlock> textlines ) {
        
        List<TocItem> items = new ArrayList<TocItem>();
        
        for( TextlineBlock block : textlines ) {
            
            String text = block.getContent().trim();
            TocItem item = this.parseTocItem( text );
            if( item != null ) {
            	item.setTocPageNo(block.getPageNo());
                items.add( item );
            }
            
            //TODO: 允许目录中有些行不遵守规则
        }
        
        if(items.isEmpty())
        	return null;
        
        TocTable table = new TocTable();
        table.setItems( items );
        table.setFromPageNo(items.get(0).getPageNo());
        table.setToPageNo(items.get(items.size()-1).getPageNo());
        
        return table;
    }
    
    /**
     * 将指定文本解释为目录行
     * @param text
     * @return
     */
    private TocItem parseTocItem( String text ) {
        
        int[] ret = this.getMaxRepeats( text, this.mPaddingChar );
        int paddingPos = ret[0];
        int paddingCnt = ret[1];
        if( paddingCnt < this.mMinPaddingCount ) { 
            return null;
        }
            
        int num = -1;
        try{
            int pos = ret[0] + ret[1];
            String numstr = text.substring( pos );
            num = Integer.parseInt( numstr.trim() );
        }
        catch( Exception ex ) {
            return null;
        }
        
        String title = text.substring( 0, paddingPos );
        
        TocItem item = new TocItem();
        item.setTitle( title );
        item.setPageNo( num );
        
        return item;
    }
    
    private boolean hasRepeatChar(String text, char chr, int minCount){
    	int count = 0;
    	for(int i=0,n=text.length();i<n;i++){
    		char c = text.charAt(i);
    		if(c == chr){
    			count ++;
    			if(count >= minCount)
    				return true;
    		}else{
    			count = 0;
    		}
    	}
    	return false;
    }
    
    /**
     * 搜索字符串内最长的一个由指定字符重复组成的子串
     * @param text
     * @param chr
     * @return
     */
    private int[] getMaxRepeats( String text, char chr ) {
        
        char[] chars = text.toCharArray();
        int n = chars.length;
        
        int max = 0;
        int pos = -1;
        for( int i = 0; i < n; i++ ) {
            
            if( chars[i] != chr ) continue;
            
            for( int j = i + 1; j < n + 1; j++ ) {
                
                if( j == n || chars[j] != chr ) {
                    
                    int len = j - i;
                    if( len > max ) {
                        max = len;
                        pos = i;
                    }
                    i = j - 1;
                    break;
                }
            }
        }
        return new int[] { pos, max };
    }
}
