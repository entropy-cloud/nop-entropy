package io.nop.pdf.extract.dashline;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ShapeBlock;

/**
 * 默认的虚线检测。
 * pdf中的虚线由多个小图片首尾拼接而成，虚线中的空白部分由图片中的空白实现
 */
public class DefaultDashLineDetector implements DashlineDetector {

    private static final Logger LOG = LoggerFactory.getLogger( DefaultDashLineDetector.class );
    
    private double floatError = 0.00001;
    
    private double maxBlockWidth = 6;
    private double maxBlockHeight = 6;
    
    private double maxGap = 0.1;
    
    private int minBlockCount = 3;
    
    /**
     * 从图像检测虚线
     * 
     * @param blocks
     * @return
     */
    public List<ShapeBlock> process( List<ImageBlock> blocks ) {
        
        List<ShapeBlock> shapes = new ArrayList<ShapeBlock>();
        
        List<ImageBlock> smallImages = this.filterImageBlocks( blocks );
        
        List<ShapeBlock> hLines = findHorzLines( smallImages );
        List<ShapeBlock> vLines = findVertLines( smallImages );
        
        shapes.addAll( hLines );
        shapes.addAll( vLines );
        
        this.traceShapeBlocks( "horz lines", hLines );
        this.traceShapeBlocks( "vert lines", vLines );
        
        return shapes;
    }
    
    /**
     * 过滤掉较大的图片
     * 
     * @param blocks
     * @return
     */
    private List<ImageBlock> filterImageBlocks( List<ImageBlock> blocks ) {
        
        List<ImageBlock> selectedImageBlocks = new ArrayList<ImageBlock>();
        for( int i = 0; i < blocks.size(); i++ ) {
            
            ImageBlock block = blocks.get( i );
            Rectangle2D rc = block.getViewBounding();
            if( rc.getWidth() > this.maxBlockWidth && rc.getHeight() > this.maxBlockHeight ) {
                continue;
            }
            selectedImageBlocks.add( block );
        }
        return selectedImageBlocks;
    }
    
    
    private List<ShapeBlock> findHorzLines( List<ImageBlock> imageBlocks ) {
        
        List<ShapeBlock> shapes = new ArrayList<ShapeBlock>();
        if( imageBlocks.size() < this.minBlockCount ) return shapes;
        
        //按top值进行排序
        Collections.sort( imageBlocks, new Comparator<ImageBlock>() {

            @Override
            public int compare( ImageBlock o1, ImageBlock o2 ) {
                
                Rectangle2D rc1 = o1.getViewBounding();
                Rectangle2D rc2 = o2.getViewBounding();
                
                double delta = rc2.getMinY() - rc1.getMinY();
                if( Math.abs( delta ) < floatError ) {
                    if( rc1.getMinX() < rc2.getMinX() ) return -1;
                    if( rc1.getMinX() > rc2.getMinX() ) return 1;
                    return 0;
                }
                if( rc1.getMinY() < rc2.getMinY() ) return -1; 
                if( rc1.getMinY() > rc2.getMinY() ) return 1;
                return 0;
            }
        });
        
        //划分为行
        List<List<ImageBlock>> rows = new ArrayList<List<ImageBlock>>();
        
        List<ImageBlock> row = new ArrayList<ImageBlock>();
        ImageBlock head = imageBlocks.get( 0 );
        row.add( head );
        double rowHeaderY = head.getViewBounding().getMinY();
        
        for( int i = 1; i < imageBlocks.size(); i++ ) {
         
            ImageBlock block = imageBlocks.get( i );
            Rectangle2D rc = block.getViewBounding();
            
            double delta = rc.getMinY() - rowHeaderY;
            if( Math.abs( delta ) < floatError ) {
                row.add( block );
                continue;
            }
            
            rows.add( row );
            
            row = new ArrayList<ImageBlock>();
            head = block;
            row.add( head );
            rowHeaderY = head.getViewBounding().getMinY();
        }
        rows.add( row );
        
        //逐行处理，查找水平线
        for( int i = 0; i < rows.size(); i++ ) {
            
            List<ShapeBlock> linesInRow = this.findHorzLinesInRow( rows.get( i ) );
            shapes.addAll( linesInRow );
        }
        
        return shapes;
    }
    
    private List<ShapeBlock> findHorzLinesInRow( List<ImageBlock> row ) {
        
        List<ShapeBlock> shapes = new ArrayList<ShapeBlock>();
        if( row.size() < minBlockCount ) return shapes;
        
        if( true ) {
            Rectangle2D rr = (Rectangle2D)row.get( row.size() - 1 ).getViewBounding();
            ImageBlock padding = new ImageBlock();
            padding.setViewBounding( new Rectangle2D.Double( Double.MAX_VALUE - 100, rr.getY(), 99, rr.getHeight() ) );
            row.add( padding );
        }
        
        traceImageBlocks( "block row", row );
        
        ImageBlock head = row.get( 0 );
        ImageBlock tail = head;
        int headIndex = 0;
        
        for( int i = 0; i < row.size(); i++ ) {
            
            ImageBlock block = row.get( i );
            Rectangle2D rect = block.getViewBounding();
            
            double x1 = tail.getViewBounding().getMinX() + tail.getViewBounding().getWidth();
            double x2 = rect.getMinX();
            
            if( x1 + this.maxGap < x2 ) {
                
                //x方向有较大空隙，认为一个线段结束，新的线段开始了
                int n = i - headIndex;
                if( n >= minBlockCount ) {                    
                    ShapeBlock shape = this.makeLineShape( head.getViewBounding(), tail.getViewBounding() );
                    shapes.add( shape );
                }
                head = block;
                tail = head;
                headIndex = i;
                continue;
            }
            tail = block;
        }
        return shapes;
    }
    
