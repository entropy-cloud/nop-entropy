package io.nop.auth.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.auth.core.model.RoleDataAuthModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/data-auth.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RoleDataAuthModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: check
     * 检查单个实体是否满足数据权限要求
     */
    private io.nop.core.lang.eval.IEvalPredicate _check ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: filter
     * 增加数据权限过滤条件，例如 <eq name="internal" value="0" />
     */
    private io.nop.core.lang.xml.IXNodeGenerator _filter ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  权限规则优先级
     * xml name: priority
     * 如果一个用户存在多个角色，则按照优先级高的权限约束规则执行。
     * 如果多个规则具有相同优先级，则只执行第一条匹配的过滤规则
     */
    private int _priority  = 100;
    
    /**
     *  角色id
     * xml name: roleIds
     * 哪些角色会应用这个权限条目
     */
    private java.util.Set<java.lang.String> _roleIds ;
    
    /**
     *  
     * xml name: when
     * 动态判断是否应用该权限条目
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: check
     *  检查单个实体是否满足数据权限要求
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getCheck(){
      return _check;
    }

    
    public void setCheck(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._check = value;
           
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
     * 
     * xml name: filter
     *  增加数据权限过滤条件，例如 <eq name="internal" value="0" />
     */
    
    public io.nop.core.lang.xml.IXNodeGenerator getFilter(){
      return _filter;
    }

    
    public void setFilter(io.nop.core.lang.xml.IXNodeGenerator value){
        checkAllowChange();
        
        this._filter = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 权限规则优先级
     * xml name: priority
     *  如果一个用户存在多个角色，则按照优先级高的权限约束规则执行。
     * 如果多个规则具有相同优先级，则只执行第一条匹配的过滤规则
     */
    
    public int getPriority(){
      return _priority;
    }

    
    public void setPriority(int value){
        checkAllowChange();
        
        this._priority = value;
           
    }

    
    /**
     * 角色id
     * xml name: roleIds
     *  哪些角色会应用这个权限条目
     */
    
    public java.util.Set<java.lang.String> getRoleIds(){
      return _roleIds;
    }

    
    public void setRoleIds(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._roleIds = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  动态判断是否应用该权限条目
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._when = value;
           
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
        
        out.putNotNull("check",this.getCheck());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("id",this.getId());
        out.putNotNull("priority",this.getPriority());
        out.putNotNull("roleIds",this.getRoleIds());
        out.putNotNull("when",this.getWhen());
    }

    public RoleDataAuthModel cloneInstance(){
        RoleDataAuthModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RoleDataAuthModel instance){
        super.copyTo(instance);
        
        instance.setCheck(this.getCheck());
        instance.setDescription(this.getDescription());
        instance.setFilter(this.getFilter());
        instance.setId(this.getId());
        instance.setPriority(this.getPriority());
        instance.setRoleIds(this.getRoleIds());
        instance.setWhen(this.getWhen());
    }

    protected RoleDataAuthModel newInstance(){
        return (RoleDataAuthModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
