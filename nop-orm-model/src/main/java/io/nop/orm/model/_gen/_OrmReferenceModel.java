package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmReferenceModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmReferenceModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: autoCascadeDelete
     * 是否在数据库层面自动实现级联删除
     */
    private boolean _autoCascadeDelete  = false;
    
    /**
     *  
     * xml name: cascadeDelete
     * 删除主表时是否自动删除子表
     */
    private boolean _cascadeDelete  = false;
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: embedded
     * 是否是嵌入在主实体中的关联属性。例如json字段，或者graphql远程存储等情况下
     */
    private boolean _embedded  = false;
    
    /**
     *  
     * xml name: join
     * 必须是关联到相关实体的主键上
     */
    private java.util.List<io.nop.orm.model.OrmJoinOnModel> _join = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: maxBatchLoadSize
     * 
     */
    private java.lang.Integer _maxBatchLoadSize ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: notGenCode
     * 
     */
    private boolean _notGenCode  = false;
    
    /**
     *  
     * xml name: persistDriver
     * 
     */
    private java.lang.String _persistDriver ;
    
    /**
     *  
     * xml name: queryable
     * 是否可以通过eql语法实现关联查询
     */
    private boolean _queryable  = true;
    
    /**
     *  
     * xml name: refDisplayName
     * 
     */
    private java.lang.String _refDisplayName ;
    
    /**
     *  
     * xml name: refEntityName
     * 
     */
    private java.lang.String _refEntityName ;
    
    /**
     *  反向关联属性名
     * xml name: refPropName
     * reference在子表上定义，name为子表上对应主表实体的属性名。如果要求主表也反向关联子表，则
     * refProp可以用来指定主表上反向关联子表的关联属性名。 例如name=parent, refProp=children
     */
    private java.lang.String _refPropName ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: autoCascadeDelete
     *  是否在数据库层面自动实现级联删除
     */
    
    public boolean isAutoCascadeDelete(){
      return _autoCascadeDelete;
    }

    
    public void setAutoCascadeDelete(boolean value){
        checkAllowChange();
        
        this._autoCascadeDelete = value;
           
    }

    
    /**
     * 
     * xml name: cascadeDelete
     *  删除主表时是否自动删除子表
     */
    
    public boolean isCascadeDelete(){
      return _cascadeDelete;
    }

    
    public void setCascadeDelete(boolean value){
        checkAllowChange();
        
        this._cascadeDelete = value;
           
    }

    
    /**
     * 
     * xml name: comment
     *  
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
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
     * xml name: embedded
     *  是否是嵌入在主实体中的关联属性。例如json字段，或者graphql远程存储等情况下
     */
    
    public boolean isEmbedded(){
      return _embedded;
    }

    
    public void setEmbedded(boolean value){
        checkAllowChange();
        
        this._embedded = value;
           
    }

    
    /**
     * 
     * xml name: join
     *  必须是关联到相关实体的主键上
     */
    
    public java.util.List<io.nop.orm.model.OrmJoinOnModel> getJoin(){
      return _join;
    }

    
    public void setJoin(java.util.List<io.nop.orm.model.OrmJoinOnModel> value){
        checkAllowChange();
        
        this._join = value;
           
    }

    
    /**
     * 
     * xml name: maxBatchLoadSize
     *  
     */
    
    public java.lang.Integer getMaxBatchLoadSize(){
      return _maxBatchLoadSize;
    }

    
    public void setMaxBatchLoadSize(java.lang.Integer value){
        checkAllowChange();
        
        this._maxBatchLoadSize = value;
           
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
     * xml name: notGenCode
     *  
     */
    
    public boolean isNotGenCode(){
      return _notGenCode;
    }

    
    public void setNotGenCode(boolean value){
        checkAllowChange();
        
        this._notGenCode = value;
           
    }

    
    /**
     * 
     * xml name: persistDriver
     *  
     */
    
    public java.lang.String getPersistDriver(){
      return _persistDriver;
    }

    
    public void setPersistDriver(java.lang.String value){
        checkAllowChange();
        
        this._persistDriver = value;
           
    }

    
    /**
     * 
     * xml name: queryable
     *  是否可以通过eql语法实现关联查询
     */
    
    public boolean isQueryable(){
      return _queryable;
    }

    
    public void setQueryable(boolean value){
        checkAllowChange();
        
        this._queryable = value;
           
    }

    
    /**
     * 
     * xml name: refDisplayName
     *  
     */
    
    public java.lang.String getRefDisplayName(){
      return _refDisplayName;
    }

    
    public void setRefDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._refDisplayName = value;
           
    }

    
    /**
     * 
     * xml name: refEntityName
     *  
     */
    
    public java.lang.String getRefEntityName(){
      return _refEntityName;
    }

    
    public void setRefEntityName(java.lang.String value){
        checkAllowChange();
        
        this._refEntityName = value;
           
    }

    
    /**
     * 反向关联属性名
     * xml name: refPropName
     *  reference在子表上定义，name为子表上对应主表实体的属性名。如果要求主表也反向关联子表，则
     * refProp可以用来指定主表上反向关联子表的关联属性名。 例如name=parent, refProp=children
     */
    
    public java.lang.String getRefPropName(){
      return _refPropName;
    }

    
    public void setRefPropName(java.lang.String value){
        checkAllowChange();
        
        this._refPropName = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._join = io.nop.api.core.util.FreezeHelper.deepFreeze(this._join);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("autoCascadeDelete",this.isAutoCascadeDelete());
        out.putNotNull("cascadeDelete",this.isCascadeDelete());
        out.putNotNull("comment",this.getComment());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("embedded",this.isEmbedded());
        out.putNotNull("join",this.getJoin());
        out.putNotNull("maxBatchLoadSize",this.getMaxBatchLoadSize());
        out.putNotNull("name",this.getName());
        out.putNotNull("notGenCode",this.isNotGenCode());
        out.putNotNull("persistDriver",this.getPersistDriver());
        out.putNotNull("queryable",this.isQueryable());
        out.putNotNull("refDisplayName",this.getRefDisplayName());
        out.putNotNull("refEntityName",this.getRefEntityName());
        out.putNotNull("refPropName",this.getRefPropName());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("type",this.getType());
    }

    public OrmReferenceModel cloneInstance(){
        OrmReferenceModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmReferenceModel instance){
        super.copyTo(instance);
        
        instance.setAutoCascadeDelete(this.isAutoCascadeDelete());
        instance.setCascadeDelete(this.isCascadeDelete());
        instance.setComment(this.getComment());
        instance.setDisplayName(this.getDisplayName());
        instance.setEmbedded(this.isEmbedded());
        instance.setJoin(this.getJoin());
        instance.setMaxBatchLoadSize(this.getMaxBatchLoadSize());
        instance.setName(this.getName());
        instance.setNotGenCode(this.isNotGenCode());
        instance.setPersistDriver(this.getPersistDriver());
        instance.setQueryable(this.isQueryable());
        instance.setRefDisplayName(this.getRefDisplayName());
        instance.setRefEntityName(this.getRefEntityName());
        instance.setRefPropName(this.getRefPropName());
        instance.setTagSet(this.getTagSet());
        instance.setType(this.getType());
    }

    protected OrmReferenceModel newInstance(){
        return (OrmReferenceModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
