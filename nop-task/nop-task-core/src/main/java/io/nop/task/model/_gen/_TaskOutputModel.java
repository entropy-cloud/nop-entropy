package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [24:10:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _TaskOutputModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: forAttr
     * 如果为true，则输出变量到taskContext.attributes集合中，否则输出到parentScope中
     */
    private boolean _forAttr  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: persist
     * 
     */
    private boolean _persist  = false;
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _value ;
    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: forAttr
     *  如果为true，则输出变量到taskContext.attributes集合中，否则输出到parentScope中
     */
    
    public boolean isForAttr(){
      return _forAttr;
    }

    
    public void setForAttr(boolean value){
        checkAllowChange();
        
        this._forAttr = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: persist
     *  
     */
    
    public boolean isPersist(){
      return _persist;
    }

    
    public void setPersist(boolean value){
        checkAllowChange();
        
        this._persist = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValue(){
      return _value;
    }

    
    public void setValue(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._value = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("displayName",this.getDisplayName());
        out.put("forAttr",this.isForAttr());
        out.put("name",this.getName());
        out.put("persist",this.isPersist());
        out.put("value",this.getValue());
    }
}
 // resume CPD analysis - CPD-ON
