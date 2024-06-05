package io.nop.auth.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.auth.core.model.ObjDataAuthModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [14:10:0:0]/nop/schema/data-auth.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ObjDataAuthModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: role-auths
     * 
     */
    private KeyedList<io.nop.auth.core.model.RoleDataAuthModel> _roleAuths = KeyedList.emptyList();
    
    /**
     *  
     * xml name: role-decider
     * 动态确定角色，如果返回结果非空，则它将取代IUserContext上的角色设置
     */
    private io.nop.core.lang.eval.IEvalAction _roleDecider ;
    
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
     * xml name: role-auths
     *  
     */
    
    public java.util.List<io.nop.auth.core.model.RoleDataAuthModel> getRoleAuths(){
      return _roleAuths;
    }

    
    public void setRoleAuths(java.util.List<io.nop.auth.core.model.RoleDataAuthModel> value){
        checkAllowChange();
        
        this._roleAuths = KeyedList.fromList(value, io.nop.auth.core.model.RoleDataAuthModel::getId);
           
    }

    
    public io.nop.auth.core.model.RoleDataAuthModel getRoleAuth(String name){
        return this._roleAuths.getByKey(name);
    }

    public boolean hasRoleAuth(String name){
        return this._roleAuths.containsKey(name);
    }

    public void addRoleAuth(io.nop.auth.core.model.RoleDataAuthModel item) {
        checkAllowChange();
        java.util.List<io.nop.auth.core.model.RoleDataAuthModel> list = this.getRoleAuths();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.auth.core.model.RoleDataAuthModel::getId);
            setRoleAuths(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_roleAuths(){
        return this._roleAuths.keySet();
    }

    public boolean hasRoleAuths(){
        return !this._roleAuths.isEmpty();
    }
    
    /**
     * 
     * xml name: role-decider
     *  动态确定角色，如果返回结果非空，则它将取代IUserContext上的角色设置
     */
    
    public io.nop.core.lang.eval.IEvalAction getRoleDecider(){
      return _roleDecider;
    }

    
    public void setRoleDecider(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._roleDecider = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._roleAuths = io.nop.api.core.util.FreezeHelper.deepFreeze(this._roleAuths);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("name",this.getName());
        out.putNotNull("roleAuths",this.getRoleAuths());
        out.putNotNull("roleDecider",this.getRoleDecider());
    }

    public ObjDataAuthModel cloneInstance(){
        ObjDataAuthModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ObjDataAuthModel instance){
        super.copyTo(instance);
        
        instance.setDisplayName(this.getDisplayName());
        instance.setName(this.getName());
        instance.setRoleAuths(this.getRoleAuths());
        instance.setRoleDecider(this.getRoleDecider());
    }

    protected ObjDataAuthModel newInstance(){
        return (ObjDataAuthModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
