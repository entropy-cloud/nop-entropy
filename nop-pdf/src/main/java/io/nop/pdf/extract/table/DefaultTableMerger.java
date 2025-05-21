package io.nop.pdf.extract.table;

import java.awt.geom.Rectangle2D;
import java.util.List;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;

/**
 * 默认的表格合并接口实现类
 */
public class DefaultTableMerger implements ITableMerger {

    @Override
    public TableBlock merge( ResourceDocument doc, TableBlock table1, TableBlock table2 ) {

        if( !this.checkPageNo( table1, table2 ) ) {
            return null;
        }

        if( !this.checkColumnNumAndWidth( table1, table2 ) ) {
            return null;
        }

        return tryMerge( doc, table1, table2 );
    }

    /**
     * 检查页码是否连续
     * 
     * @param table1
     * @param table2
     * @return
     */
    private boolean checkPageNo( TableBlock table1, TableBlock table2 ) {

        int pageNo1 = table1.getPageNo();
        int pageNo2 = table2.getPageNo();

        if( table1.isMerged() ) {

            if( pageNo2 - table1.getEndingPageNo() != 1 ) {
                return false;
            }
            return true;
        }

        if( pageNo2 - pageNo1 != 1 ) {
            return false;
        }
        return true;
    }

    /**
     * 检查列是否兼容
     * 
     * @param table1
     * @param table2
     * @return
     */
    private boolean checkColumnNumAndWidth( TableBlock table1, TableBlock table2 ) {

        // 检查列数是否相同
        if( table1.getColCount() != table2.getColCount() ) {
            return false;
        }

        // 检查列宽
        List<Double> xpoints1 = table1.getXpoints();
        List<Double> xpoints2 = table2.getXpoints();
        int matchedCount = 0;
        for( int i = 0; i < table1.getColCount(); i++ ) {

            double x1 = xpoints1.get( i );
            double x2 = xpoints2.get( i );

            if( Math.abs( x1 - x2 ) < 0.1 ) {
                matchedCount++;
            }
        }

        // 列宽符合率小于85%,不合并
        if( matchedCount < table1.getColCount() * 0.85 ) {
            return false;
        }

        return true;
    }

    private TableBlock tryMerge( ResourceDocument doc, TableBlock table1, TableBlock table2 ) {

        // 开始合并
        TableBlock newTable = new TableBlock();
        
        //将第一个表合并到新表中
        for( int i = 0, n = table1.getRowCount(); i < n; i++ ) {
            
            for( TableCellBlock c : table1.getRowCells( i ) ) {
                
                if( c == null ) continue;
                
                TableCellBlock cell = new TableCellBlock( c.getRowPos(), c.getColPos(), c.getRowspan(), c.getColspan() );
                cell.setContent( c.getContent() );
                cell.setViewBounding( c.getViewBounding() );

                newTable.addCell( c.getRowPos(), c.getColPos(), cell );
            }
        }

        //将第二个表合并到新表尾部
        Rectangle2D rectTable1 = table1.getViewBounding();
        Rectangle2D rectTable2 = table2.getViewBounding();

        for( int i = 0, n = table2.getRowCount(); i < n; i++ ) {
            
            for( TableCellBlock c: table2.getRowCells( i ) ){
                
                if( c == null ) continue;
                
                int row = c.getRowPos() + table1.getRowCount();
                int col = c.getColPos();
    
                TableCellBlock cell = new TableCellBlock( row, col, c.getRowspan(), c.getColspan() );
                cell.setContent( c.getContent() );
                
                Rectangle2D rc = c.getViewBounding();
                if(rc != null){
                    double x = rc.getMinX();
                    double y = rectTable1.getMinY() + rc.getMinY() - rectTable2.getMinY();
                    Rectangle2D rect = new Rectangle2D.Double( x, y, rc.getWidth(), rc.getHeight() );
                    cell.setViewBounding( rect );
                }
                
                newTable.addCell( row, col, cell );
            }
        }
        
        newTable.setMerged( true );
        newTable.setPageNo( table1.getPageNo() );
        newTable.setEndingPageNo( table2.getPageNo() );
        newTable.setColCount( table1.getColCount() );

        if( rectTable1 != null ) {
           
            double x = rectTable1.getMinX();
            double y = rectTable1.getMinY();
            double w = rectTable1.getWidth();
            double h = rectTable1.getHeight() + rectTable2.getHeight();
            Rectangle2D rectNewTable = new Rectangle2D.Double( x, y, w, h  );
            newTable.setViewBounding( rectNewTable );
            newTable.rebuildXpoints();
        }

        return newTable;
    }
}
