package io.nop.xlang.xmeta.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xmeta.impl.ObjConditionExpr;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [91:14:0:0]/nop/schema/schema/obj-schema.xdef <p>
 * 新增或者修改的时候如果前台没有发送本字段的值，则可以根据autoExpr来自动计算得到
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ObjConditionExpr extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     *  
     * xml name: when
     * 
     */
    private java.util.Set<java.lang.String> _when ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public java.util.Set<java.lang.String> getWhen(){
      return _when;
    }

    
    public void setWhen(java.util.Set<java.lang.String> value){
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
        
        out.put("source",this.getSource());
        out.put("when",this.getWhen());
    }

    public ObjConditionExpr cloneInstance(){
        ObjConditionExpr instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ObjConditionExpr instance){
        super.copyTo(instance);
        
        instance.setSource(this.getSource());
        instance.setWhen(this.getWhen());
    }

    protected ObjConditionExpr newInstance(){
        return (ObjConditionExpr) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
