package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizTccModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [41:14:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizTccModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cancelMethod
     * 
     */
    private java.lang.String _cancelMethod ;
    
    /**
     *  
     * xml name: confirmMethod
     * 
     */
    private java.lang.String _confirmMethod ;
    
    /**
     * 
     * xml name: cancelMethod
     *  
     */
    
    public java.lang.String getCancelMethod(){
      return _cancelMethod;
    }

    
    public void setCancelMethod(java.lang.String value){
        checkAllowChange();
        
        this._cancelMethod = value;
           
    }

    
    /**
     * 
     * xml name: confirmMethod
     *  
     */
    
    public java.lang.String getConfirmMethod(){
      return _confirmMethod;
    }

    
    public void setConfirmMethod(java.lang.String value){
        checkAllowChange();
        
        this._confirmMethod = value;
           
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
        
        out.putNotNull("cancelMethod",this.getCancelMethod());
        out.putNotNull("confirmMethod",this.getConfirmMethod());
    }

    public BizTccModel cloneInstance(){
        BizTccModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizTccModel instance){
        super.copyTo(instance);
        
        instance.setCancelMethod(this.getCancelMethod());
        instance.setConfirmMethod(this.getConfirmMethod());
    }

    protected BizTccModel newInstance(){
        return (BizTccModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
