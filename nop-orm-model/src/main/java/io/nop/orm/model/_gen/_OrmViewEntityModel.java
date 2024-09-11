package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmViewEntityModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/view-entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmViewEntityModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: className
     * 
     */
    private java.lang.String _className ;
    
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
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.orm.model.OrmViewFieldModel> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: member-entity
     * 
     */
    private KeyedList<io.nop.orm.model.OrmViewMemberEntityModel> _memberEntities = KeyedList.emptyList();
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: view-link
     * 
     */
    private io.nop.orm.model.OrmViewLinkModel _viewLink ;
    
    /**
     * 
     * xml name: className
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
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
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.orm.model.OrmViewFieldModel> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.orm.model.OrmViewFieldModel> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.orm.model.OrmViewFieldModel::getName);
           
    }

    
    public io.nop.orm.model.OrmViewFieldModel getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.orm.model.OrmViewFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.model.OrmViewFieldModel> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.model.OrmViewFieldModel::getName);
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
     * xml name: member-entity
     *  
     */
    
    public java.util.List<io.nop.orm.model.OrmViewMemberEntityModel> getMemberEntities(){
      return _memberEntities;
    }

    
    public void setMemberEntities(java.util.List<io.nop.orm.model.OrmViewMemberEntityModel> value){
        checkAllowChange();
        
        this._memberEntities = KeyedList.fromList(value, io.nop.orm.model.OrmViewMemberEntityModel::getAlias);
           
    }

    
    public io.nop.orm.model.OrmViewMemberEntityModel getMemberEntity(String name){
        return this._memberEntities.getByKey(name);
    }

    public boolean hasMemberEntity(String name){
        return this._memberEntities.containsKey(name);
    }

    public void addMemberEntity(io.nop.orm.model.OrmViewMemberEntityModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.model.OrmViewMemberEntityModel> list = this.getMemberEntities();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.model.OrmViewMemberEntityModel::getAlias);
            setMemberEntities(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_memberEntities(){
        return this._memberEntities.keySet();
    }

    public boolean hasMemberEntities(){
        return !this._memberEntities.isEmpty();
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
     * xml name: view-link
     *  
     */
    
    public io.nop.orm.model.OrmViewLinkModel getViewLink(){
      return _viewLink;
    }

    
    public void setViewLink(io.nop.orm.model.OrmViewLinkModel value){
        checkAllowChange();
        
        this._viewLink = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._memberEntities = io.nop.api.core.util.FreezeHelper.deepFreeze(this._memberEntities);
            
           this._viewLink = io.nop.api.core.util.FreezeHelper.deepFreeze(this._viewLink);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("className",this.getClassName());
        out.putNotNull("comment",this.getComment());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("memberEntities",this.getMemberEntities());
        out.putNotNull("name",this.getName());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("viewLink",this.getViewLink());
    }

    public OrmViewEntityModel cloneInstance(){
        OrmViewEntityModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmViewEntityModel instance){
        super.copyTo(instance);
        
        instance.setClassName(this.getClassName());
        instance.setComment(this.getComment());
        instance.setDisplayName(this.getDisplayName());
        instance.setFields(this.getFields());
        instance.setMemberEntities(this.getMemberEntities());
        instance.setName(this.getName());
        instance.setTagSet(this.getTagSet());
        instance.setViewLink(this.getViewLink());
    }

    protected OrmViewEntityModel newInstance(){
        return (OrmViewEntityModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
