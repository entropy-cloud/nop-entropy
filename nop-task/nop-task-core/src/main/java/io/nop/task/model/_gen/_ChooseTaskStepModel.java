package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.ChooseTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [232:14:0:0]/nop/schema/task/task.xdef <p>
 * 类似于switch语句。根据decider的返回结果动态选择执行哪个分支
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChooseTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: case
     * 
     */
    private KeyedList<io.nop.task.model.TaskChooseCaseModel> _cases = KeyedList.emptyList();
    
    /**
     *  
     * xml name: decider
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _decider ;
    
    /**
     *  
     * xml name: otherwise
     * 
     */
    private io.nop.task.model.TaskChooseOtherwiseModel _otherwise ;
    
    /**
     * 
     * xml name: case
     *  
     */
    
    public java.util.List<io.nop.task.model.TaskChooseCaseModel> getCases(){
      return _cases;
    }

    
    public void setCases(java.util.List<io.nop.task.model.TaskChooseCaseModel> value){
        checkAllowChange();
        
        this._cases = KeyedList.fromList(value, io.nop.task.model.TaskChooseCaseModel::getMatch);
           
    }

    
    public io.nop.task.model.TaskChooseCaseModel getCase(String name){
        return this._cases.getByKey(name);
    }

    public boolean hasCase(String name){
        return this._cases.containsKey(name);
    }

    public void addCase(io.nop.task.model.TaskChooseCaseModel item) {
        checkAllowChange();
        java.util.List<io.nop.task.model.TaskChooseCaseModel> list = this.getCases();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.task.model.TaskChooseCaseModel::getMatch);
            setCases(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_cases(){
        return this._cases.keySet();
    }

    public boolean hasCases(){
        return !this._cases.isEmpty();
    }
    
    /**
     * 
     * xml name: decider
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getDecider(){
      return _decider;
    }

    
    public void setDecider(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._decider = value;
           
    }

    
    /**
     * 
     * xml name: otherwise
     *  
     */
    
    public io.nop.task.model.TaskChooseOtherwiseModel getOtherwise(){
      return _otherwise;
    }

    
    public void setOtherwise(io.nop.task.model.TaskChooseOtherwiseModel value){
        checkAllowChange();
        
        this._otherwise = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cases = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cases);
            
           this._otherwise = io.nop.api.core.util.FreezeHelper.deepFreeze(this._otherwise);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("cases",this.getCases());
        out.putNotNull("decider",this.getDecider());
        out.putNotNull("otherwise",this.getOtherwise());
    }

    public ChooseTaskStepModel cloneInstance(){
        ChooseTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChooseTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setCases(this.getCases());
        instance.setDecider(this.getDecider());
        instance.setOtherwise(this.getOtherwise());
    }

    protected ChooseTaskStepModel newInstance(){
        return (ChooseTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
