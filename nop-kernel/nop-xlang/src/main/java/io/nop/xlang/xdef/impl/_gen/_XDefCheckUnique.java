package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefCheckUnique;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * xdef:check-unique 在指定范围内检查唯一性。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefCheckUnique extends io.nop.xlang.xdef.impl.XDefAbstractCheck {
    
    /**
     *  
     * xml name: prop
     * 从 select 选中的节点获取的唯一属性名
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
     * xml name: prop
     *  从 select 选中的节点获取的唯一属性名
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
        
        out.putNotNull("prop",this.getProp());
        out.putNotNull("scope",this.getScope());
    }

    public XDefCheckUnique cloneInstance(){
        XDefCheckUnique instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefCheckUnique instance){
        super.copyTo(instance);
        
        instance.setProp(this.getProp());
        instance.setScope(this.getScope());
    }

    protected XDefCheckUnique newInstance(){
        return (XDefCheckUnique) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
