package io.nop.rule.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rule.core.model.RuleOutputDefineModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [48:10:0:0]/nop/schema/rule.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RuleOutputDefineModel extends io.nop.xlang.xmeta.ObjVarDefineModel {
    
    /**
     *  
     * xml name: aggregate
     * 如果存在多个同名的输出时的汇总方式：max,min, sum等
     */
    private io.nop.rule.core.model.RuleAggregateMethod _aggregate ;
    
    /**
     *  
     * xml name: useWeight
     * 
     */
    private boolean _useWeight  = false;
    
    /**
     * 
     * xml name: aggregate
     *  如果存在多个同名的输出时的汇总方式：max,min, sum等
     */
    
    public io.nop.rule.core.model.RuleAggregateMethod getAggregate(){
      return _aggregate;
    }

    
    public void setAggregate(io.nop.rule.core.model.RuleAggregateMethod value){
        checkAllowChange();
        
        this._aggregate = value;
           
    }

    
    /**
     * 
     * xml name: useWeight
     *  
     */
    
    public boolean isUseWeight(){
      return _useWeight;
    }

    
    public void setUseWeight(boolean value){
        checkAllowChange();
        
        this._useWeight = value;
           
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
        
        out.putNotNull("aggregate",this.getAggregate());
        out.putNotNull("useWeight",this.isUseWeight());
    }

    public RuleOutputDefineModel cloneInstance(){
        RuleOutputDefineModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RuleOutputDefineModel instance){
        super.copyTo(instance);
        
        instance.setAggregate(this.getAggregate());
        instance.setUseWeight(this.isUseWeight());
    }

    protected RuleOutputDefineModel newInstance(){
        return (RuleOutputDefineModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
