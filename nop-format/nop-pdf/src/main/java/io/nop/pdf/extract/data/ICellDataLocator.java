package io.nop.pdf.extract.data;

import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;

/**
 * 表格单元格定位的接口
 *
 */
public interface ICellDataLocator {

    /**
     * 唯一键
     * @param key
     */
    void setKey( String key );
    
    /**
     * 唯一键
     * @return
     */
    String getKey();
    
    /**
     * 检查指定单元格是否符合定位
     * @param ctx
     * @param table
     * @param cell
     * @return
     */
    boolean check( CellDataLocatorContext ctx, TableBlock table, TableCellBlock cell );
    
    /**
     * 在表格中定位单元格
     * @param ctx
     * @param table
     * @return
     */
    TableCellBlock locate( CellDataLocatorContext ctx, TableBlock table );
}
