package io.nop.xlang.xt.model._gen;

import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtValueModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xt.xdef <p>
 * 作为XNode的value输出
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtValueModel extends io.nop.xlang.xt.model.XtRuleModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _body ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _xtType ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBody(){
      return _body;
    }

    
    public void setBody(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("xtType",this.getXtType());
    }

    public XtValueModel cloneInstance(){
        XtValueModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtValueModel instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setMandatory(this.isMandatory());
        instance.setXtType(this.getXtType());
    }

    protected XtValueModel newInstance(){
        return (XtValueModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
