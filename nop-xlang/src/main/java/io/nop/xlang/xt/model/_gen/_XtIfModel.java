package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtIfModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtIfModel extends io.nop.xlang.xt.model.XtRuleGroupModel {
    
    /**
     *  
     * xml name: test
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _test ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _xtType ;
    
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

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getXtType(){
      return _xtType;
    }

    
    public void setXtType(java.lang.String value){
        checkAllowChange();
        
        this._xtType = value;
           
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
        
        out.putNotNull("test",this.getTest());
        out.putNotNull("xtType",this.getXtType());
    }

    public XtIfModel cloneInstance(){
        XtIfModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtIfModel instance){
        super.copyTo(instance);
        
        instance.setTest(this.getTest());
        instance.setXtType(this.getXtType());
    }

    protected XtIfModel newInstance(){
        return (XtIfModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
