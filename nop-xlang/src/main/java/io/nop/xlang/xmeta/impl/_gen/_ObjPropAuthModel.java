package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [74:14:0:0]/nop/schema/schema/obj-schema.xdef <p>
 * 配置字段级别的权限约束
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
public abstract class _ObjPropAuthModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: for
     * 如果为all，则表示所有操作都可以匹配这个权限约束。
     * 如果设置为read，则表示当读取的时候使用此约束。此时如果没有配置write所对应的auth，则实际不允许修改
     * 如果设置为write，则表示读取和修改的时候都使用此约束
     */
    private java.lang.String _for ;
    
    /**
     *  
     * xml name: permissions
     * 
     */
    private io.nop.api.core.util.MultiCsvSet _permissions ;
    
    /**
     *  
     * xml name: roles
     * 
     */
    private java.util.Set<java.lang.String> _roles ;
    
    /**
     * 
     * xml name: for
     *  如果为all，则表示所有操作都可以匹配这个权限约束。
     * 如果设置为read，则表示当读取的时候使用此约束。此时如果没有配置write所对应的auth，则实际不允许修改
     * 如果设置为write，则表示读取和修改的时候都使用此约束
     */
    
    public java.lang.String getFor(){
      return _for;
    }

    
    public void setFor(java.lang.String value){
        checkAllowChange();
        
        this._for = value;
           
    }

    
    /**
     * 
     * xml name: permissions
     *  
     */
    
    public io.nop.api.core.util.MultiCsvSet getPermissions(){
      return _permissions;
    }

    
    public void setPermissions(io.nop.api.core.util.MultiCsvSet value){
        checkAllowChange();
        
        this._permissions = value;
           
    }

    
    /**
     * 
     * xml name: roles
     *  
     */
    
    public java.util.Set<java.lang.String> getRoles(){
      return _roles;
    }

    
    public void setRoles(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._roles = value;
           
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
        
        out.put("for",this.getFor());
        out.put("permissions",this.getPermissions());
        out.put("roles",this.getRoles());
    }
}
 // resume CPD analysis - CPD-ON