    private List<ShapeBlock> findVertLines( List<ImageBlock> imageBlocks ) {
     
        List<ShapeBlock> shapes = new ArrayList<ShapeBlock>();

        if( imageBlocks.size() < this.minBlockCount ) return shapes;
        
        //按Left值进行排序
        Collections.sort( imageBlocks, new Comparator<ImageBlock>() {

            @Override
            public int compare( ImageBlock o1, ImageBlock o2 ) {
                
                Rectangle2D rc1 = o1.getViewBounding();
                Rectangle2D rc2 = o2.getViewBounding();
                
                double delta = rc2.getMinX() - rc1.getMinX();
                if( Math.abs( delta ) < floatError ) {
                    if( rc1.getMinY() < rc2.getMinY() ) return -1;
                    if( rc1.getMinY() > rc2.getMinY() ) return 1;
                    return 0;
                }
                if( rc1.getMinX() < rc2.getMinX() ) return -1; 
                if( rc1.getMinX() > rc2.getMinX() ) return 1;
                return 0;
            }
        });
        
        //划分为列
        List<List<ImageBlock>> cols = new ArrayList<List<ImageBlock>>();
        
        List<ImageBlock> col = new ArrayList<ImageBlock>();
        ImageBlock head = imageBlocks.get( 0 );
        col.add( head );
        double colHeaderX = head.getViewBounding().getMinX();
        
        for( int i = 1; i < imageBlocks.size(); i++ ) {
         
            ImageBlock block = imageBlocks.get( i );
            Rectangle2D rc = block.getViewBounding();
            
            double delta = rc.getMinX() - colHeaderX;
            if( Math.abs( delta ) < floatError ) {
                col.add( block );
                continue;
            }
            
            cols.add( col );
            
            col = new ArrayList<ImageBlock>();
            head = block;
            col.add( head );
            colHeaderX = head.getViewBounding().getMinX();
        }
        cols.add( col );
        
        //逐列处理，查找竖直线
        for( int i = 0; i < cols.size(); i++ ) {
            
            List<ShapeBlock> linesIncol = this.findVertLinesInCol( cols.get( i ) );
            shapes.addAll( linesIncol );
        }
        
        return shapes;
    }

    private List<ShapeBlock> findVertLinesInCol( List<ImageBlock> col ) {
        
        List<ShapeBlock> shapes = new ArrayList<ShapeBlock>();
        if( col.size() < minBlockCount ) return shapes;
        
        if( true ) {
            Rectangle2D rr = (Rectangle2D)col.get( col.size() - 1 ).getViewBounding();
            ImageBlock padding = new ImageBlock();
            padding.setViewBounding( new Rectangle2D.Double( rr.getX(), Double.MAX_VALUE - 100, rr.getWidth(), 99 ) );
            col.add( padding );
        }
        
        traceImageBlocks( "block col", col );
        
        ImageBlock head = col.get( 0 );
        ImageBlock tail = head;
        int headIndex = 0;
        
        for( int i = 0; i < col.size(); i++ ) {
            
            ImageBlock block = col.get( i );
            Rectangle2D rect = block.getViewBounding();
            
            double y1 = tail.getViewBounding().getMinY() + tail.getViewBounding().getHeight();
            double y2 = rect.getMinY();
            
            if( y1 + this.maxGap < y2 ) {
                
                //y方向有较大空隙，认为一个线段结束，新的线段开始了
                int n = i - headIndex;
                if( n >= minBlockCount ) {                    
                    ShapeBlock shape = this.makeLineShape( head.getViewBounding(), tail.getViewBounding() );
                    shapes.add( shape );
                }
                head = block;
                tail = head;
                headIndex = i;
                continue;
            }
            tail = block;
        }
        return shapes;
    }
    
    private ShapeBlock makeLineShape( Rectangle2D headRect, Rectangle2D tailRect ) {
        
        Rectangle2D rect = headRect.createUnion( tailRect );
        
        ShapeBlock shape = new ShapeBlock();
        shape.setViewBounding( rect );
        
        return shape;
    }
    
    
    private void traceImageBlocks( String intro, List<ImageBlock> imageBlocks ) {
        
        if( !LOG.isTraceEnabled() ) return;
        
        if( intro != null ) {
            System.out.println( intro );
        }
        
        for( int i = 0; i < imageBlocks.size(); i++ ) {
            
            ImageBlock block = imageBlocks.get( i );
            
            Rectangle2D rect = block.getViewBounding();
            
            System.out.printf( "%d [%f,%f - %f,%f]\r\n", i, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight() );
        }
    }
    
    private void traceShapeBlocks( String intro, List<ShapeBlock> shapes ) {
        
        if( !LOG.isTraceEnabled() ) return;
        
        if( intro != null ) {
            System.out.println( intro );
        }
        
        for( int i = 0; i < shapes.size(); i++ ) {
            
            ShapeBlock block = shapes.get( i );
            
            Rectangle2D rect = block.getViewBounding();
            
            System.out.printf( "%d [%f,%f - %f,%f]\r\n", i, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight() );
        }
    }
}
