package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefCheckRef;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * xdef:check-ref 在指定范围内检查引用合法性。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefCheckRef extends io.nop.xlang.xdef.impl.XDefAbstractCheck {
    
    /**
     *  
     * xml name: disallowSelf
     * 是否禁止引用自身（缺省 false）
     */
    private java.lang.Boolean _disallowSelf  = false;
    
    /**
     *  
     * xml name: keyProp
     * select 选中节点自身的标识属性名，供 disallowSelf 使用（可选）
     */
    private java.lang.String _keyProp ;
    
    /**
     *  
     * xml name: prop
     * 从 select 选中的节点上抽取引用集合（例如 depends）。
     * 引用集合的解析由字段 defType 决定（如 csv-set/csv-list），这里不负责 split。
     */
    private java.lang.String _prop ;
    
    /**
     *  
     * xml name: scope
     * 校验范围（document/siblings/...，由 XDefCheckScope 枚举定义）
     */
    private io.nop.xlang.xdef.XDefCheckScope _scope ;
    
    /**
     *  
     * xml name: targetProp
     * 从 targetSelect 选中节点上抽取 key 的属性名（可选）
     */
    private java.lang.String _targetProp ;
    
    /**
     *  
     * xml name: targetSelect
     * 被引用节点的选择集合（可选，缺省与 select 相同）
     */
    private java.lang.String _targetSelect ;
    
    /**
     * 
     * xml name: disallowSelf
     *  是否禁止引用自身（缺省 false）
     */
    
    public java.lang.Boolean getDisallowSelf(){
      return _disallowSelf;
    }

    
    public void setDisallowSelf(java.lang.Boolean value){
        checkAllowChange();
        
        this._disallowSelf = value;
           
    }

    
    /**
     * 
     * xml name: keyProp
     *  select 选中节点自身的标识属性名，供 disallowSelf 使用（可选）
     */
    
    public java.lang.String getKeyProp(){
      return _keyProp;
    }

    
    public void setKeyProp(java.lang.String value){
        checkAllowChange();
        
        this._keyProp = value;
           
    }

    
    /**
     * 
     * xml name: prop
     *  从 select 选中的节点上抽取引用集合（例如 depends）。
     * 引用集合的解析由字段 defType 决定（如 csv-set/csv-list），这里不负责 split。
     */
    
    public java.lang.String getProp(){
      return _prop;
    }

    
    public void setProp(java.lang.String value){
        checkAllowChange();
        
        this._prop = value;
           
    }

    
    /**
     * 
     * xml name: scope
     *  校验范围（document/siblings/...，由 XDefCheckScope 枚举定义）
     */
    
    public io.nop.xlang.xdef.XDefCheckScope getScope(){
      return _scope;
    }

    
    public void setScope(io.nop.xlang.xdef.XDefCheckScope value){
        checkAllowChange();
        
        this._scope = value;
           
    }

    
    /**
     * 
     * xml name: targetProp
     *  从 targetSelect 选中节点上抽取 key 的属性名（可选）
     */
    
    public java.lang.String getTargetProp(){
      return _targetProp;
    }

    
    public void setTargetProp(java.lang.String value){
        checkAllowChange();
        
        this._targetProp = value;
           
    }

    
    /**
     * 
     * xml name: targetSelect
     *  被引用节点的选择集合（可选，缺省与 select 相同）
     */
    
    public java.lang.String getTargetSelect(){
      return _targetSelect;
    }

    
    public void setTargetSelect(java.lang.String value){
        checkAllowChange();
        
        this._targetSelect = value;
           
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
        
        out.putNotNull("disallowSelf",this.getDisallowSelf());
        out.putNotNull("keyProp",this.getKeyProp());
        out.putNotNull("prop",this.getProp());
        out.putNotNull("scope",this.getScope());
        out.putNotNull("targetProp",this.getTargetProp());
        out.putNotNull("targetSelect",this.getTargetSelect());
    }

    public XDefCheckRef cloneInstance(){
        XDefCheckRef instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefCheckRef instance){
        super.copyTo(instance);
        
        instance.setDisallowSelf(this.getDisallowSelf());
        instance.setKeyProp(this.getKeyProp());
        instance.setProp(this.getProp());
        instance.setScope(this.getScope());
        instance.setTargetProp(this.getTargetProp());
        instance.setTargetSelect(this.getTargetSelect());
    }

    protected XDefCheckRef newInstance(){
        return (XDefCheckRef) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
