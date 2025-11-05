package io.nop.pdf.extract.data;

import java.util.ArrayList;
import java.util.List;

import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import io.nop.commons.util.StringHelper;

/**
 * 表格单元格定位实现类
 * 
 * 基于行列路径定位单元格
 * 路径使用/分割，*表示任意一级，**表示任意多级
 * 行路径由第一列第一行往下描述
 * 列路径由第n列第一行往下描述
 */
public class RCPathCellDataLocator extends AbstractCellDataLocator {

    private static final String VAR_FLAT_TABLE = "flat-table";
    
    private String mRowPathPattern;

    private String mColPathPattern;

    private List<String> mRowPathPatternElements = new ArrayList<String>();

    private List<String> mColPathPatternElements = new ArrayList<String>();
    
    private WildcardPathMatcher mPathMatcher = new WildcardPathMatcher();

    public RCPathCellDataLocator() {
    }
    
    public RCPathCellDataLocator( String key, String rowPath, String colPath ) {
        
        this.setKey( key );
        
        this.mRowPathPattern = rowPath;
        this.mColPathPattern = colPath;
     
        this.updatePathElements( rowPath, this.mRowPathPatternElements );
        this.updatePathElements( colPath, this.mColPathPatternElements );
    }
    
    public String getRowPath() {

        return mRowPathPattern;
    }

    public void setRowPath( String rowPath ) {
        
        this.mRowPathPattern = rowPath;
    }

    public String getColPath() {
        return mColPathPattern;
    }

    public void setColPath( String colPath ) {
        this.mColPathPattern = colPath;
    }
    
    public List<String> getRowPathElements() {
        return mRowPathPatternElements;
    }

    public void setRowPathElements( List<String> rowPathElements ) {
        this.mRowPathPatternElements = rowPathElements;
    }
    
    public List<String> getColPathElements() {
        return mColPathPatternElements;
    }

    public void setColPathElements( List<String> colPathElements ) {
        this.mColPathPatternElements = colPathElements;
    }
    
    @Override
    public boolean check( CellDataLocatorContext ctx, TableBlock table, TableCellBlock cell ) {

        return false;
    }
    
    @Override
    public TableCellBlock locate( CellDataLocatorContext ctx, TableBlock table ) {

        int rowIndex = this.findRowIndex( ctx, table );
        int colIndex = this.findColIndex( ctx, table );

        if( rowIndex < 0  && rowIndex >= table.getRowCount() ) {
            return null;
        }

        if( colIndex < 0  && colIndex >= table.getColCount() ) {
            return null;
        }
        
        return table.getCell( rowIndex, colIndex );
    }

    private void updatePathElements( String path, List<String> elements ) {
        
        elements.clear();
        
        if( path.startsWith( WildcardPathMatcher.PATH_SEP ) ) {
            
            elements.add( WildcardPathMatcher.WILDCARD_BEFORE_FIRST );
            path = path.substring( 1 );
        }
        else {
            elements.add( WildcardPathMatcher.WILDCARD_ANYLEVELS );
        }
        
        String[] items = path.split( WildcardPathMatcher.PATH_SEP );
        
        for( String str : items ) {
            
            if( str == null ) str = "";
            else str = str.trim();
            
            elements.add( str );
        }
    }
    
    private String[][] getFlatTable( CellDataLocatorContext ctx, TableBlock table ) {
        
        Object val = ctx.get( VAR_FLAT_TABLE );
        
        if( val instanceof String[][] ) {
            return (String[][])val;
        }
        
        String[][] flat = this.buildFlatTable( table );
        ctx.set( VAR_FLAT_TABLE, flat );
     
        return flat;
    }
    
    private int findRowIndex( CellDataLocatorContext ctx, TableBlock table ) {
        
        String[][] flatTable = this.getFlatTable( ctx, table );
        String[] texts = new String[table.getRowCount()];

        for( int i = 0; i < table.getRowCount(); i++ ) {
            
            String text = flatTable[i][0];
            texts[i] = text;
            
        }
        return this.mPathMatcher.match( mRowPathPatternElements, texts );
    }
    
    private int findColIndex( CellDataLocatorContext ctx, TableBlock table ) {
        
        String[][] flatTable = this.getFlatTable( ctx, table );
        
        for( int k = 0; k < table.getColCount(); k++ ) {
        
            String[] texts = new String[table.getRowCount()];
           for( int i = 0; i < table.getRowCount(); i++ ) {
               
                String text = flatTable[i][k];
                texts[i] = text;
            }
            int index= this.mPathMatcher.match( this.mColPathPatternElements, texts );
            if( index > -1 ) return k;
        }
        
        return -1;
    }
    
    /**
     * 构造一个平坦的表格，已合并的单元被拆散，内容分配到各个单元格里。
     * 单元格内容中影响路径判断的特殊字符都被替换成中文符号
     * @param table
     * @return
     */
    private String[][] buildFlatTable( TableBlock table ) {
        
        int rows = table.getRowCount();
        int cols = table.getColCount();
        
        String[][] cells = new String[rows][cols];
        
        for( int i = 0; i < rows; i++ ) {
            
            for( int j = 0; j < cols; j++ ) {
                
                TableCellBlock cell = table.getCell( i, j );
                if( cell == null ) continue;
                
                String text = cell.getContent();
                if( text == null ) text = "";
                text = text.trim();
                
                //替换掉路径定义字符: / * !
                text = StringHelper.replace( text, "/", "∕" );
                text = StringHelper.replace( text, "*", "※" );
                text = StringHelper.replace( text, "!", "！" );
                
                for( int p = 0; p < cell.getRowspan(); p++ ) {
                    for( int q = 0; q < cell.getColspan(); q++ ) {
                        cells[i+p][j+q] = text;
                    }
                }
            }
        }
        return cells;
    }
}
