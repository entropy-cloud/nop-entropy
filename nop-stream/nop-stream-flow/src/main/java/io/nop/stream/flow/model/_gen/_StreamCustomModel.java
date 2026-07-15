package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamCustomModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 自定义算子节点
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamCustomModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: customType
     * 
     */
    private java.lang.String _customType ;
    
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
     * xml name: customType
     *  
     */
    
    public java.lang.String getCustomType(){
      return _customType;
    }

    
    public void setCustomType(java.lang.String value){
        checkAllowChange();
        
        this._customType = value;
           
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
        
        out.putNotNull("customType",this.getCustomType());
        out.putNotNull("params",this.getParams());
        out.putNotNull("source",this.getSource());
        out.putNotNull("type",this.getType());
    }

    public StreamCustomModel cloneInstance(){
        StreamCustomModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamCustomModel instance){
        super.copyTo(instance);
        
        instance.setCustomType(this.getCustomType());
        instance.setParams(this.getParams());
        instance.setSource(this.getSource());
        instance.setType(this.getType());
    }

    protected StreamCustomModel newInstance(){
        return (StreamCustomModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
