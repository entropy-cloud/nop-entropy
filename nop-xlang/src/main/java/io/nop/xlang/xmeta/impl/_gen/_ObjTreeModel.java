package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [36:6:0:0]/nop/schema/xmeta.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ObjTreeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: childrenProp
     * 
     */
    private java.lang.String _childrenProp ;
    
    /**
     *  
     * xml name: isLeafProp
     * 
     */
    private java.lang.String _isLeafProp ;
    
    /**
     *  
     * xml name: parentProp
     * 
     */
    private java.lang.String _parentProp ;
    
    /**
     * 
     * xml name: childrenProp
     *  
     */
    
    public java.lang.String getChildrenProp(){
      return _childrenProp;
    }

    
    public void setChildrenProp(java.lang.String value){
        checkAllowChange();
        
        this._childrenProp = value;
           
    }

    
    /**
     * 
     * xml name: isLeafProp
     *  
     */
    
    public java.lang.String getIsLeafProp(){
      return _isLeafProp;
    }

    
    public void setIsLeafProp(java.lang.String value){
        checkAllowChange();
        
        this._isLeafProp = value;
           
    }

    
    /**
     * 
     * xml name: parentProp
     *  
     */
    
    public java.lang.String getParentProp(){
      return _parentProp;
    }

    
    public void setParentProp(java.lang.String value){
        checkAllowChange();
        
        this._parentProp = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("childrenProp",this.getChildrenProp());
        out.put("isLeafProp",this.getIsLeafProp());
        out.put("parentProp",this.getParentProp());
    }
}
 // resume CPD analysis - CPD-ON
