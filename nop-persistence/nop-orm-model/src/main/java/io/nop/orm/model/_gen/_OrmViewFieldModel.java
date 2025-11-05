package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmViewFieldModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/view-entity.xdef <p>
 * 如果指定了formula，则相当于是  formula as fieldName, 否则 相当于 func(owner.sourceProp) as fieldName
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmViewFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: domain
     * 
     */
    private java.lang.String _domain ;
    
    /**
     *  
     * xml name: formula
     * 
     */
    private java.lang.String _formula ;
    
    /**
     *  
     * xml name: func
     * 
     */
    private java.lang.String _func ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: owner
     * 
     */
    private java.lang.String _owner ;
    
    /**
     *  
     * xml name: sourceProp
     * 
     */
    private java.lang.String _sourceProp ;
    
    /**
     *  
     * xml name: stdDataType
     * 
     */
    private io.nop.commons.type.StdDataType _stdDataType ;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
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
     * xml name: formula
     *  
     */
    
    public java.lang.String getFormula(){
      return _formula;
    }

    
    public void setFormula(java.lang.String value){
        checkAllowChange();
        
        this._formula = value;
           
    }

    
    /**
     * 
     * xml name: func
     *  
     */
    
    public java.lang.String getFunc(){
      return _func;
    }

    
    public void setFunc(java.lang.String value){
        checkAllowChange();
        
        this._func = value;
           
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
     * xml name: owner
     *  
     */
    
    public java.lang.String getOwner(){
      return _owner;
    }

    
    public void setOwner(java.lang.String value){
        checkAllowChange();
        
        this._owner = value;
           
    }

    
    /**
     * 
     * xml name: sourceProp
     *  
     */
    
    public java.lang.String getSourceProp(){
      return _sourceProp;
    }

    
    public void setSourceProp(java.lang.String value){
        checkAllowChange();
        
        this._sourceProp = value;
           
    }

    
    /**
     * 
     * xml name: stdDataType
     *  
     */
    
    public io.nop.commons.type.StdDataType getStdDataType(){
      return _stdDataType;
    }

    
    public void setStdDataType(io.nop.commons.type.StdDataType value){
        checkAllowChange();
        
        this._stdDataType = value;
           
    }

    
    /**
     * 
     * xml name: stdDomain
     *  
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
        
        out.putNotNull("comment",this.getComment());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("domain",this.getDomain());
        out.putNotNull("formula",this.getFormula());
        out.putNotNull("func",this.getFunc());
        out.putNotNull("name",this.getName());
        out.putNotNull("owner",this.getOwner());
        out.putNotNull("sourceProp",this.getSourceProp());
        out.putNotNull("stdDataType",this.getStdDataType());
        out.putNotNull("stdDomain",this.getStdDomain());
        out.putNotNull("tagSet",this.getTagSet());
    }

    public OrmViewFieldModel cloneInstance(){
        OrmViewFieldModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmViewFieldModel instance){
        super.copyTo(instance);
        
        instance.setComment(this.getComment());
        instance.setDisplayName(this.getDisplayName());
        instance.setDomain(this.getDomain());
        instance.setFormula(this.getFormula());
        instance.setFunc(this.getFunc());
        instance.setName(this.getName());
        instance.setOwner(this.getOwner());
        instance.setSourceProp(this.getSourceProp());
        instance.setStdDataType(this.getStdDataType());
        instance.setStdDomain(this.getStdDomain());
        instance.setTagSet(this.getTagSet());
    }

    protected OrmViewFieldModel newInstance(){
        return (OrmViewFieldModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
