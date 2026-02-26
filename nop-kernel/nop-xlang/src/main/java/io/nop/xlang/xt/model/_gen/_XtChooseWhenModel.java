package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtChooseWhenModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtChooseWhenModel extends io.nop.xlang.xt.model.XtRuleGroupModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: test
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _test ;
    
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
     * xml name: test
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getTest(){
      return _test;
    }

    
    public void setTest(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._test = value;
           
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
        
        out.putNotNull("id",this.getId());
        out.putNotNull("test",this.getTest());
    }

    public XtChooseWhenModel cloneInstance(){
        XtChooseWhenModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtChooseWhenModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setTest(this.getTest());
    }

    protected XtChooseWhenModel newInstance(){
        return (XtChooseWhenModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
