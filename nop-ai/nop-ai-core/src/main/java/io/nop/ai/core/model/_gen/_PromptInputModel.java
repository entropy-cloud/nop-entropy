package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 声明模板中使用的变量信息。主要用于模板管理
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptInputModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _defaultExpr ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: optional
     * 
     */
    private boolean _optional  = false;
    
    /**
     *  
     * xml name: parseFromMessage
     * 
     */
    private io.nop.ai.core.model.PromptInputParseModel _parseFromMessage ;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     * 
     * xml name: defaultExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getDefaultExpr(){
      return _defaultExpr;
    }

    
    public void setDefaultExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._defaultExpr = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
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
     * xml name: optional
     *  
     */
    
    public boolean isOptional(){
      return _optional;
    }

    
    public void setOptional(boolean value){
        checkAllowChange();
        
        this._optional = value;
           
    }

    
    /**
     * 
     * xml name: parseFromMessage
     *  
     */
    
    public io.nop.ai.core.model.PromptInputParseModel getParseFromMessage(){
      return _parseFromMessage;
    }

    
    public void setParseFromMessage(io.nop.ai.core.model.PromptInputParseModel value){
        checkAllowChange();
        
        this._parseFromMessage = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    
    public io.nop.xlang.xmeta.ISchema getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.xlang.xmeta.ISchema value){
        checkAllowChange();
        
        this._schema = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._parseFromMessage = io.nop.api.core.util.FreezeHelper.deepFreeze(this._parseFromMessage);
            
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("defaultExpr",this.getDefaultExpr());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("name",this.getName());
        out.putNotNull("optional",this.isOptional());
        out.putNotNull("parseFromMessage",this.getParseFromMessage());
        out.putNotNull("schema",this.getSchema());
    }

    public PromptInputModel cloneInstance(){
        PromptInputModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptInputModel instance){
        super.copyTo(instance);
        
        instance.setDefaultExpr(this.getDefaultExpr());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setMandatory(this.isMandatory());
        instance.setName(this.getName());
        instance.setOptional(this.isOptional());
        instance.setParseFromMessage(this.getParseFromMessage());
        instance.setSchema(this.getSchema());
    }

    protected PromptInputModel newInstance(){
        return (PromptInputModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
