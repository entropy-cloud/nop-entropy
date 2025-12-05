package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizMakerCheckerModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizMakerCheckerModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cancelMethod
     * 
     */
    private java.lang.String _cancelMethod ;
    
    /**
     *  
     * xml name: tryMethod
     * 
     */
    private java.lang.String _tryMethod ;
    
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
     * xml name: tryMethod
     *  
     */
    
    public java.lang.String getTryMethod(){
      return _tryMethod;
    }

    
    public void setTryMethod(java.lang.String value){
        checkAllowChange();
        
        this._tryMethod = value;
           
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
        out.putNotNull("tryMethod",this.getTryMethod());
    }

    public BizMakerCheckerModel cloneInstance(){
        BizMakerCheckerModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizMakerCheckerModel instance){
        super.copyTo(instance);
        
        instance.setCancelMethod(this.getCancelMethod());
        instance.setTryMethod(this.getTryMethod());
    }

    protected BizMakerCheckerModel newInstance(){
        return (BizMakerCheckerModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
