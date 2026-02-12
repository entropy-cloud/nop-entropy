package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefCheckMutex;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * 互斥约束（至少一个或完全互斥）
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefCheckMutex extends io.nop.xlang.xdef.impl.XDefAbstractCheck {
    
    /**
     *  
     * xml name: atLeastOne
     * 
     */
    private java.lang.Boolean _atLeastOne ;
    
    /**
     *  
     * xml name: props
     * 
     */
    private java.util.List<java.lang.String> _props ;
    
    /**
     * 
     * xml name: atLeastOne
     *  
     */
    
    public java.lang.Boolean getAtLeastOne(){
      return _atLeastOne;
    }

    
    public void setAtLeastOne(java.lang.Boolean value){
        checkAllowChange();
        
        this._atLeastOne = value;
           
    }

    
    /**
     * 
     * xml name: props
     *  
     */
    
    public java.util.List<java.lang.String> getProps(){
      return _props;
    }

    
    public void setProps(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._props = value;
           
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
        
        out.putNotNull("atLeastOne",this.getAtLeastOne());
        out.putNotNull("props",this.getProps());
    }

    public XDefCheckMutex cloneInstance(){
        XDefCheckMutex instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefCheckMutex instance){
        super.copyTo(instance);
        
        instance.setAtLeastOne(this.getAtLeastOne());
        instance.setProps(this.getProps());
    }

    protected XDefCheckMutex newInstance(){
        return (XDefCheckMutex) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
