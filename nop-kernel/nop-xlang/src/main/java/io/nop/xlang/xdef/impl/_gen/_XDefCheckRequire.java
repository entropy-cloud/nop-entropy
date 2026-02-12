package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefCheckRequire;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * 条件必填/禁止约束
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefCheckRequire extends io.nop.xlang.xdef.impl.XDefAbstractCheck {
    
    /**
     *  
     * xml name: condition
     * 触发条件
     */
    private io.nop.core.lang.eval.IEvalAction _condition ;
    
    /**
     *  
     * xml name: forbiddenProps
     * 禁止存在的属性列表
     */
    private java.util.Set<java.lang.String> _forbiddenProps ;
    
    /**
     *  
     * xml name: requiredProps
     * 必须存在的属性列表
     */
    private java.util.Set<java.lang.String> _requiredProps ;
    
    /**
     * 
     * xml name: condition
     *  触发条件
     */
    
    public io.nop.core.lang.eval.IEvalAction getCondition(){
      return _condition;
    }

    
    public void setCondition(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._condition = value;
           
    }

    
    /**
     * 
     * xml name: forbiddenProps
     *  禁止存在的属性列表
     */
    
    public java.util.Set<java.lang.String> getForbiddenProps(){
      return _forbiddenProps;
    }

    
    public void setForbiddenProps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._forbiddenProps = value;
           
    }

    
    /**
     * 
     * xml name: requiredProps
     *  必须存在的属性列表
     */
    
    public java.util.Set<java.lang.String> getRequiredProps(){
      return _requiredProps;
    }

    
    public void setRequiredProps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._requiredProps = value;
           
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
        
        out.putNotNull("condition",this.getCondition());
        out.putNotNull("forbiddenProps",this.getForbiddenProps());
        out.putNotNull("requiredProps",this.getRequiredProps());
    }

    public XDefCheckRequire cloneInstance(){
        XDefCheckRequire instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefCheckRequire instance){
        super.copyTo(instance);
        
        instance.setCondition(this.getCondition());
        instance.setForbiddenProps(this.getForbiddenProps());
        instance.setRequiredProps(this.getRequiredProps());
    }

    protected XDefCheckRequire newInstance(){
        return (XDefCheckRequire) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
