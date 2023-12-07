package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [73:6:0:0]/nop/schema/record/record-field.xdef <p>
 * 动态确定字段类型
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _RecordFieldSwitch extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: case
     * 
     */
    private KeyedList<io.nop.record.model.RecordFieldSwitchCase> _cases = KeyedList.emptyList();
    
    /**
     *  
     * xml name: default
     * 
     */
    private java.lang.String _default ;
    
    /**
     *  
     * xml name: on
     * 类型判断表达式
     */
    private io.nop.core.lang.eval.IEvalAction _on ;
    
    /**
     * 
     * xml name: case
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordFieldSwitchCase> getCases(){
      return _cases;
    }

    
    public void setCases(java.util.List<io.nop.record.model.RecordFieldSwitchCase> value){
        checkAllowChange();
        
        this._cases = KeyedList.fromList(value, io.nop.record.model.RecordFieldSwitchCase::getWhen);
           
    }

    
    public io.nop.record.model.RecordFieldSwitchCase getCase(String name){
        return this._cases.getByKey(name);
    }

    public boolean hasCase(String name){
        return this._cases.containsKey(name);
    }

    public void addCase(io.nop.record.model.RecordFieldSwitchCase item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordFieldSwitchCase> list = this.getCases();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordFieldSwitchCase::getWhen);
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
     * xml name: default
     *  
     */
    
    public java.lang.String getDefault(){
      return _default;
    }

    
    public void setDefault(java.lang.String value){
        checkAllowChange();
        
        this._default = value;
           
    }

    
    /**
     * 
     * xml name: on
     *  类型判断表达式
     */
    
    public io.nop.core.lang.eval.IEvalAction getOn(){
      return _on;
    }

    
    public void setOn(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._on = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cases = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cases);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("cases",this.getCases());
        out.put("default",this.getDefault());
        out.put("on",this.getOn());
    }
}
 // resume CPD analysis - CPD-ON
