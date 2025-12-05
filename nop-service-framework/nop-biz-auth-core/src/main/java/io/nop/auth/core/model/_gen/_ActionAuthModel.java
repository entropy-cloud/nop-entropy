package io.nop.auth.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.auth.core.model.ActionAuthModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/action-auth.xdef <p>
 * 用于描述用户对页面以及页面上的操作的权限范围。页面和按钮被统一抽象为Resource概念。用户具有角色，角色可以访问指定资源，
 * 具体的资源通过action与系统内部的程序逻辑关联起来。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ActionAuthModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: role
     * 
     */
    private KeyedList<io.nop.auth.core.model.AuthRoleModel> _roles = KeyedList.emptyList();
    
    /**
     *  
     * xml name: site
     * 
     */
    private KeyedList<io.nop.auth.api.messages.SiteMapBean> _sites = KeyedList.emptyList();
    
    /**
     * 
     * xml name: role
     *  
     */
    
    public java.util.List<io.nop.auth.core.model.AuthRoleModel> getRoles(){
      return _roles;
    }

    
    public void setRoles(java.util.List<io.nop.auth.core.model.AuthRoleModel> value){
        checkAllowChange();
        
        this._roles = KeyedList.fromList(value, io.nop.auth.core.model.AuthRoleModel::getName);
           
    }

    
    public io.nop.auth.core.model.AuthRoleModel getRole(String name){
        return this._roles.getByKey(name);
    }

    public boolean hasRole(String name){
        return this._roles.containsKey(name);
    }

    public void addRole(io.nop.auth.core.model.AuthRoleModel item) {
        checkAllowChange();
        java.util.List<io.nop.auth.core.model.AuthRoleModel> list = this.getRoles();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.auth.core.model.AuthRoleModel::getName);
            setRoles(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_roles(){
        return this._roles.keySet();
    }

    public boolean hasRoles(){
        return !this._roles.isEmpty();
    }
    
    /**
     * 
     * xml name: site
     *  
     */
    
    public java.util.List<io.nop.auth.api.messages.SiteMapBean> getSites(){
      return _sites;
    }

    
    public void setSites(java.util.List<io.nop.auth.api.messages.SiteMapBean> value){
        checkAllowChange();
        
        this._sites = KeyedList.fromList(value, io.nop.auth.api.messages.SiteMapBean::getId);
           
    }

    
    public io.nop.auth.api.messages.SiteMapBean getSite(String name){
        return this._sites.getByKey(name);
    }

    public boolean hasSite(String name){
        return this._sites.containsKey(name);
    }

    public void addSite(io.nop.auth.api.messages.SiteMapBean item) {
        checkAllowChange();
        java.util.List<io.nop.auth.api.messages.SiteMapBean> list = this.getSites();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.auth.api.messages.SiteMapBean::getId);
            setSites(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sites(){
        return this._sites.keySet();
    }

    public boolean hasSites(){
        return !this._sites.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._roles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._roles);
            
           this._sites = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sites);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("roles",this.getRoles());
        out.putNotNull("sites",this.getSites());
    }

    public ActionAuthModel cloneInstance(){
        ActionAuthModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ActionAuthModel instance){
        super.copyTo(instance);
        
        instance.setRoles(this.getRoles());
        instance.setSites(this.getSites());
    }

    protected ActionAuthModel newInstance(){
        return (ActionAuthModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
