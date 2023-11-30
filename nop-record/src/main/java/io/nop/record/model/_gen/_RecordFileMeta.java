package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [9:2:0:0]/nop/schema/record/record-file.xdef <p>
 * 定长记录文件的描述
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _RecordFileMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: binary
     * 是否是二进制文件。如果否，则表示是文本文件
     */
    private boolean _binary  = false;
    
    /**
     *  
     * xml name: bitEndian
     * 
     */
    private io.nop.commons.bytes.EndianKind _bitEndian ;
    
    /**
     *  
     * xml name: body
     * 
     */
    private io.nop.record.model.RecordFileBodyMeta _body ;
    
    /**
     *  
     * xml name: defaultTextEncoding
     * 如果是文本文件，则这里指定文件的缺省语言编码。
     */
    private java.lang.String _defaultTextEncoding  = "UTF-8";
    
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
     * xml name: endian
     * 
     */
    private io.nop.commons.bytes.EndianKind _endian ;
    
    /**
     *  
     * xml name: enums
     * 
     */
    private KeyedList<io.nop.record.model.RecordEnum> _enums = KeyedList.emptyList();
    
    /**
     *  
     * xml name: header
     * 
     */
    private io.nop.record.model.RecordObjectMeta _header ;
    
    /**
     *  
     * xml name: param
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
     * xml name: types
     * 
     */
    private KeyedList<io.nop.record.model.RecordTypeMeta> _types = KeyedList.emptyList();
    
    /**
     * 
     * xml name: binary
     *  是否是二进制文件。如果否，则表示是文本文件
     */
    
    public boolean isBinary(){
      return _binary;
    }

    
    public void setBinary(boolean value){
        checkAllowChange();
        
        this._binary = value;
           
    }

    
    /**
     * 
     * xml name: bitEndian
     *  
     */
    
    public io.nop.commons.bytes.EndianKind getBitEndian(){
      return _bitEndian;
    }

    
    public void setBitEndian(io.nop.commons.bytes.EndianKind value){
        checkAllowChange();
        
        this._bitEndian = value;
           
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
     * xml name: defaultTextEncoding
     *  如果是文本文件，则这里指定文件的缺省语言编码。
     */
    
    public java.lang.String getDefaultTextEncoding(){
      return _defaultTextEncoding;
    }

    
    public void setDefaultTextEncoding(java.lang.String value){
        checkAllowChange();
        
        this._defaultTextEncoding = value;
           
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
     * xml name: endian
     *  
     */
    
    public io.nop.commons.bytes.EndianKind getEndian(){
      return _endian;
    }

    
    public void setEndian(io.nop.commons.bytes.EndianKind value){
        checkAllowChange();
        
        this._endian = value;
           
    }

    
    /**
     * 
     * xml name: enums
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordEnum> getEnums(){
      return _enums;
    }

    
    public void setEnums(java.util.List<io.nop.record.model.RecordEnum> value){
        checkAllowChange();
        
        this._enums = KeyedList.fromList(value, io.nop.record.model.RecordEnum::getName);
           
    }

    
    public io.nop.record.model.RecordEnum getEnum(String name){
        return this._enums.getByKey(name);
    }

    public boolean hasEnum(String name){
        return this._enums.containsKey(name);
    }

    public void addEnum(io.nop.record.model.RecordEnum item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordEnum> list = this.getEnums();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordEnum::getName);
            setEnums(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_enums(){
        return this._enums.keySet();
    }

    public boolean hasEnums(){
        return !this._enums.isEmpty();
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
     * xml name: param
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

    
    /**
     * 
     * xml name: types
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordTypeMeta> getTypes(){
      return _types;
    }

    
    public void setTypes(java.util.List<io.nop.record.model.RecordTypeMeta> value){
        checkAllowChange();
        
        this._types = KeyedList.fromList(value, io.nop.record.model.RecordTypeMeta::getName);
           
    }

    
    public io.nop.record.model.RecordTypeMeta getType(String name){
        return this._types.getByKey(name);
    }

    public boolean hasType(String name){
        return this._types.containsKey(name);
    }

    public void addType(io.nop.record.model.RecordTypeMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordTypeMeta> list = this.getTypes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordTypeMeta::getName);
            setTypes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_types(){
        return this._types.keySet();
    }

    public boolean hasTypes(){
        return !this._types.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._body = io.nop.api.core.util.FreezeHelper.deepFreeze(this._body);
            
           this._enums = io.nop.api.core.util.FreezeHelper.deepFreeze(this._enums);
            
           this._header = io.nop.api.core.util.FreezeHelper.deepFreeze(this._header);
            
           this._params = io.nop.api.core.util.FreezeHelper.deepFreeze(this._params);
            
           this._trailer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._trailer);
            
           this._types = io.nop.api.core.util.FreezeHelper.deepFreeze(this._types);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("binary",this.isBinary());
        out.put("bitEndian",this.getBitEndian());
        out.put("body",this.getBody());
        out.put("defaultTextEncoding",this.getDefaultTextEncoding());
        out.put("doc",this.getDoc());
        out.put("docRef",this.getDocRef());
        out.put("endian",this.getEndian());
        out.put("enums",this.getEnums());
        out.put("header",this.getHeader());
        out.put("params",this.getParams());
        out.put("trailer",this.getTrailer());
        out.put("types",this.getTypes());
    }
}
 // resume CPD analysis - CPD-ON
