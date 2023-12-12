package io.nop.xlang.xpl.xlib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [101:14:0:0]/nop/schema/xlib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
public abstract class _XplTagReturn extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: stdDomain
     *  
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("description",this.getDescription());
        out.put("stdDomain",this.getStdDomain());
        out.put("type",this.getType());
    }
}
 // resume CPD analysis - CPD-ON
