package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamAggregateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * Aggregate 节点：实现 AggregateFunction 接口。WI8c：bean 与 aggregatorRef
 * 恰好其一（并存或双缺构建期 fail-fast）；aggregatorRef 引用 <aggregators>
 * 注册表 entry，累加语义由聚合求值器提供。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamAggregateModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: aggregatorRef
     * 
     */
    private java.lang.String _aggregatorRef ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: aggregatorRef
     *  
     */
    
    public java.lang.String getAggregatorRef(){
      return _aggregatorRef;
    }

    
    public void setAggregatorRef(java.lang.String value){
        checkAllowChange();
        
        this._aggregatorRef = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("aggregatorRef",this.getAggregatorRef());
        out.putNotNull("type",this.getType());
    }

    public StreamAggregateModel cloneInstance(){
        StreamAggregateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamAggregateModel instance){
        super.copyTo(instance);
        
        instance.setAggregatorRef(this.getAggregatorRef());
        instance.setType(this.getType());
    }

    protected StreamAggregateModel newInstance(){
        return (StreamAggregateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
