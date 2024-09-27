package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordFieldSwitch;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-field.xdef <p>
 * 动态确定字段类型
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
    private io.nop.core.lang.eval.IEvalFunction _on ;
    
    /**
     *  
     * xml name: onField
     * 如果指定了onField，则输出时根据从record[onField]上获取到case类型，然后再映射到type类型，从根对象的types集合中再获取具体定义
     */
    private java.lang.String _onField ;
    
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
    
    public io.nop.core.lang.eval.IEvalFunction getOn(){
      return _on;
    }

    
    public void setOn(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._on = value;
           
    }

    
    /**
     * 
     * xml name: onField
     *  如果指定了onField，则输出时根据从record[onField]上获取到case类型，然后再映射到type类型，从根对象的types集合中再获取具体定义
     */
    
    public java.lang.String getOnField(){
      return _onField;
    }

    
    public void setOnField(java.lang.String value){
        checkAllowChange();
        
        this._onField = value;
           
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
        
        out.putNotNull("cases",this.getCases());
        out.putNotNull("default",this.getDefault());
        out.putNotNull("on",this.getOn());
        out.putNotNull("onField",this.getOnField());
    }

    public RecordFieldSwitch cloneInstance(){
        RecordFieldSwitch instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFieldSwitch instance){
        super.copyTo(instance);
        
        instance.setCases(this.getCases());
        instance.setDefault(this.getDefault());
        instance.setOn(this.getOn());
        instance.setOnField(this.getOnField());
    }

    protected RecordFieldSwitch newInstance(){
        return (RecordFieldSwitch) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
