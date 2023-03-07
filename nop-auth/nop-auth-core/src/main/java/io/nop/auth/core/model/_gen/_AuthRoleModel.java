package io.nop.auth.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [12:6:0:0]/nop/schema/action-auth.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _AuthRoleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: primary
     * 
     */
    private boolean _primary  = false;
    
    /**
     *  
     * xml name: resource
     * 
     */
    private KeyedList<io.nop.auth.core.model.AuthRoleResourceModel> _resources = KeyedList.emptyList();
    
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
     * xml name: primary
     *  
     */
    
    public boolean isPrimary(){
      return _primary;
    }

    
    public void setPrimary(boolean value){
        checkAllowChange();
        
        this._primary = value;
           
    }

    
    /**
     * 
     * xml name: resource
     *  
     */
    
    public java.util.List<io.nop.auth.core.model.AuthRoleResourceModel> getResources(){
      return _resources;
    }

    
    public void setResources(java.util.List<io.nop.auth.core.model.AuthRoleResourceModel> value){
        checkAllowChange();
        
        this._resources = KeyedList.fromList(value, io.nop.auth.core.model.AuthRoleResourceModel::getId);
           
    }

    
    public io.nop.auth.core.model.AuthRoleResourceModel getResource(String name){
        return this._resources.getByKey(name);
    }

    public boolean hasResource(String name){
        return this._resources.containsKey(name);
    }

    public void addResource(io.nop.auth.core.model.AuthRoleResourceModel item) {
        checkAllowChange();
        java.util.List<io.nop.auth.core.model.AuthRoleResourceModel> list = this.getResources();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.auth.core.model.AuthRoleResourceModel::getId);
            setResources(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_resources(){
        return this._resources.keySet();
    }

    public boolean hasResources(){
        return !this._resources.isEmpty();
    }
    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._resources = io.nop.api.core.util.FreezeHelper.deepFreeze(this._resources);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("name",this.getName());
        out.put("primary",this.isPrimary());
        out.put("resources",this.getResources());
    }
}
 // resume CPD analysis - CPD-ON
