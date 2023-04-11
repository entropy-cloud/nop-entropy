package io.nop.rule.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [48:14:0:0]/nop/schema/rule.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _RuleTableCellModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: outputs
     * 
     */
    private KeyedList<io.nop.rule.core.model.RuleOutputValueModel> _outputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: pos
     * 
     */
    private io.nop.core.model.table.CellPosition _pos ;
    
    /**
     * 
     * xml name: outputs
     *  
     */
    
    public java.util.List<io.nop.rule.core.model.RuleOutputValueModel> getOutputs(){
      return _outputs;
    }

    
    public void setOutputs(java.util.List<io.nop.rule.core.model.RuleOutputValueModel> value){
        checkAllowChange();
        
        this._outputs = KeyedList.fromList(value, io.nop.rule.core.model.RuleOutputValueModel::getName);
           
    }

    
    public io.nop.rule.core.model.RuleOutputValueModel getOutput(String name){
        return this._outputs.getByKey(name);
    }

    public boolean hasOutput(String name){
        return this._outputs.containsKey(name);
    }

    public void addOutput(io.nop.rule.core.model.RuleOutputValueModel item) {
        checkAllowChange();
        java.util.List<io.nop.rule.core.model.RuleOutputValueModel> list = this.getOutputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rule.core.model.RuleOutputValueModel::getName);
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
     * xml name: pos
     *  
     */
    
    public io.nop.core.model.table.CellPosition getPos(){
      return _pos;
    }

    
    public void setPos(io.nop.core.model.table.CellPosition value){
        checkAllowChange();
        
        this._pos = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._outputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputs);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("outputs",this.getOutputs());
        out.put("pos",this.getPos());
    }
}
 // resume CPD analysis - CPD-ON
