package io.nop.pdf.extract.data;

import java.util.ArrayList;
import java.util.List;

public class WildcardPathMatcher {

    /**
     * 路径分割符
     */
    public static final String PATH_SEP = "/";
    
    /**
     * 路径开始符
     */
    public static final String WILDCARD_BEFORE_FIRST = "*BEFORE-FIRST*";
    
    /**
     * 一级通配符
     */
    public static final String WILDCARD_ANYONE = "*";
    
    /**
     * 多级通配符
     */
    public static final String WILDCARD_ANYLEVELS = "**";
    
    public int match( List<String> pathPattern, String[] path ) {
        
        List<String> list = new ArrayList<String>();
        for( String str : path ) {
            list.add( str );
        }
        
        return this.match( pathPattern, list );
    }
    
    /**
     * 匹配路径，返回按pathPattern匹配出的部分在path中的位置（末尾），-1表示未成功匹配
     * 
     * @param pathPattern
     * @param path
     * @return
     */
    public int match( List<String> pathPattern, List<String> path ) {
        
        if( path == null ) return -1;
        
        String last = path.get( path.size() - 1 );
        
        //最后一级不允许为**
        if( WILDCARD_ANYLEVELS.equals( last ) ) {
            return -1;
        }
        
        return this.matchAndGetIndex( pathPattern, path );
    }
    
    /**
     * 匹配路径，返回按pathPattern匹配出的部分在path中的位置（末尾），-1表示未成功匹配
     * 
     * @param pathPattern
     * @param path
     * @return
     */
    private int matchAndGetIndex( List<String> pathPattern, List<String> path ) {
        
        int pos = 0;
        
        for( int k = 0; k < pathPattern.size(); k++ ) {
            
            String pattern = pathPattern.get( k );
            
            if( pattern.equals( WILDCARD_BEFORE_FIRST ) ) {
                
                if( pos > 0 ) return -1;
            }
            else if( pattern.equals( WILDCARD_ANYLEVELS ) ) {
                
            }
            else if( pattern.equals( WILDCARD_ANYONE ) ) {
                pos++;
            }
            else {
                //从pos往后找到第一个match
                boolean found = false;
                while( pos < path.size() && !found ) {
                    String str = path.get( pos );
                    if( pattern.equals( str ) ) {
                        found = true;
                    }
                    pos++;
                }
                if( !found ) return -1;
            }
        }
        
        return pos - 1;
    }
}
