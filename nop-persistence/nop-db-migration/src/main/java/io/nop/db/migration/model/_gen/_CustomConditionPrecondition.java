package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.CustomConditionPrecondition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 自定义条件检查
 * 使用 XPL 表达式判断条件
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CustomConditionPrecondition extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: expect
     * 期望结果
     */
    private io.nop.db.migration.PreconditionExpect _expect ;
    
    /**
     *  
     * xml name: expression
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _expression ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: expect
     *  期望结果
     */
    
    public io.nop.db.migration.PreconditionExpect getExpect(){
      return _expect;
    }

    
    public void setExpect(io.nop.db.migration.PreconditionExpect value){
        checkAllowChange();
        
        this._expect = value;
           
    }

    
    /**
     * 
     * xml name: expression
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getExpression(){
      return _expression;
    }

    
    public void setExpression(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._expression = value;
           
    }

    
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
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
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
        
        out.putNotNull("expect",this.getExpect());
        out.putNotNull("expression",this.getExpression());
        out.putNotNull("id",this.getId());
        out.putNotNull("type",this.getType());
    }

    public CustomConditionPrecondition cloneInstance(){
        CustomConditionPrecondition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CustomConditionPrecondition instance){
        super.copyTo(instance);
        
        instance.setExpect(this.getExpect());
        instance.setExpression(this.getExpression());
        instance.setId(this.getId());
        instance.setType(this.getType());
    }

    protected CustomConditionPrecondition newInstance(){
        return (CustomConditionPrecondition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
