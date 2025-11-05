package io.nop.pdf.extract.dashline;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚线，点划线模式检测
 */
public class DashPatternDetector {

    private double maxChange = 0.2;
    
    /**
     * 从points中检测出一组或者多组模式
     * 
     * @param points 偶数位置为实线段长度，奇数位置为虚线段的长度
     * @return
     */
    public List<DashPattern> process( double[] points ) {
        
        return processPoints( points );
    }
    
    private List<DashPattern> processPoints( double[] points ) {
        
        List<DashPattern> lines = new ArrayList<DashPattern>();
        
        double[] pts = this.normalize( points, 10 );

        int i = 0;
        while( i < pts.length ) {

            boolean found = false;
            
            for( int k = 2; k < 7; k++ ) {

                int steps = this.walk( k, pts, i, pts.length );
                if( steps > 0 ) {
                    DashPattern line = new DashPattern( k, i, steps );
                    lines.add( line );
                    i += steps;
                    found = true;
                    break;
                }
            }
            
            if( !found ) i++;
        }
        return lines;
    }
    
    /**
     * 将所有的实线段和虚线段的长度标准化为1.0+
     * @param points
     * @param padding，末尾填充MAX_DOUBLE的个数，取决于最长模式数
     * @return
     */
    private double[] normalize( double[] points, int padding ) {
        
        
        int count = points.length + padding;
        
        double[] newPoints = new double[count];
        
        //选最小值作为模
        double min = Double.MAX_VALUE;
        for( int i = 0; i < points.length; i++ ) {
            
            if( points[i] > 0 && points[i] < min ) {
                min = points[i];
            }
        }
        
        //将所有数据调整到1以上
        for( int i = 0; i < points.length; i++ ) {
            
            newPoints[i] = points[i] / min;
        }
        
        for( int i = 0; i < padding; i++ ) {
            
            newPoints[points.length + i] = Double.MAX_VALUE;
        }
        
        return newPoints;
    }
    
    private int walk( int n, double[] pts, int startPos, int length ) {
        
        if( startPos + n * 2 > length ) {
            return 0;
        }
        
        double[] prev = new double[n];
        double[] next = new double[n];
        
        int m = ( length - startPos ) / n;
        int steps = 1;
        for( int i = 1; i < m; i++ ) {
            
            int pos = startPos + i * n;
            
            for( int j = 0; j < n; j++ ) {
                prev[j] = pts[pos+j-n];
                next[j] = pts[pos+j];
            }
            
            boolean pass = true;

            for( int j = 0; j < n; j++ ) {
                if( next[j] == Double.MAX_VALUE || ( prev[j] == 0.0 && next[j] != 0.0 ) ) {
                    pass = false;
                    break;
                }
            }
            
            if( !pass ) break;
            
            for( int j = 0; j < n; j++ ) {
                
                double delta = Math.abs( ( next[j] - prev[j] ) / prev[j] ) ;
                if( delta > maxChange ) { 
                    pass = false;
                    break;
                }
            }
            
            if( pass ) steps++;
        }
        
        if( steps > 2 ) return steps * n;
        
        return 0;
    }
}
