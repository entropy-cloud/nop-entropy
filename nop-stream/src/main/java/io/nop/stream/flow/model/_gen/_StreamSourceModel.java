package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * Source 节点：实现 SourceFunction 接口
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSourceModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: consistencyCapability
     * 
     */
    private io.nop.stream.core.common.functions.source.SourceConsistencyCapability _consistencyCapability ;
    
    /**
     *  
     * xml name: maxParallelism
     * 
     */
    private int _maxParallelism  = 0;
    
    /**
     *  
     * xml name: outputType
     * 
     */
    private io.nop.core.type.IGenericType _outputType ;
    
    /**
     *  
     * xml name: params
     * 
     */
    private KeyedList<io.nop.stream.flow.model.StreamParamModel> _params = KeyedList.emptyList();
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _source ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: consistencyCapability
     *  
     */
    
    public io.nop.stream.core.common.functions.source.SourceConsistencyCapability getConsistencyCapability(){
      return _consistencyCapability;
    }

    
    public void setConsistencyCapability(io.nop.stream.core.common.functions.source.SourceConsistencyCapability value){
        checkAllowChange();
        
        this._consistencyCapability = value;
           
    }

    
    /**
     * 
     * xml name: maxParallelism
     *  
     */
    
    public int getMaxParallelism(){
      return _maxParallelism;
    }

    
    public void setMaxParallelism(int value){
        checkAllowChange();
        
        this._maxParallelism = value;
           
    }

    
    /**
     * 
     * xml name: outputType
     *  
     */
    
    public io.nop.core.type.IGenericType getOutputType(){
      return _outputType;
    }

    
    public void setOutputType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._outputType = value;
           
    }

    
    /**
     * 
     * xml name: params
     *  
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamParamModel> getParams(){
      return _params;
    }

    
    public void setParams(java.util.List<io.nop.stream.flow.model.StreamParamModel> value){
        checkAllowChange();
        
        this._params = KeyedList.fromList(value, io.nop.stream.flow.model.StreamParamModel::getName);
           
    }

    
    public io.nop.stream.flow.model.StreamParamModel getParam(String name){
        return this._params.getByKey(name);
    }

    public boolean hasParam(String name){
        return this._params.containsKey(name);
    }

    public void addParam(io.nop.stream.flow.model.StreamParamModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamParamModel> list = this.getParams();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamParamModel::getName);
            setParams(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_params(){
        return this._params.keySet();
    }

    public boolean hasParams(){
        return !this._params.isEmpty();
    }
    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._source = value;
           
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
        
           this._params = io.nop.api.core.util.FreezeHelper.deepFreeze(this._params);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("consistencyCapability",this.getConsistencyCapability());
        out.putNotNull("maxParallelism",this.getMaxParallelism());
        out.putNotNull("outputType",this.getOutputType());
        out.putNotNull("params",this.getParams());
        out.putNotNull("source",this.getSource());
        out.putNotNull("type",this.getType());
    }

    public StreamSourceModel cloneInstance(){
        StreamSourceModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSourceModel instance){
        super.copyTo(instance);
        
        instance.setConsistencyCapability(this.getConsistencyCapability());
        instance.setMaxParallelism(this.getMaxParallelism());
        instance.setOutputType(this.getOutputType());
        instance.setParams(this.getParams());
        instance.setSource(this.getSource());
        instance.setType(this.getType());
    }

    protected StreamSourceModel newInstance(){
        return (StreamSourceModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
