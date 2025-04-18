package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 解析响应消息，得到结果变量保存到AiResultMessage上
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptOutputModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: parseBeforeProcess
     * 是否在processChatResponse调用之前解析
     */
    private boolean _parseBeforeProcess  = false;
    
    /**
     *  
     * xml name: parseFromResponse
     * 
     */
    private io.nop.ai.core.model.PromptOutputParseModel _parseFromResponse ;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: skipWhenResponseInvalid
     * 当AiChatResponse为invalid状态时，跳过此输出变量的解析
     */
    private boolean _skipWhenResponseInvalid  = false;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: xdef
     * 
     */
    private io.nop.core.lang.xml.XNode _xdef ;
    
    /**
     *  
     * xml name: xdefPath
     * 
     */
    private java.lang.String _xdefPath ;
    
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
     * xml name: parseBeforeProcess
     *  是否在processChatResponse调用之前解析
     */
    
    public boolean isParseBeforeProcess(){
      return _parseBeforeProcess;
    }

    
    public void setParseBeforeProcess(boolean value){
        checkAllowChange();
        
        this._parseBeforeProcess = value;
           
    }

    
    /**
     * 
     * xml name: parseFromResponse
     *  
     */
    
    public io.nop.ai.core.model.PromptOutputParseModel getParseFromResponse(){
      return _parseFromResponse;
    }

    
    public void setParseFromResponse(io.nop.ai.core.model.PromptOutputParseModel value){
        checkAllowChange();
        
        this._parseFromResponse = value;
           
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

    
    /**
     * 
     * xml name: skipWhenResponseInvalid
     *  当AiChatResponse为invalid状态时，跳过此输出变量的解析
     */
    
    public boolean isSkipWhenResponseInvalid(){
      return _skipWhenResponseInvalid;
    }

    
    public void setSkipWhenResponseInvalid(boolean value){
        checkAllowChange();
        
        this._skipWhenResponseInvalid = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: xdef
     *  
     */
    
    public io.nop.core.lang.xml.XNode getXdef(){
      return _xdef;
    }

    
    public void setXdef(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._xdef = value;
           
    }

    
    /**
     * 
     * xml name: xdefPath
     *  
     */
    
    public java.lang.String getXdefPath(){
      return _xdefPath;
    }

    
    public void setXdefPath(java.lang.String value){
        checkAllowChange();
        
        this._xdefPath = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._parseFromResponse = io.nop.api.core.util.FreezeHelper.deepFreeze(this._parseFromResponse);
            
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("name",this.getName());
        out.putNotNull("optional",this.isOptional());
        out.putNotNull("parseBeforeProcess",this.isParseBeforeProcess());
        out.putNotNull("parseFromResponse",this.getParseFromResponse());
        out.putNotNull("schema",this.getSchema());
        out.putNotNull("skipWhenResponseInvalid",this.isSkipWhenResponseInvalid());
        out.putNotNull("type",this.getType());
        out.putNotNull("xdef",this.getXdef());
        out.putNotNull("xdefPath",this.getXdefPath());
    }

    public PromptOutputModel cloneInstance(){
        PromptOutputModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptOutputModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setMandatory(this.isMandatory());
        instance.setName(this.getName());
        instance.setOptional(this.isOptional());
        instance.setParseBeforeProcess(this.isParseBeforeProcess());
        instance.setParseFromResponse(this.getParseFromResponse());
        instance.setSchema(this.getSchema());
        instance.setSkipWhenResponseInvalid(this.isSkipWhenResponseInvalid());
        instance.setType(this.getType());
        instance.setXdef(this.getXdef());
        instance.setXdefPath(this.getXdefPath());
    }

    protected PromptOutputModel newInstance(){
        return (PromptOutputModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
