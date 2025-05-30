package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordSimpleFieldMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-simple-field.xdef <p>
 * 定长记录的定义
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordSimpleFieldMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: charset
     * 
     */
    private java.lang.String _charset ;
    
    /**
     *  
     * xml name: codec
     * 
     */
    private java.lang.String _codec ;
    
    /**
     *  
     * xml name: content
     * 如果非空，则表示字段为固定内容。当输出字段到数据文件中时直接使用该内容输出
     */
    private io.nop.commons.bytes.ByteString _content ;
    
    /**
     *  
     * xml name: displayName
     * 字段的显示名称。抛出用户可读的异常消息时可能会用到
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: doc
     * 
     */
    private java.lang.String _doc ;
    
    /**
     *  
     * xml name: endian
     * 
     */
    private io.nop.commons.bytes.EndianKind _endian ;
    
    /**
     *  
     * xml name: exportExpr
     * 导出时通过此表达式获取值，而不是通过prop获取值
     */
    private io.nop.core.lang.eval.IEvalFunction _exportExpr ;
    
    /**
     *  
     * xml name: format
     * 
     */
    private java.lang.String _format ;
    
    /**
     *  
     * xml name: includeTerminator
     * 解析结果是否包含terminator
     */
    private boolean _includeTerminator  = false;
    
    /**
     *  
     * xml name: leftPad
     * 
     */
    private boolean _leftPad  = false;
    
    /**
     *  
     * xml name: length
     * 缺省长度。如果padding不为空，则会补全到该长度
     */
    private int _length  = 0;
    
    /**
     *  
     * xml name: lengthExpr
     * 动态确定字段长度。在表达式中record指向父结构，_root指向根结构。
     */
    private io.nop.core.lang.eval.IEvalFunction _lengthExpr ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: name
     * 对应解析得到的属性名。如果指定了prop，则以prop为准
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: offset
     * 相对于上一个字段的偏移量。如果大于0，解析的时候会跳过指定个数的字符或者字节。从新的位置处开始解析
     */
    private int _offset  = 0;
    
    /**
     *  
     * xml name: padding
     * 用于padding的字符
     */
    private io.nop.commons.bytes.ByteString _padding ;
    
    /**
     *  
     * xml name: parseExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parseExpr ;
    
    /**
     *  
     * xml name: prop
     * 如果设置了此属性，则这个prop才是实际对应的属性名
     */
    private java.lang.String _prop ;
    
    /**
     *  
     * xml name: readWhen
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _readWhen ;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: skipWhenRead
     * 当读取记录时忽略此字段，此字段可能仅用于输出
     */
    private boolean _skipWhenRead  = false;
    
    /**
     *  
     * xml name: skipWhenWrite
     * 
     */
    private boolean _skipWhenWrite  = false;
    
    /**
     *  
     * xml name: skipWriteWhenEmpty
     * 
     */
    private boolean _skipWriteWhenEmpty  = false;
    
    /**
     *  
     * xml name: terminator
     * 读取到terminator判断字段结束
     */
    private io.nop.commons.bytes.ByteString _terminator ;
    
    /**
     *  
     * xml name: tillEnd
     * 读取所有剩余部分
     */
    private boolean _tillEnd  = false;
    
    /**
     *  
     * xml name: transformIn
     * 解析时对已经解析到的value进行转换
     */
    private io.nop.core.lang.eval.IEvalFunction _transformIn ;
    
    /**
     *  
     * xml name: transformOut
     * 输出时不从实体上获取，根据表达式计算得到输出值
     */
    private io.nop.core.lang.eval.IEvalFunction _transformOut ;
    
    /**
     *  
     * xml name: trim
     * 解析得到值之后是否自动执行trim操作，去除padding字符。如果没有指定padding，则去除空格
     */
    private boolean _trim  = false;
    
    /**
     *  
     * xml name: type
     * 字段解析得到的java类型
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.Object _value ;
    
    /**
     *  
     * xml name: virtual
     * 虚拟字段，不解析到java bean中。当它具有fields子字段时可以起到分组作用。fields子字段会作为父对象的字段
     */
    private boolean _virtual  = false;
    
    /**
     *  
     * xml name: writeWhen
     * 当表达式返回false时，此字段将被跳过，不会被处理
     */
    private io.nop.core.lang.eval.IEvalFunction _writeWhen ;
    
    /**
     * 
     * xml name: charset
     *  
     */
    
    public java.lang.String getCharset(){
      return _charset;
    }

    
    public void setCharset(java.lang.String value){
        checkAllowChange();
        
        this._charset = value;
           
    }

    
    /**
     * 
     * xml name: codec
     *  
     */
    
    public java.lang.String getCodec(){
      return _codec;
    }

    
    public void setCodec(java.lang.String value){
        checkAllowChange();
        
        this._codec = value;
           
    }

    
    /**
     * 
     * xml name: content
     *  如果非空，则表示字段为固定内容。当输出字段到数据文件中时直接使用该内容输出
     */
    
    public io.nop.commons.bytes.ByteString getContent(){
      return _content;
    }

    
    public void setContent(io.nop.commons.bytes.ByteString value){
        checkAllowChange();
        
        this._content = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  字段的显示名称。抛出用户可读的异常消息时可能会用到
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
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
     * xml name: exportExpr
     *  导出时通过此表达式获取值，而不是通过prop获取值
     */
    
    public io.nop.core.lang.eval.IEvalFunction getExportExpr(){
      return _exportExpr;
    }

    
    public void setExportExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._exportExpr = value;
           
    }

    
    /**
     * 
     * xml name: format
     *  
     */
    
    public java.lang.String getFormat(){
      return _format;
    }

    
    public void setFormat(java.lang.String value){
        checkAllowChange();
        
        this._format = value;
           
    }

    
    /**
     * 
     * xml name: includeTerminator
     *  解析结果是否包含terminator
     */
    
    public boolean isIncludeTerminator(){
      return _includeTerminator;
    }

    
    public void setIncludeTerminator(boolean value){
        checkAllowChange();
        
        this._includeTerminator = value;
           
    }

    
    /**
     * 
     * xml name: leftPad
     *  
     */
    
    public boolean isLeftPad(){
      return _leftPad;
    }

    
    public void setLeftPad(boolean value){
        checkAllowChange();
        
        this._leftPad = value;
           
    }

    
    /**
     * 
     * xml name: length
     *  缺省长度。如果padding不为空，则会补全到该长度
     */
    
    public int getLength(){
      return _length;
    }

    
    public void setLength(int value){
        checkAllowChange();
        
        this._length = value;
           
    }

    
    /**
     * 
     * xml name: lengthExpr
     *  动态确定字段长度。在表达式中record指向父结构，_root指向根结构。
     */
    
    public io.nop.core.lang.eval.IEvalFunction getLengthExpr(){
      return _lengthExpr;
    }

    
    public void setLengthExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._lengthExpr = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  对应解析得到的属性名。如果指定了prop，则以prop为准
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: offset
     *  相对于上一个字段的偏移量。如果大于0，解析的时候会跳过指定个数的字符或者字节。从新的位置处开始解析
     */
    
    public int getOffset(){
      return _offset;
    }

    
    public void setOffset(int value){
        checkAllowChange();
        
        this._offset = value;
           
    }

    
    /**
     * 
     * xml name: padding
     *  用于padding的字符
     */
    
    public io.nop.commons.bytes.ByteString getPadding(){
      return _padding;
    }

    
    public void setPadding(io.nop.commons.bytes.ByteString value){
        checkAllowChange();
        
        this._padding = value;
           
    }

    
    /**
     * 
     * xml name: parseExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getParseExpr(){
      return _parseExpr;
    }

    
    public void setParseExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._parseExpr = value;
           
    }

    
    /**
     * 
     * xml name: prop
     *  如果设置了此属性，则这个prop才是实际对应的属性名
     */
    
    public java.lang.String getProp(){
      return _prop;
    }

    
    public void setProp(java.lang.String value){
        checkAllowChange();
        
        this._prop = value;
           
    }

    
    /**
     * 
     * xml name: readWhen
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getReadWhen(){
      return _readWhen;
    }

    
    public void setReadWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._readWhen = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    
    public io.nop.xlang.xmeta.ISchema getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.xlang.xmeta.ISchema value){
        checkAllowChange();
        
        this._schema = value;
           
    }

    
    /**
     * 
     * xml name: skipWhenRead
     *  当读取记录时忽略此字段，此字段可能仅用于输出
     */
    
    public boolean isSkipWhenRead(){
      return _skipWhenRead;
    }

    
    public void setSkipWhenRead(boolean value){
        checkAllowChange();
        
        this._skipWhenRead = value;
           
    }

    
    /**
     * 
     * xml name: skipWhenWrite
     *  
     */
    
    public boolean isSkipWhenWrite(){
      return _skipWhenWrite;
    }

    
    public void setSkipWhenWrite(boolean value){
        checkAllowChange();
        
        this._skipWhenWrite = value;
           
    }

    
    /**
     * 
     * xml name: skipWriteWhenEmpty
     *  
     */
    
    public boolean isSkipWriteWhenEmpty(){
      return _skipWriteWhenEmpty;
    }

    
    public void setSkipWriteWhenEmpty(boolean value){
        checkAllowChange();
        
        this._skipWriteWhenEmpty = value;
           
    }

    
    /**
     * 
     * xml name: terminator
     *  读取到terminator判断字段结束
     */
    
    public io.nop.commons.bytes.ByteString getTerminator(){
      return _terminator;
    }

    
    public void setTerminator(io.nop.commons.bytes.ByteString value){
        checkAllowChange();
        
        this._terminator = value;
           
    }

    
    /**
     * 
     * xml name: tillEnd
     *  读取所有剩余部分
     */
    
    public boolean isTillEnd(){
      return _tillEnd;
    }

    
    public void setTillEnd(boolean value){
        checkAllowChange();
        
        this._tillEnd = value;
           
    }

    
    /**
     * 
     * xml name: transformIn
     *  解析时对已经解析到的value进行转换
     */
    
    public io.nop.core.lang.eval.IEvalFunction getTransformIn(){
      return _transformIn;
    }

    
    public void setTransformIn(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._transformIn = value;
           
    }

    
    /**
     * 
     * xml name: transformOut
     *  输出时不从实体上获取，根据表达式计算得到输出值
     */
    
    public io.nop.core.lang.eval.IEvalFunction getTransformOut(){
      return _transformOut;
    }

    
    public void setTransformOut(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._transformOut = value;
           
    }

    
    /**
     * 
     * xml name: trim
     *  解析得到值之后是否自动执行trim操作，去除padding字符。如果没有指定padding，则去除空格
     */
    
    public boolean isTrim(){
      return _trim;
    }

    
    public void setTrim(boolean value){
        checkAllowChange();
        
        this._trim = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  字段解析得到的java类型
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public java.lang.Object getValue(){
      return _value;
    }

    
    public void setValue(java.lang.Object value){
        checkAllowChange();
        
        this._value = value;
           
    }

    
    /**
     * 
     * xml name: virtual
     *  虚拟字段，不解析到java bean中。当它具有fields子字段时可以起到分组作用。fields子字段会作为父对象的字段
     */
    
    public boolean isVirtual(){
      return _virtual;
    }

    
    public void setVirtual(boolean value){
        checkAllowChange();
        
        this._virtual = value;
           
    }

    
    /**
     * 
     * xml name: writeWhen
     *  当表达式返回false时，此字段将被跳过，不会被处理
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWriteWhen(){
      return _writeWhen;
    }

    
    public void setWriteWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._writeWhen = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("charset",this.getCharset());
        out.putNotNull("codec",this.getCodec());
        out.putNotNull("content",this.getContent());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("endian",this.getEndian());
        out.putNotNull("exportExpr",this.getExportExpr());
        out.putNotNull("format",this.getFormat());
        out.putNotNull("includeTerminator",this.isIncludeTerminator());
        out.putNotNull("leftPad",this.isLeftPad());
        out.putNotNull("length",this.getLength());
        out.putNotNull("lengthExpr",this.getLengthExpr());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("name",this.getName());
        out.putNotNull("offset",this.getOffset());
        out.putNotNull("padding",this.getPadding());
        out.putNotNull("parseExpr",this.getParseExpr());
        out.putNotNull("prop",this.getProp());
        out.putNotNull("readWhen",this.getReadWhen());
        out.putNotNull("schema",this.getSchema());
        out.putNotNull("skipWhenRead",this.isSkipWhenRead());
        out.putNotNull("skipWhenWrite",this.isSkipWhenWrite());
        out.putNotNull("skipWriteWhenEmpty",this.isSkipWriteWhenEmpty());
        out.putNotNull("terminator",this.getTerminator());
        out.putNotNull("tillEnd",this.isTillEnd());
        out.putNotNull("transformIn",this.getTransformIn());
        out.putNotNull("transformOut",this.getTransformOut());
        out.putNotNull("trim",this.isTrim());
        out.putNotNull("type",this.getType());
        out.putNotNull("value",this.getValue());
        out.putNotNull("virtual",this.isVirtual());
        out.putNotNull("writeWhen",this.getWriteWhen());
    }

    public RecordSimpleFieldMeta cloneInstance(){
        RecordSimpleFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordSimpleFieldMeta instance){
        super.copyTo(instance);
        
        instance.setCharset(this.getCharset());
        instance.setCodec(this.getCodec());
        instance.setContent(this.getContent());
        instance.setDisplayName(this.getDisplayName());
        instance.setDoc(this.getDoc());
        instance.setEndian(this.getEndian());
        instance.setExportExpr(this.getExportExpr());
        instance.setFormat(this.getFormat());
        instance.setIncludeTerminator(this.isIncludeTerminator());
        instance.setLeftPad(this.isLeftPad());
        instance.setLength(this.getLength());
        instance.setLengthExpr(this.getLengthExpr());
        instance.setMandatory(this.isMandatory());
        instance.setName(this.getName());
        instance.setOffset(this.getOffset());
        instance.setPadding(this.getPadding());
        instance.setParseExpr(this.getParseExpr());
        instance.setProp(this.getProp());
        instance.setReadWhen(this.getReadWhen());
        instance.setSchema(this.getSchema());
        instance.setSkipWhenRead(this.isSkipWhenRead());
        instance.setSkipWhenWrite(this.isSkipWhenWrite());
        instance.setSkipWriteWhenEmpty(this.isSkipWriteWhenEmpty());
        instance.setTerminator(this.getTerminator());
        instance.setTillEnd(this.isTillEnd());
        instance.setTransformIn(this.getTransformIn());
        instance.setTransformOut(this.getTransformOut());
        instance.setTrim(this.isTrim());
        instance.setType(this.getType());
        instance.setValue(this.getValue());
        instance.setVirtual(this.isVirtual());
        instance.setWriteWhen(this.getWriteWhen());
    }

    protected RecordSimpleFieldMeta newInstance(){
        return (RecordSimpleFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
