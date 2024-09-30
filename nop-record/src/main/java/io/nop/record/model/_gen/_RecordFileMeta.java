package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordFileMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-file.xdef <p>
 * 定长记录文件的描述
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordFileMeta extends io.nop.record.model.RecordDefinitions {
    
    /**
     *  
     * xml name: aggregates
     * 
     */
    private KeyedList<io.nop.record.model.RecordAggregateFieldMeta> _aggregates = KeyedList.emptyList();
    
    /**
     *  
     * xml name: body
     * 
     */
    private io.nop.record.model.RecordFileBodyMeta _body ;
    
    /**
     *  
     * xml name: doc
     * 
     */
    private java.lang.String _doc ;
    
    /**
     *  
     * xml name: docRef
     * 翻译为java doc的@see注释
     */
    private java.lang.String _docRef ;
    
    /**
     *  
     * xml name: header
     * 
     */
    private io.nop.record.model.RecordObjectMeta _header ;
    
    /**
     *  
     * xml name: pagination
     * 分页生成，每页包含指定条目数，并且有可能会具有页头和页尾
     */
    private io.nop.record.model.RecordPaginationMeta _pagination ;
    
    /**
     *  
     * xml name: params
     * 
     */
    private KeyedList<io.nop.record.model.RecordParamMeta> _params = KeyedList.emptyList();
    
    /**
     *  
     * xml name: trailer
     * 
     */
    private io.nop.record.model.RecordObjectMeta _trailer ;
    
    /**
     * 
     * xml name: aggregates
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordAggregateFieldMeta> getAggregates(){
      return _aggregates;
    }

    
    public void setAggregates(java.util.List<io.nop.record.model.RecordAggregateFieldMeta> value){
        checkAllowChange();
        
        this._aggregates = KeyedList.fromList(value, io.nop.record.model.RecordAggregateFieldMeta::getName);
           
    }

    
    public io.nop.record.model.RecordAggregateFieldMeta getAggregate(String name){
        return this._aggregates.getByKey(name);
    }

    public boolean hasAggregate(String name){
        return this._aggregates.containsKey(name);
    }

    public void addAggregate(io.nop.record.model.RecordAggregateFieldMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordAggregateFieldMeta> list = this.getAggregates();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordAggregateFieldMeta::getName);
            setAggregates(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_aggregates(){
        return this._aggregates.keySet();
    }

    public boolean hasAggregates(){
        return !this._aggregates.isEmpty();
    }
    
    /**
     * 
     * xml name: body
     *  
     */
    
    public io.nop.record.model.RecordFileBodyMeta getBody(){
      return _body;
    }

    
    public void setBody(io.nop.record.model.RecordFileBodyMeta value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: doc
     *  
     */
    
    public java.lang.String getDoc(){
      return _doc;
    }

    
    public void setDoc(java.lang.String value){
        checkAllowChange();
        
        this._doc = value;
           
    }

    
    /**
     * 
     * xml name: docRef
     *  翻译为java doc的@see注释
     */
    
    public java.lang.String getDocRef(){
      return _docRef;
    }

    
    public void setDocRef(java.lang.String value){
        checkAllowChange();
        
        this._docRef = value;
           
    }

    
    /**
     * 
     * xml name: header
     *  
     */
    
    public io.nop.record.model.RecordObjectMeta getHeader(){
      return _header;
    }

    
    public void setHeader(io.nop.record.model.RecordObjectMeta value){
        checkAllowChange();
        
        this._header = value;
           
    }

    
    /**
     * 
     * xml name: pagination
     *  分页生成，每页包含指定条目数，并且有可能会具有页头和页尾
     */
    
    public io.nop.record.model.RecordPaginationMeta getPagination(){
      return _pagination;
    }

    
    public void setPagination(io.nop.record.model.RecordPaginationMeta value){
        checkAllowChange();
        
        this._pagination = value;
           
    }

    
    /**
     * 
     * xml name: params
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordParamMeta> getParams(){
      return _params;
    }

    
    public void setParams(java.util.List<io.nop.record.model.RecordParamMeta> value){
        checkAllowChange();
        
        this._params = KeyedList.fromList(value, io.nop.record.model.RecordParamMeta::getName);
           
    }

    
    public io.nop.record.model.RecordParamMeta getParam(String name){
        return this._params.getByKey(name);
    }

    public boolean hasParam(String name){
        return this._params.containsKey(name);
    }

    public void addParam(io.nop.record.model.RecordParamMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordParamMeta> list = this.getParams();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordParamMeta::getName);
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
     * xml name: trailer
     *  
     */
    
    public io.nop.record.model.RecordObjectMeta getTrailer(){
      return _trailer;
    }

    
    public void setTrailer(io.nop.record.model.RecordObjectMeta value){
        checkAllowChange();
        
        this._trailer = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._aggregates = io.nop.api.core.util.FreezeHelper.deepFreeze(this._aggregates);
            
           this._body = io.nop.api.core.util.FreezeHelper.deepFreeze(this._body);
            
           this._header = io.nop.api.core.util.FreezeHelper.deepFreeze(this._header);
            
           this._pagination = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pagination);
            
           this._params = io.nop.api.core.util.FreezeHelper.deepFreeze(this._params);
            
           this._trailer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._trailer);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("aggregates",this.getAggregates());
        out.putNotNull("body",this.getBody());
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("docRef",this.getDocRef());
        out.putNotNull("header",this.getHeader());
        out.putNotNull("pagination",this.getPagination());
        out.putNotNull("params",this.getParams());
        out.putNotNull("trailer",this.getTrailer());
    }

    public RecordFileMeta cloneInstance(){
        RecordFileMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFileMeta instance){
        super.copyTo(instance);
        
        instance.setAggregates(this.getAggregates());
        instance.setBody(this.getBody());
        instance.setDoc(this.getDoc());
        instance.setDocRef(this.getDocRef());
        instance.setHeader(this.getHeader());
        instance.setPagination(this.getPagination());
        instance.setParams(this.getParams());
        instance.setTrailer(this.getTrailer());
    }

    protected RecordFileMeta newInstance(){
        return (RecordFileMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
