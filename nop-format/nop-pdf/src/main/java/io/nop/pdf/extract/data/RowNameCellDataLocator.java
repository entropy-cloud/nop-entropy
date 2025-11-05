package io.nop.pdf.extract.data;

import java.util.regex.Pattern;

import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;

/**
 * 表格单元格定位实现类
 * 基于行名称的定位, 可以使用正则表达式或者精确匹配的方式定位表格单元格
 */
public class RowNameCellDataLocator extends AbstractCellDataLocator {

    /**
     * 行名匹配模式
     */
    private String mRowNamePattern = null;
    
    /**
     * 行名所在的列index
     */
    private int mRowNameColIndex = 0;
    
    /**
     * 数据列与行名列的偏移量
     */
    private int mDataColOffset = 1;
    
    /**
     * 是否进行精确匹配
     */
    private boolean mMatchExactly = false;
    
    public RowNameCellDataLocator() {
        
    }
    
    public RowNameCellDataLocator( String key, String rowNamePattern, boolean matchExactly ) {
        
        this.setKey( key );
        this.mRowNamePattern = rowNamePattern;
        this.mMatchExactly = matchExactly;
    }
    
    public String getRowNamePattern() {
        return mRowNamePattern;
    }
    
    public void setRowNamePattern( String rowNamePattern ) {
        this.mRowNamePattern = rowNamePattern;
    }

    public int getRowNameColIndex() {
        return mRowNameColIndex;
    }

    public void setRowNameColIndex( int rowNameColIndex ) {
        this.mRowNameColIndex = rowNameColIndex;
    }

    public int getDataColOffset() {
        return mDataColOffset;
    }

    public void setDataColOffset( int dataColOffset ) {
        this.mDataColOffset = dataColOffset;
    }

    public boolean isMatchExactly() {
        return mMatchExactly;
    }

    public void setMatchExactly( boolean matchExactly ) {
        this.mMatchExactly = matchExactly;
    }
    
    @Override
    public boolean check( CellDataLocatorContext ctx, TableBlock table, TableCellBlock cell ) {
        
        return false;
    }

    @Override
    public TableCellBlock locate( CellDataLocatorContext ctx, TableBlock table ) {

        int rowIndex = this.getRowIndex( ctx, table );
        if( rowIndex < 0 ) return null;

        int colIndex = this.mRowNameColIndex + this.mDataColOffset;
        
        return table.getCell( rowIndex, colIndex );
    }
    
    private int getRowIndex( CellDataLocatorContext ctx, TableBlock table ) {

        for( int i = 0; i < table.getRowCount(); i++ ) {
            
            TableCellBlock cell = table.getCell( i, this.mRowNameColIndex );
            if( cell == null ) {
                continue;
            }
            
            String text = cell.getContent();
            if( text == null ) continue;
            
            text = text.trim();
            
            boolean matched = false;
            if( mMatchExactly ) {
                
                matched = this.mRowNamePattern.equals( text );
            }
            else matched = Pattern.matches( this.mRowNamePattern, text );
            
            if( matched ) return i;
        }
        return -1;
    }
}
