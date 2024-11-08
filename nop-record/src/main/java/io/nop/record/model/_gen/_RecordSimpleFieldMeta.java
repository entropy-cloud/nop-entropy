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
     * xml name: absoluteOffset
     * 是否绝对偏移。如果是，则offset是相对于父字段的起始位置来定位，否则是相对于前一个字段来定位
     */
    private boolean _absoluteOffset  = false;
    
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
     * xml name: enum
     * 
     */
    private java.lang.String _enum ;
    
    /**
     *  
     * xml name: eosError
     * 
     */
    private boolean _eosError  = true;
    
    /**
     *  
     * xml name: excludeMax
     * 
     */
    private java.lang.Boolean _excludeMax ;
    
    /**
     *  
     * xml name: excludeMin
     * 
     */
    private java.lang.Boolean _excludeMin ;
    
    /**
     *  
     * xml name: exportExpr
     * 输出时不从实体上获取，根据表达式计算得到输出值
     */
    private io.nop.core.lang.eval.IEvalFunction _exportExpr ;
    
    /**
     *  
     * xml name: ifExpr
     * 当表达式返回false时，此字段将被跳过，不会被处理
     */
    private io.nop.core.lang.eval.IEvalFunction _ifExpr ;
    
    /**
     *  
     * xml name: includeTerminator
     * 解析结果是否包含terminator
     */
    private boolean _includeTerminator  = false;
    
    /**
     *  
     * xml name: label
     * 字段的显示名称。抛出用户可读的异常消息时可能会用到
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: lazy
     * 是否延迟解析。如果延迟解析，则只是记录当前offset和length
     */
    private boolean _lazy  = false;
    
    /**
     *  
     * xml name: leftPad
     * 如果padding不为空且length大于0，则缺省在右侧增加pad。如果配置了leftPad=true，则在左侧增加pad。p
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
     * xml name: max
     * 
     */
    private java.lang.Double _max ;
    
    /**
     *  
     * xml name: maxLength
     * 
     */
    private java.lang.Integer _maxLength ;
    
    /**
     *  
     * xml name: min
     * 
     */
    private java.lang.Double _min ;
    
    /**
     *  
     * xml name: minLength
     * 
     */
    private java.lang.Integer _minLength ;
    
    /**
     *  
     * xml name: name
     * 对应解析得到的属性名。如果指定了prop，则以prop为准
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: offset
     * 在行内的偏移量。从该位置处开始解析
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
     * xml name: pattern
     * 
     */
    private java.lang.String _pattern ;
    
    /**
     *  
     * xml name: prop
     * 如果设置了此属性，则这个prop才是实际对应的属性名
     */
    private java.lang.String _prop ;
    
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
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: terminator
     * 读取到terminator判断字段结束
     */
    private io.nop.commons.bytes.ByteString _terminator ;
    
    /**
     *  
     * xml name: tillEnd
     * 
     */
    private boolean _tillEnd  = false;
    
    /**
     *  
     * xml name: transformInExpr
     * 解析时对已经解析到的value进行转换
     */
    private io.nop.core.lang.eval.IEvalFunction _transformInExpr ;
    
    /**
     *  
     * xml name: trim
     * 解析得到值之后是否自动执行trim操作，去除padding字符。如果没有指定padding，则去除空格
     */
    private boolean _trim  = false;
    
    /**
     *  
     * xml name: type
     * 引用已有的字段定义
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: virtual
     * 虚拟字段，不解析到java bean中。当它具有fields子字段时可以起到分组作用。fields子字段会作为父对象的字段
     */
    private boolean _virtual  = false;
    
    /**
     * 
     * xml name: absoluteOffset
     *  是否绝对偏移。如果是，则offset是相对于父字段的起始位置来定位，否则是相对于前一个字段来定位
     */
    
    public boolean isAbsoluteOffset(){
      return _absoluteOffset;
    }

    
    public void setAbsoluteOffset(boolean value){
        checkAllowChange();
        
        this._absoluteOffset = value;
           
    }

    
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
     * xml name: enum
     *  
     */
    
    public java.lang.String getEnum(){
      return _enum;
    }

    
    public void setEnum(java.lang.String value){
        checkAllowChange();
        
        this._enum = value;
           
    }

    
    /**
     * 
     * xml name: eosError
     *  
     */
    
    public boolean isEosError(){
      return _eosError;
    }

    
    public void setEosError(boolean value){
        checkAllowChange();
        
        this._eosError = value;
           
    }

    
    /**
     * 
     * xml name: excludeMax
     *  
     */
    
    public java.lang.Boolean getExcludeMax(){
      return _excludeMax;
    }

    
    public void setExcludeMax(java.lang.Boolean value){
        checkAllowChange();
        
        this._excludeMax = value;
           
    }

    
    /**
     * 
     * xml name: excludeMin
     *  
     */
    
    public java.lang.Boolean getExcludeMin(){
      return _excludeMin;
    }

    
    public void setExcludeMin(java.lang.Boolean value){
        checkAllowChange();
        
        this._excludeMin = value;
           
    }

    
    /**
     * 
     * xml name: exportExpr
     *  输出时不从实体上获取，根据表达式计算得到输出值
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
     * xml name: ifExpr
     *  当表达式返回false时，此字段将被跳过，不会被处理
     */
    
    public io.nop.core.lang.eval.IEvalFunction getIfExpr(){
      return _ifExpr;
    }

    
    public void setIfExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._ifExpr = value;
           
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
     * xml name: label
     *  字段的显示名称。抛出用户可读的异常消息时可能会用到
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: lazy
     *  是否延迟解析。如果延迟解析，则只是记录当前offset和length
     */
    
    public boolean isLazy(){
      return _lazy;
    }

    
    public void setLazy(boolean value){
        checkAllowChange();
        
        this._lazy = value;
           
    }

    
    /**
     * 
     * xml name: leftPad
     *  如果padding不为空且length大于0，则缺省在右侧增加pad。如果配置了leftPad=true，则在左侧增加pad。p
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
     * xml name: max
     *  
     */
    
    public java.lang.Double getMax(){
      return _max;
    }

    
    public void setMax(java.lang.Double value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: maxLength
     *  
     */
    
    public java.lang.Integer getMaxLength(){
      return _maxLength;
    }

    
    public void setMaxLength(java.lang.Integer value){
        checkAllowChange();
        
        this._maxLength = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  
     */
    
    public java.lang.Double getMin(){
      return _min;
    }

    
    public void setMin(java.lang.Double value){
        checkAllowChange();
        
        this._min = value;
           
    }

    
    /**
     * 
     * xml name: minLength
     *  
     */
    
    public java.lang.Integer getMinLength(){
      return _minLength;
    }

    
    public void setMinLength(java.lang.Integer value){
        checkAllowChange();
        
        this._minLength = value;
           
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
     *  在行内的偏移量。从该位置处开始解析
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
     * xml name: pattern
     *  
     */
    
    public java.lang.String getPattern(){
      return _pattern;
    }

    
    public void setPattern(java.lang.String value){
        checkAllowChange();
        
        this._pattern = value;
           
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
     * xml name: stdDomain
     *  
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
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
     *  
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
     * xml name: transformInExpr
     *  解析时对已经解析到的value进行转换
     */
    
    public io.nop.core.lang.eval.IEvalFunction getTransformInExpr(){
      return _transformInExpr;
    }

    
    public void setTransformInExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._transformInExpr = value;
           
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
     *  引用已有的字段定义
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("absoluteOffset",this.isAbsoluteOffset());
        out.putNotNull("charset",this.getCharset());
        out.putNotNull("codec",this.getCodec());
        out.putNotNull("content",this.getContent());
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("endian",this.getEndian());
        out.putNotNull("enum",this.getEnum());
        out.putNotNull("eosError",this.isEosError());
        out.putNotNull("excludeMax",this.getExcludeMax());
        out.putNotNull("excludeMin",this.getExcludeMin());
        out.putNotNull("exportExpr",this.getExportExpr());
        out.putNotNull("ifExpr",this.getIfExpr());
        out.putNotNull("includeTerminator",this.isIncludeTerminator());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("lazy",this.isLazy());
        out.putNotNull("leftPad",this.isLeftPad());
        out.putNotNull("length",this.getLength());
        out.putNotNull("lengthExpr",this.getLengthExpr());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("max",this.getMax());
        out.putNotNull("maxLength",this.getMaxLength());
        out.putNotNull("min",this.getMin());
        out.putNotNull("minLength",this.getMinLength());
        out.putNotNull("name",this.getName());
        out.putNotNull("offset",this.getOffset());
        out.putNotNull("padding",this.getPadding());
        out.putNotNull("parseExpr",this.getParseExpr());
        out.putNotNull("pattern",this.getPattern());
        out.putNotNull("prop",this.getProp());
        out.putNotNull("skipWhenRead",this.isSkipWhenRead());
        out.putNotNull("skipWhenWrite",this.isSkipWhenWrite());
        out.putNotNull("skipWriteWhenEmpty",this.isSkipWriteWhenEmpty());
        out.putNotNull("stdDomain",this.getStdDomain());
        out.putNotNull("terminator",this.getTerminator());
        out.putNotNull("tillEnd",this.isTillEnd());
        out.putNotNull("transformInExpr",this.getTransformInExpr());
        out.putNotNull("trim",this.isTrim());
        out.putNotNull("type",this.getType());
        out.putNotNull("virtual",this.isVirtual());
    }

    public RecordSimpleFieldMeta cloneInstance(){
        RecordSimpleFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordSimpleFieldMeta instance){
        super.copyTo(instance);
        
        instance.setAbsoluteOffset(this.isAbsoluteOffset());
        instance.setCharset(this.getCharset());
        instance.setCodec(this.getCodec());
        instance.setContent(this.getContent());
        instance.setDoc(this.getDoc());
        instance.setEndian(this.getEndian());
        instance.setEnum(this.getEnum());
        instance.setEosError(this.isEosError());
        instance.setExcludeMax(this.getExcludeMax());
        instance.setExcludeMin(this.getExcludeMin());
        instance.setExportExpr(this.getExportExpr());
        instance.setIfExpr(this.getIfExpr());
        instance.setIncludeTerminator(this.isIncludeTerminator());
        instance.setLabel(this.getLabel());
        instance.setLazy(this.isLazy());
        instance.setLeftPad(this.isLeftPad());
        instance.setLength(this.getLength());
        instance.setLengthExpr(this.getLengthExpr());
        instance.setMandatory(this.isMandatory());
        instance.setMax(this.getMax());
        instance.setMaxLength(this.getMaxLength());
        instance.setMin(this.getMin());
        instance.setMinLength(this.getMinLength());
        instance.setName(this.getName());
        instance.setOffset(this.getOffset());
        instance.setPadding(this.getPadding());
        instance.setParseExpr(this.getParseExpr());
        instance.setPattern(this.getPattern());
        instance.setProp(this.getProp());
        instance.setSkipWhenRead(this.isSkipWhenRead());
        instance.setSkipWhenWrite(this.isSkipWhenWrite());
        instance.setSkipWriteWhenEmpty(this.isSkipWriteWhenEmpty());
        instance.setStdDomain(this.getStdDomain());
        instance.setTerminator(this.getTerminator());
        instance.setTillEnd(this.isTillEnd());
        instance.setTransformInExpr(this.getTransformInExpr());
        instance.setTrim(this.isTrim());
        instance.setType(this.getType());
        instance.setVirtual(this.isVirtual());
    }

    protected RecordSimpleFieldMeta newInstance(){
        return (RecordSimpleFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
