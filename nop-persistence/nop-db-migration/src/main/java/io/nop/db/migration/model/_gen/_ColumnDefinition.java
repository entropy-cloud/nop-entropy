package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ColumnDefinition extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: autoIncrement
     * 
     */
    private boolean _autoIncrement  = false;
    
    /**
     *  
     * xml name: decimalDigits
     * 
     */
    private java.lang.Integer _decimalDigits ;
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.String _defaultValue ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: nullable
     * 
     */
    private boolean _nullable  = true;
    
    /**
     *  
     * xml name: primaryKey
     * 
     */
    private boolean _primaryKey  = false;
    
    /**
     *  
     * xml name: remark
     * 
     */
    private java.lang.String _remark ;
    
    /**
     *  
     * xml name: size
     * 
     */
    private java.lang.Integer _size ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.commons.type.StdSqlType _type ;
    
    /**
     * 
     * xml name: autoIncrement
     *  
     */
    
    public boolean isAutoIncrement(){
      return _autoIncrement;
    }

    
    public void setAutoIncrement(boolean value){
        checkAllowChange();
        
        this._autoIncrement = value;
           
    }

    
    /**
     * 
     * xml name: decimalDigits
     *  
     */
    
    public java.lang.Integer getDecimalDigits(){
      return _decimalDigits;
    }

    
    public void setDecimalDigits(java.lang.Integer value){
        checkAllowChange();
        
        this._decimalDigits = value;
           
    }

    
    /**
     * 
     * xml name: defaultValue
     *  
     */
    
    public java.lang.String getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.String value){
        checkAllowChange();
        
        this._defaultValue = value;
           
    }

    
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
     * xml name: nullable
     *  
     */
    
    public boolean isNullable(){
      return _nullable;
    }

    
    public void setNullable(boolean value){
        checkAllowChange();
        
        this._nullable = value;
           
    }

    
    /**
     * 
     * xml name: primaryKey
     *  
     */
    
    public boolean isPrimaryKey(){
      return _primaryKey;
    }

    
    public void setPrimaryKey(boolean value){
        checkAllowChange();
        
        this._primaryKey = value;
           
    }

    
    /**
     * 
     * xml name: remark
     *  
     */
    
    public java.lang.String getRemark(){
      return _remark;
    }

    
    public void setRemark(java.lang.String value){
        checkAllowChange();
        
        this._remark = value;
           
    }

    
    /**
     * 
     * xml name: size
     *  
     */
    
    public java.lang.Integer getSize(){
      return _size;
    }

    
    public void setSize(java.lang.Integer value){
        checkAllowChange();
        
        this._size = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.commons.type.StdSqlType getType(){
      return _type;
    }

    
    public void setType(io.nop.commons.type.StdSqlType value){
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
        
        out.putNotNull("autoIncrement",this.isAutoIncrement());
        out.putNotNull("decimalDigits",this.getDecimalDigits());
        out.putNotNull("defaultValue",this.getDefaultValue());
        out.putNotNull("name",this.getName());
        out.putNotNull("nullable",this.isNullable());
        out.putNotNull("primaryKey",this.isPrimaryKey());
        out.putNotNull("remark",this.getRemark());
        out.putNotNull("size",this.getSize());
        out.putNotNull("type",this.getType());
    }

    public ColumnDefinition cloneInstance(){
        ColumnDefinition instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ColumnDefinition instance){
        super.copyTo(instance);
        
        instance.setAutoIncrement(this.isAutoIncrement());
        instance.setDecimalDigits(this.getDecimalDigits());
        instance.setDefaultValue(this.getDefaultValue());
        instance.setName(this.getName());
        instance.setNullable(this.isNullable());
        instance.setPrimaryKey(this.isPrimaryKey());
        instance.setRemark(this.getRemark());
        instance.setSize(this.getSize());
        instance.setType(this.getType());
    }

    protected ColumnDefinition newInstance(){
        return (ColumnDefinition) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
