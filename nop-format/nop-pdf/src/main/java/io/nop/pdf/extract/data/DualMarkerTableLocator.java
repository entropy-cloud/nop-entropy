package io.nop.pdf.extract.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.nop.pdf.extract.processor.StringProcessor;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TextlineBlock;
import io.nop.pdf.extract.table.ITableMerger;

/**
 * 表格定位：根据前后两个标记定位表格
 *
 */
public class DualMarkerTableLocator implements ITableLocaltor {

    private String mStartMarker = null;
    
    private String mEndMarker = null;
    
    private boolean mMergeTable = true;
    
    private ITableMerger mTableMerger = null;
    
    public DualMarkerTableLocator() {
        
    }
    
    public DualMarkerTableLocator( String marker1, String marker2 ) {
        
        this.mStartMarker = marker1;
        this.mEndMarker = marker2;
    }
    
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

    public boolean isMergeTable() {
        return mMergeTable;
    }

    public void setMergeTable( boolean mergeTable ) {
        this.mMergeTable = mergeTable;
    }

    public ITableMerger getTableMerger() {
        return mTableMerger;
    }

    public void setTableMerger( ITableMerger tableMerger ) {
        this.mTableMerger = tableMerger;
    }
    
    @Override
    public List<TableBlock> locateTables( ResourceDocument doc ) {

        List<TableBlock> tables = new ArrayList<TableBlock>();
        
        boolean startMarkerFound = false;
        boolean endMarkerFound = false;
        boolean exitSelect = false;
        
        List<ResourcePage> pages = doc.getPages();
        
        for( ResourcePage page : pages ) {
            
            List<Block> blocks = page.getSortedBlocks();
            for( Block block : blocks ) {
                
                if( block == null ) continue;
                
                if( block instanceof TextlineBlock ) {
                    
                    TextlineBlock txtblock = (TextlineBlock)block;
                    
                    String text = txtblock.getContent();
                    if( text == null ) continue;
                    
                    StringProcessor sp = new StringProcessor( text );
                    sp.removeWhitespace();
                    text = sp.toString();
                    
                    if( this.mStartMarker != null &&  startMarkerFound == false ) {
                        
                        boolean matched = Pattern.matches( this.mStartMarker, text );
                        if( matched ) {
                            startMarkerFound = true;
                        }
                        continue;
                    }
                    
                    if( this.mEndMarker != null && endMarkerFound == false ) {
                        
                        boolean matched = Pattern.matches( this.mEndMarker, text );
                        if( matched ) {
                            endMarkerFound = true;
                            exitSelect = true;
                            break;
                        }
                    }
                }
                
                if( block instanceof TableBlock ) {
                    
                    if( startMarkerFound && !endMarkerFound ) {
                        
                        tables.add( (TableBlock)block );
                    }
                }
            }
            if( exitSelect ) break;
        }
        
        if( mMergeTable && mTableMerger != null ) {
            tables = this.merge( doc, tables );
        }
        
        return tables;
    }
    
    private List<TableBlock> merge( ResourceDocument doc, List<TableBlock> srcTables ) {
        
        List<TableBlock> list = new ArrayList<TableBlock>();
        
        list.add( srcTables.get( 0 ) );
        
        for( int i = 1; i < srcTables.size(); i++ ) {

            TableBlock lastTable = list.get( list.size() - 1 );
            TableBlock table1 = srcTables.get( i );

            TableBlock mergedTable = this.mTableMerger.merge( doc, lastTable, table1 );
            if( mergedTable != null ) {
                
                list.remove( lastTable );
                list.add( mergedTable );
            }
            
        }
        return list;
    }
    
    
}
