package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-template.xdef <p>
 * 动态生成消息对象，可用于动态测试
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.record.model.RecordTemplateFieldModel> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: generator
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _generator ;
    
    /**
     *  
     * xml name: template
     * 
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _template ;
    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordTemplateFieldModel> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.record.model.RecordTemplateFieldModel> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.record.model.RecordTemplateFieldModel::getName);
           
    }

    
    public io.nop.record.model.RecordTemplateFieldModel getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.record.model.RecordTemplateFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordTemplateFieldModel> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordTemplateFieldModel::getName);
            setFields(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_fields(){
        return this._fields.keySet();
    }

    public boolean hasFields(){
        return !this._fields.isEmpty();
    }
    
    /**
     * 
     * xml name: generator
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getGenerator(){
      return _generator;
    }

    
    public void setGenerator(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._generator = value;
           
    }

    
    /**
     * 
     * xml name: template
     *  
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._template = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("fields",this.getFields());
        out.putNotNull("generator",this.getGenerator());
        out.putNotNull("template",this.getTemplate());
    }

    public RecordTemplateModel cloneInstance(){
        RecordTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordTemplateModel instance){
        super.copyTo(instance);
        
        instance.setFields(this.getFields());
        instance.setGenerator(this.getGenerator());
        instance.setTemplate(this.getTemplate());
    }

    protected RecordTemplateModel newInstance(){
        return (RecordTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
