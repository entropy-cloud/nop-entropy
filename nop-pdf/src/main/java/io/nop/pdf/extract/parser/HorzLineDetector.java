package io.nop.pdf.extract.parser;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.nop.pdf.extract.ILineDetector;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.ShapeBlock;
import io.nop.pdf.extract.struct.TableBlock;

/**
 * 水平分割线检测程序
 */
public class HorzLineDetector implements ILineDetector {

    /**
     * 有效区域(分割线上部区域)上边沿在页面高度中的比例
     */
    private double mRangeTop  = 0.0;
    
    /**
     * 有效区域(分割线上部区域)下边沿在页面高度中的比例
     */
    private double mRangeBottom = 0.25;
    
    /**
     * 分割线宽度占页面宽度的最小比例
     */
    private double mMinWidthPercent = 0.70;
    
    /**
     * 分割线长宽比的最小值
     */
    private double mMinLenToWidthRatio  = 20;
    
    /**
     * 是否使用降序
     */
    private boolean mDescOrder = false;
    
    public double getRangeTop() {
        return mRangeTop;
    }

    public void setRangeTop( double rangeTop ) {
        this.mRangeTop = rangeTop;
    }

    public double getRangeBottom() {
        return mRangeBottom;
    }

    public void setRangeBottom( double rangeBottom ) {
        this.mRangeBottom = rangeBottom;
    }

    public double getMinWidthPercent() {
        return mMinWidthPercent;
    }

    public void setMinWidthPercent( double minWidthPercent ) {
        this.mMinWidthPercent = minWidthPercent;
    }

    public double getMinLenToWidthRatio() {
        return mMinLenToWidthRatio;
    }

    public void setMinLenToWidthRatio( double minLenToWidthRatio ) {
        this.mMinLenToWidthRatio = minLenToWidthRatio;
    }

    public boolean isDescOrder() {
        return mDescOrder;
    }

    public void setDescOrder( boolean descOrder ) {
        this.mDescOrder = descOrder;
    }
    
    @Override
    public List<ShapeBlock> detect( ResourcePage page ) {

        List<ShapeBlock> lines = new ArrayList<ShapeBlock>();
        
        this.detectlines( page, lines );
        
        return lines;
    }
    
    /**
     * 检测页内的分割线条
     * @param page
     * @param lines
     */
    private void detectlines( ResourcePage page, List<ShapeBlock> lines ) {
        
        lines.clear();
        
        double vw = page.getPageViewBounding().getWidth();
        double vh = page.getPageViewBounding().getHeight();
        
        double miny = this.mRangeTop * vh;
        double maxy = this.mRangeBottom * vh;
        
        List<ShapeBlock> shapes = page.getShapeBlocks();
        List<TableBlock> tables = page.getTables();
        
        List<ShapeBlock> shapesInRange = new ArrayList<ShapeBlock>();
        
        List<ShapeBlock> candidates = new ArrayList<ShapeBlock>();
        
        for( ShapeBlock shape : shapes ) {
            
            Rectangle2D rect = shape.getViewBounding();
            
            //排除空
            if( rect == null || rect.isEmpty() ) {
                continue;
            }
            
            //排除有效区域之外的
            if( rect.getMinY() > maxy || rect.getMaxY() < miny ) {
                continue;
            }
            
            //记录与有效区域有交集的
            shapesInRange.add( shape );
            
            //排除与表格有交集的
            if( this.checkIntersectTables( shape, tables ) ) {
                continue;
            }
            
            //排除长宽比不服的
            if( rect.getWidth() < this.mMinLenToWidthRatio * rect.getHeight() ) {
                continue;
            }
            
            ///排除长度不够的
            if( rect.getWidth() < this.mMinWidthPercent * vw ) {
                continue;
            }
            
            candidates.add( shape );
        }
        
        //排序
        this.sortCandiates( candidates );
        
        //TODO: 需要优化，是否可以只使用第一条
        
        //查找独立的线条
        for( ShapeBlock candidate : candidates ) {
            
            boolean intersects = this.checkIntersectShapes( candidate, shapesInRange );
            if( !intersects ) {
                lines.add( candidate );
            }
        }
    }
    
    /**
     * 检查是否与表格相交
     * @param shape
     * @param tables
     * @return
     */
    private boolean checkIntersectTables( ShapeBlock shape, List<TableBlock> tables ) {
        
        if( tables == null ) return false;
        
        Rectangle2D rect = shape.getViewBounding();
        if( rect == null || rect.isEmpty() ) return false;
        
        for( TableBlock table : tables ) {
            
            Rectangle2D rc = table.getViewBounding();
            if( rc == null || rc.isEmpty() ) continue;
            
            boolean b = rc.intersects( rect );
            if( b ) return true;
        }
        
        return false;
    }
    
    /**
     * 判断是否为独立图像块
     * @param shape
     * @param shapes
     * @return
     */
    private boolean checkIntersectShapes( ShapeBlock shape, List<ShapeBlock> shapes ) {
        
        Rectangle2D rect = shape.getViewBounding();
        
        for( ShapeBlock shp : shapes ) {
            
            if( shp == null ) continue;
            
            if( shp == shape ) continue;
            
            Rectangle2D rc = shp.getViewBounding();
            if( rc == null || rc.isEmpty() ) {
                continue;
            }
            
            if( rect.intersects( rc ) ) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 按y坐标排序
     * @param candidates
     */
    private void sortCandiates( List<ShapeBlock> candidates ) {
        
        if( this.mDescOrder ) {
            
            Collections.sort( candidates, new Comparator<ShapeBlock>() {
                
                @Override
                public int compare( ShapeBlock block1, ShapeBlock block2 ) {
                    
                    if( block1 == block2 ) return 0;
                    if( block1.getViewBounding().getMaxY() < block2.getViewBounding().getMaxY() ) return -1;
                    if( block1.getViewBounding().getMaxY() > block2.getViewBounding().getMaxY() ) return 1;
                    
                    return 0;
                }
            });
            return;
        }
        
        Collections.sort( candidates, new Comparator<ShapeBlock>() {
            
            @Override
            public int compare( ShapeBlock block1, ShapeBlock block2 ) {
                
                if( block1 == block2 ) return 0;
                if( block1.getViewBounding().getMinY() < block2.getViewBounding().getMinY() ) return -1;
                if( block1.getViewBounding().getMinY() > block2.getViewBounding().getMinY() ) return 1;
                
                return 0;
            }
        });
    }

}
