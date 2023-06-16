package io.nop.graphql.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [15:14:0:0]/nop/schema/gateway.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _GatewayOnPathModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: path
     * 对应REST请求链接，例如 /r/NopAuthUser__findPage
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: pattern
     * 支持指定前缀或者后缀的简单匹配模式，例如 XYZ*或者 *XYZ
     */
    private java.lang.String _pattern ;
    
    /**
     * 
     * xml name: path
     *  对应REST请求链接，例如 /r/NopAuthUser__findPage
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
    }

    
    /**
     * 
     * xml name: pattern
     *  支持指定前缀或者后缀的简单匹配模式，例如 XYZ*或者 *XYZ
     */
    
    public java.lang.String getPattern(){
      return _pattern;
    }

    
    public void setPattern(java.lang.String value){
        checkAllowChange();
        
        this._pattern = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("path",this.getPath());
        out.put("pattern",this.getPattern());
    }
}
 // resume CPD analysis - CPD-ON
