package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.IfTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _IfTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: condition
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _condition ;
    
    /**
     *  
     * xml name: else
     * 
     */
    private io.nop.task.model.IfElseTaskStepModel _else ;
    
    /**
     *  
     * xml name: then
     * 
     */
    private io.nop.task.model.IfThenTaskStepModel _then ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: condition
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getCondition(){
      return _condition;
    }

    
    public void setCondition(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._condition = value;
           
    }

    
    /**
     * 
     * xml name: else
     *  
     */
    
    public io.nop.task.model.IfElseTaskStepModel getElse(){
      return _else;
    }

    
    public void setElse(io.nop.task.model.IfElseTaskStepModel value){
        checkAllowChange();
        
        this._else = value;
           
    }

    
    /**
     * 
     * xml name: then
     *  
     */
    
    public io.nop.task.model.IfThenTaskStepModel getThen(){
      return _then;
    }

    
    public void setThen(io.nop.task.model.IfThenTaskStepModel value){
        checkAllowChange();
        
        this._then = value;
           
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
        
           this._else = io.nop.api.core.util.FreezeHelper.deepFreeze(this._else);
            
           this._then = io.nop.api.core.util.FreezeHelper.deepFreeze(this._then);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("condition",this.getCondition());
        out.putNotNull("else",this.getElse());
        out.putNotNull("then",this.getThen());
        out.putNotNull("type",this.getType());
    }

    public IfTaskStepModel cloneInstance(){
        IfTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(IfTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setCondition(this.getCondition());
        instance.setElse(this.getElse());
        instance.setThen(this.getThen());
        instance.setType(this.getType());
    }

    protected IfTaskStepModel newInstance(){
        return (IfTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
