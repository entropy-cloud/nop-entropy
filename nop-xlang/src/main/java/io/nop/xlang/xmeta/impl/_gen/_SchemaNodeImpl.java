package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xmeta.impl.SchemaNodeImpl;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/schema/schema-node.xdef <p>
 * schema节点的基类
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SchemaNodeImpl extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bizObjName
     * 对应GraphQL中的对象名
     */
    private java.lang.String _bizObjName ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  显示名称
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: domain
     * 
     */
    private java.lang.String _domain ;
    
    /**
     *  
     * xml name: id
     * 内部处理循环引用时所使用的属性，应用层不应使用。
     */
    private java.lang.String _id ;
    
    /**
     *  名称
     * xml name: name
     * 对应于SimpleClassName
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: ref
     * 
     */
    private java.lang.String _ref ;
    
    /**
     *  
     * xml name: refResolved
     * 
     */
    private java.lang.Boolean _refResolved ;
    
    /**
     *  标准域
     * xml name: stdDomain
     * 对应于StdDomainRegistry中注册标准类型。参见std-domain.dict.yaml中的说明
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: validator
     * 
     */
    private io.nop.core.model.validator.ValidatorModel _validator ;
    
    /**
     * 
     * xml name: bizObjName
     *  对应GraphQL中的对象名
     */
    
    public java.lang.String getBizObjName(){
      return _bizObjName;
    }

    
    public void setBizObjName(java.lang.String value){
        checkAllowChange();
        
        this._bizObjName = value;
           
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
     * 显示名称
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
     * xml name: domain
     *  
     */
    
    public java.lang.String getDomain(){
      return _domain;
    }

    
    public void setDomain(java.lang.String value){
        checkAllowChange();
        
        this._domain = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  内部处理循环引用时所使用的属性，应用层不应使用。
     */
    @Deprecated
    public java.lang.String getId(){
      return _id;
    }

    @Deprecated
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 名称
     * xml name: name
     *  对应于SimpleClassName
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
     * xml name: ref
     *  
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
    }

    
    /**
     * 
     * xml name: refResolved
     *  
     */
    @Deprecated
    public java.lang.Boolean getRefResolved(){
      return _refResolved;
    }

    @Deprecated
    public void setRefResolved(java.lang.Boolean value){
        checkAllowChange();
        
        this._refResolved = value;
           
    }

    
    /**
     * 标准域
     * xml name: stdDomain
     *  对应于StdDomainRegistry中注册标准类型。参见std-domain.dict.yaml中的说明
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
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
     * xml name: validator
     *  
     */
    
    public io.nop.core.model.validator.ValidatorModel getValidator(){
      return _validator;
    }

    
    public void setValidator(io.nop.core.model.validator.ValidatorModel value){
        checkAllowChange();
        
        this._validator = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._validator = io.nop.api.core.util.FreezeHelper.deepFreeze(this._validator);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("bizObjName",this.getBizObjName());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("domain",this.getDomain());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
        out.putNotNull("ref",this.getRef());
        out.putNotNull("refResolved",this.getRefResolved());
        out.putNotNull("stdDomain",this.getStdDomain());
        out.putNotNull("type",this.getType());
        out.putNotNull("validator",this.getValidator());
    }

    public SchemaNodeImpl cloneInstance(){
        SchemaNodeImpl instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SchemaNodeImpl instance){
        super.copyTo(instance);
        
        instance.setBizObjName(this.getBizObjName());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setDomain(this.getDomain());
        instance.setId(this.getId());
        instance.setName(this.getName());
        instance.setRef(this.getRef());
        instance.setRefResolved(this.getRefResolved());
        instance.setStdDomain(this.getStdDomain());
        instance.setType(this.getType());
        instance.setValidator(this.getValidator());
    }

    protected SchemaNodeImpl newInstance(){
        return (SchemaNodeImpl) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
