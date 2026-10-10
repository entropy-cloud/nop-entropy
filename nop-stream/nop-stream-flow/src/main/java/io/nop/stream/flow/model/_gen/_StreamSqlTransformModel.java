package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSqlTransformModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * U2/deferred-unlock（owner 2026-10-10 解除 WI16 transform 形态延期）：transform 级
 * SQL 声明面。<sqlTransform> 是图中单入（入度恰一，入边须 FORWARD 无 outputTag）
 * 多出（fan-out 合法）的 SQL 变换顶点：EQL 编译产物为完整 <stream> 模型形态的
 * 片段（FROM 表 source 顶点 id 恒为保留常量 sqlTransformEntry、无 sink），构建期由
 * StreamModelDslBuilder 剥离 entry、改名去重后拼接进图。与顶层 <sql> 的边界：
 * 顶层是 model generator（必须为模型唯一内容、产物含 sink 并替换全部内容），
 * 本顶点只承载一个变换子链，可与既有 transforms/registries 共存。emit 语义同
 * 顶层（UPDATE 仅窗口聚合查询合法）；join/UNION 多 source 形态不支持（fail-fast）。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSqlTransformModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: emit
     * 
     */
    private io.nop.stream.core.windowing.StreamEmitMode _emit ;
    
    /**
     *  
     * xml name: schemas
     * 
     */
    private KeyedList<io.nop.stream.flow.model.StreamSqlFieldModel> _schemas = KeyedList.emptyList();
    
    /**
     *  
     * xml name: source
     * 
     */
    private java.lang.String _source ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: emit
     *  
     */
    
    public io.nop.stream.core.windowing.StreamEmitMode getEmit(){
      return _emit;
    }

    
    public void setEmit(io.nop.stream.core.windowing.StreamEmitMode value){
        checkAllowChange();
        
        this._emit = value;
           
    }

    
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
        
           this._schemas = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schemas);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("emit",this.getEmit());
        out.putNotNull("schemas",this.getSchemas());
        out.putNotNull("source",this.getSource());
        out.putNotNull("type",this.getType());
    }

    public StreamSqlTransformModel cloneInstance(){
        StreamSqlTransformModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSqlTransformModel instance){
        super.copyTo(instance);
        
        instance.setEmit(this.getEmit());
        instance.setSchemas(this.getSchemas());
        instance.setSource(this.getSource());
        instance.setType(this.getType());
    }

    protected StreamSqlTransformModel newInstance(){
        return (StreamSqlTransformModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
