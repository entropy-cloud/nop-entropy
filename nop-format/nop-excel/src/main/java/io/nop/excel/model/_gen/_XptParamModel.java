package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.XptParamModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XptParamModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: controlType
     * 
     */
    private java.lang.String _controlType ;
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.String _defaultValue ;
    
    /**
     *  
     * xml name: dependsOn
     * 
     */
    private java.lang.String _dependsOn ;
    
    /**
     *  
     * xml name: dictName
     * 
     */
    private java.lang.String _dictName ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: multiple
     * 
     */
    private boolean _multiple  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: options
     * 
     */
    private java.util.List<java.lang.Object> _options = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: optionsSql
     * 
     */
    private java.lang.String _optionsSql ;
    
    /**
     *  
     * xml name: querySpace
     * 
     */
    private java.lang.String _querySpace ;
    
    /**
     *  
     * xml name: required
     * 
     */
    private boolean _required  = false;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.commons.type.StdDataType _type ;
    
    /**
     * 
     * xml name: controlType
     *  
     */
    
    public java.lang.String getControlType(){
      return _controlType;
    }

    
    public void setControlType(java.lang.String value){
        checkAllowChange();
        
        this._controlType = value;
           
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
     * xml name: dependsOn
     *  
     */
    
    public java.lang.String getDependsOn(){
      return _dependsOn;
    }

    
    public void setDependsOn(java.lang.String value){
        checkAllowChange();
        
        this._dependsOn = value;
           
    }

    
    /**
     * 
     * xml name: dictName
     *  
     */
    
    public java.lang.String getDictName(){
      return _dictName;
    }

    
    public void setDictName(java.lang.String value){
        checkAllowChange();
        
        this._dictName = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: multiple
     *  
     */
    
    public boolean isMultiple(){
      return _multiple;
    }

    
    public void setMultiple(boolean value){
        checkAllowChange();
        
        this._multiple = value;
           
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
     * xml name: options
     *  
     */
    
    public java.util.List<java.lang.Object> getOptions(){
      return _options;
    }

    
    public void setOptions(java.util.List<java.lang.Object> value){
        checkAllowChange();
        
        this._options = value;
           
    }

    
    /**
     * 
     * xml name: optionsSql
     *  
     */
    
    public java.lang.String getOptionsSql(){
      return _optionsSql;
    }

    
    public void setOptionsSql(java.lang.String value){
        checkAllowChange();
        
        this._optionsSql = value;
           
    }

    
    /**
     * 
     * xml name: querySpace
     *  
     */
    
    public java.lang.String getQuerySpace(){
      return _querySpace;
    }

    
    public void setQuerySpace(java.lang.String value){
        checkAllowChange();
        
        this._querySpace = value;
           
    }

    
    /**
     * 
     * xml name: required
     *  
     */
    
    public boolean isRequired(){
      return _required;
    }

    
    public void setRequired(boolean value){
        checkAllowChange();
        
        this._required = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.commons.type.StdDataType getType(){
      return _type;
    }

    
    public void setType(io.nop.commons.type.StdDataType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._options = io.nop.api.core.util.FreezeHelper.deepFreeze(this._options);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("controlType",this.getControlType());
        out.putNotNull("defaultValue",this.getDefaultValue());
        out.putNotNull("dependsOn",this.getDependsOn());
        out.putNotNull("dictName",this.getDictName());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("multiple",this.isMultiple());
        out.putNotNull("name",this.getName());
        out.putNotNull("options",this.getOptions());
        out.putNotNull("optionsSql",this.getOptionsSql());
        out.putNotNull("querySpace",this.getQuerySpace());
        out.putNotNull("required",this.isRequired());
        out.putNotNull("type",this.getType());
    }

    public XptParamModel cloneInstance(){
        XptParamModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XptParamModel instance){
        super.copyTo(instance);
        
        instance.setControlType(this.getControlType());
        instance.setDefaultValue(this.getDefaultValue());
        instance.setDependsOn(this.getDependsOn());
        instance.setDictName(this.getDictName());
        instance.setLabel(this.getLabel());
        instance.setMultiple(this.isMultiple());
        instance.setName(this.getName());
        instance.setOptions(this.getOptions());
        instance.setOptionsSql(this.getOptionsSql());
        instance.setQuerySpace(this.getQuerySpace());
        instance.setRequired(this.isRequired());
        instance.setType(this.getType());
    }

    protected XptParamModel newInstance(){
        return (XptParamModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
