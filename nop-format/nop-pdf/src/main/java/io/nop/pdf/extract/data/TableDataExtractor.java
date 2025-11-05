package io.nop.pdf.extract.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;

/**
 * 表格数据提取
 *
 */
public class TableDataExtractor {

    private ITableLocaltor mTableLocator = null;
    
    private List<ICellDataLocator> mCellDataLocator = null;
    
    public ITableLocaltor getTableLocator() {
        
        return mTableLocator;
    }

    public void setTableLocator( ITableLocaltor tableLocator ) {
        
        this.mTableLocator = tableLocator;
    }

    public List<ICellDataLocator> getCellLocaltors() {
        
        return mCellDataLocator;
    }

    public void setCellLocaltors( List<ICellDataLocator> cellLocaltors ) {
        
        this.mCellDataLocator = cellLocaltors;
    }
    
    public Map<String,String> extract( ResourceDocument doc ) {
                
        List<TableBlock> tables = this.mTableLocator.locateTables( doc );
        
        if( tables == null || tables.size() == 0 ) {
            return new HashMap<String,String>();
        }
        
        return this.extractTableData( tables.get( 0 ) );
    }
    
    private Map<String,String> extractTableData( TableBlock table ) {
        
        Map<String,String> data = new HashMap<String,String>();
        
        CellDataLocatorContext ctx = new CellDataLocatorContext();
        
        for( ICellDataLocator locator : this.mCellDataLocator ) {
            
            TableCellBlock cell = locator.locate( ctx, table );
            if( cell == null ) {
                continue;
            }
            
            data.put( locator.getKey(), cell.getContent() );
        }
        return data;
    }
}
