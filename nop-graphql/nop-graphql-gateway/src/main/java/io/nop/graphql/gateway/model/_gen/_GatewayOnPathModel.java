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
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: path
     * 对应REST请求链接，例如 /r/NopAuthUser__findPage
     */
    private java.lang.String _path ;
    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("id",this.getId());
        out.put("path",this.getPath());
    }
}
 // resume CPD analysis - CPD-ON
