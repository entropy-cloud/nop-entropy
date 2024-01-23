package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfEndModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [31:6:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfEndModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: output
     * 返回给父流程的值
     */
    private KeyedList<io.nop.wf.core.model.WfReturnVarModel> _outputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: output
     *  返回给父流程的值
     */
    
    public java.util.List<io.nop.wf.core.model.WfReturnVarModel> getOutputs(){
      return _outputs;
    }

    
    public void setOutputs(java.util.List<io.nop.wf.core.model.WfReturnVarModel> value){
        checkAllowChange();
        
        this._outputs = KeyedList.fromList(value, io.nop.wf.core.model.WfReturnVarModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfReturnVarModel getOutput(String name){
        return this._outputs.getByKey(name);
    }

    public boolean hasOutput(String name){
        return this._outputs.containsKey(name);
    }

    public void addOutput(io.nop.wf.core.model.WfReturnVarModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfReturnVarModel> list = this.getOutputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfReturnVarModel::getName);
            setOutputs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_outputs(){
        return this._outputs.keySet();
    }

    public boolean hasOutputs(){
        return !this._outputs.isEmpty();
    }
    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._outputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputs);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("outputs",this.getOutputs());
        out.putNotNull("source",this.getSource());
    }

    public WfEndModel cloneInstance(){
        WfEndModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfEndModel instance){
        super.copyTo(instance);
        
        instance.setOutputs(this.getOutputs());
        instance.setSource(this.getSource());
    }

    protected WfEndModel newInstance(){
        return (WfEndModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
