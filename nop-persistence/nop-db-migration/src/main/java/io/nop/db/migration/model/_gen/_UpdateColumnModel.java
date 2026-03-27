package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.UpdateColumnModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UpdateColumnModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     *  
     * xml name: valueBoolean
     * 
     */
    private java.lang.Boolean _valueBoolean ;
    
    /**
     *  
     * xml name: valueDate
     * 
     */
    private java.time.LocalDate _valueDate ;
    
    /**
     *  
     * xml name: valueNumeric
     * 
     */
    private java.lang.Number _valueNumeric ;
    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public java.lang.String getValue(){
      return _value;
    }

    
    public void setValue(java.lang.String value){
        checkAllowChange();
        
        this._value = value;
           
    }

    
    /**
     * 
     * xml name: valueBoolean
     *  
     */
    
    public java.lang.Boolean getValueBoolean(){
      return _valueBoolean;
    }

    
    public void setValueBoolean(java.lang.Boolean value){
        checkAllowChange();
        
        this._valueBoolean = value;
           
    }

    
    /**
     * 
     * xml name: valueDate
     *  
     */
    
    public java.time.LocalDate getValueDate(){
      return _valueDate;
    }

    
    public void setValueDate(java.time.LocalDate value){
        checkAllowChange();
        
        this._valueDate = value;
           
    }

    
    /**
     * 
     * xml name: valueNumeric
     *  
     */
    
    public java.lang.Number getValueNumeric(){
      return _valueNumeric;
    }

    
    public void setValueNumeric(java.lang.Number value){
        checkAllowChange();
        
        this._valueNumeric = value;
           
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
        
        out.putNotNull("name",this.getName());
        out.putNotNull("value",this.getValue());
        out.putNotNull("valueBoolean",this.getValueBoolean());
        out.putNotNull("valueDate",this.getValueDate());
        out.putNotNull("valueNumeric",this.getValueNumeric());
    }

    public UpdateColumnModel cloneInstance(){
        UpdateColumnModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UpdateColumnModel instance){
        super.copyTo(instance);
        
        instance.setName(this.getName());
        instance.setValue(this.getValue());
        instance.setValueBoolean(this.getValueBoolean());
        instance.setValueDate(this.getValueDate());
        instance.setValueNumeric(this.getValueNumeric());
    }

    protected UpdateColumnModel newInstance(){
        return (UpdateColumnModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
