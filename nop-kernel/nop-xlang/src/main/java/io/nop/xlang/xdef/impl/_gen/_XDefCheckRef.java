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
        out.putNotNull("prop",this.getProp());
        out.putNotNull("scope",this.getScope());
    }

    public XDefCheckRef cloneInstance(){
        XDefCheckRef instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefCheckRef instance){
        super.copyTo(instance);
        
        instance.setDisallowSelf(this.getDisallowSelf());
        instance.setProp(this.getProp());
        instance.setScope(this.getScope());
    }

    protected XDefCheckRef newInstance(){
        return (XDefCheckRef) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
