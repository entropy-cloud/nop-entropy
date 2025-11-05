package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmPackageModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/orm.xdef <p>
 * package仅仅作为界面组织手段
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmPackageModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: diagram
     * 
     */
    private java.lang.Object _diagram ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: entities
     * 
     */
    private java.util.Set<java.lang.String> _entities ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
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
     * xml name: diagram
     *  
     */
    
    public java.lang.Object getDiagram(){
      return _diagram;
    }

    
    public void setDiagram(java.lang.Object value){
        checkAllowChange();
        
        this._diagram = value;
           
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
     * xml name: entities
     *  
     */
    
    public java.util.Set<java.lang.String> getEntities(){
      return _entities;
    }

    
    public void setEntities(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._entities = value;
           
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
        out.putNotNull("diagram",this.getDiagram());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("entities",this.getEntities());
        out.putNotNull("name",this.getName());
    }

    public OrmPackageModel cloneInstance(){
        OrmPackageModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmPackageModel instance){
        super.copyTo(instance);
        
        instance.setComment(this.getComment());
        instance.setDiagram(this.getDiagram());
        instance.setDisplayName(this.getDisplayName());
        instance.setEntities(this.getEntities());
        instance.setName(this.getName());
    }

    protected OrmPackageModel newInstance(){
        return (OrmPackageModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
