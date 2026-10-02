package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSqlModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * WI17 (plan 25 r2 B4): <sql> 是模型级生成器而非 transform——顶层元素，编译发生在
 * builder.build() 的 buildTransforms 之前：SPI（ISqlStreamCompiler，nop-stream-flow
 * 定义，nop-stream-sql 提供实现）把 SQL 文本编译为完整流模型 XML，builder 解析回读并
 * 替换父模型内容。<sql> 必须是模型唯一内容（与其他 transforms/edges/registries 并存
 * → fail-fast）。schema 为 SQL 面自带声明（D7 受管类型名九名闭集）；sinkBean 由编译器
 * 追加 sink 声明与边（产物自洽可执行）。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSqlModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: schemas
     * 
     */
    private KeyedList<io.nop.stream.flow.model.StreamSqlFieldModel> _schemas = KeyedList.emptyList();
    
    /**
     *  
     * xml name: sinkBean
     * 
     */
    private java.lang.String _sinkBean ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private java.lang.String _source ;
    
    /**
     * 
     * xml name: schemas
     *  
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamSqlFieldModel> getSchemas(){
      return _schemas;
    }

    
    public void setSchemas(java.util.List<io.nop.stream.flow.model.StreamSqlFieldModel> value){
        checkAllowChange();
        
        this._schemas = KeyedList.fromList(value, io.nop.stream.flow.model.StreamSqlFieldModel::getName);
           
    }

    
    public io.nop.stream.flow.model.StreamSqlFieldModel getField(String name){
        return this._schemas.getByKey(name);
    }

    public boolean hasField(String name){
        return this._schemas.containsKey(name);
    }

    public void addField(io.nop.stream.flow.model.StreamSqlFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamSqlFieldModel> list = this.getSchemas();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamSqlFieldModel::getName);
            setSchemas(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_schemas(){
        return this._schemas.keySet();
    }

    public boolean hasSchemas(){
        return !this._schemas.isEmpty();
    }
    
    /**
     * 
     * xml name: sinkBean
     *  
     */
    
    public java.lang.String getSinkBean(){
      return _sinkBean;
    }

    
    public void setSinkBean(java.lang.String value){
        checkAllowChange();
        
        this._sinkBean = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public java.lang.String getSource(){
      return _source;
    }

    
    public void setSource(java.lang.String value){
        checkAllowChange();
        
        this._source = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._schemas = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schemas);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("schemas",this.getSchemas());
        out.putNotNull("sinkBean",this.getSinkBean());
        out.putNotNull("source",this.getSource());
    }

    public StreamSqlModel cloneInstance(){
        StreamSqlModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSqlModel instance){
        super.copyTo(instance);
        
        instance.setSchemas(this.getSchemas());
        instance.setSinkBean(this.getSinkBean());
        instance.setSource(this.getSource());
    }

    protected StreamSqlModel newInstance(){
        return (StreamSqlModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
