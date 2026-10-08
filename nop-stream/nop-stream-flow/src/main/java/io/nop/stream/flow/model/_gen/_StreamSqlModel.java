package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSqlModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * WI37/D11: emit 两档 emit="UPDATE|CLOSE"（表面为枚举常量名大写——xdef 枚举字典校验按常量名
 * 精确匹配，与 accumulationMode 同一约定；2026-10-08 WI37 执行修正 F-1，原小写表述废止）——
 * UPDATE 逐记录发射窗口更新（隐含 ACCUMULATING），CLOSE 维持窗口末单次发射；
 * 挂 <sql> 顶层属性，编译产物落 <strategy emit>（UPDATE 仅窗口聚合查询合法）
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSqlModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
        
        out.putNotNull("emit",this.getEmit());
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
        
        instance.setEmit(this.getEmit());
        instance.setSchemas(this.getSchemas());
        instance.setSinkBean(this.getSinkBean());
        instance.setSource(this.getSource());
    }

    protected StreamSqlModel newInstance(){
        return (StreamSqlModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
