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
     * xml name: encoder
     * 
     */
    private java.lang.String _encoder ;
    
    /**
     *  
     * xml name: encoding
     * 
     */
    private java.lang.String _encoding ;
    
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
     * xml name: ifExpr
     * 当表达式返回false时，此字段将被跳过，不会被处理
     */
    private io.nop.core.lang.eval.IEvalPredicate _ifExpr ;
    
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
     * 动态确定字段长度。在表达式中_parent指向父结构，_root指向根结构。其他变量指向兄弟字段
     */
    private io.nop.core.lang.eval.IEvalAction _lengthExpr ;
    
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
     * 对应解析得到的属性名
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
     * xml name: pattern
     * 
     */
    private java.lang.String _pattern ;
    
    /**
     *  
     * xml name: repeat
     * 
     */
    private io.nop.record.model.FieldRepeatKind _repeat ;
    
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
     * 虚拟字段，不解析到java bean中
     */
    private boolean _virtual  = false;
    
    /**
     *  
     * xml name: wrapper
     * 
     */
    private boolean _wrapper  = false;
    
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
     * xml name: encoder
     *  
     */
    
    public java.lang.String getEncoder(){
      return _encoder;
    }

    
    public void setEncoder(java.lang.String value){
        checkAllowChange();
        
        this._encoder = value;
           
    }

    
    /**
     * 
     * xml name: encoding
     *  
     */
    
    public java.lang.String getEncoding(){
      return _encoding;
    }

    
    public void setEncoding(java.lang.String value){
        checkAllowChange();
        
        this._encoding = value;
           
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
     * xml name: ifExpr
     *  当表达式返回false时，此字段将被跳过，不会被处理
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getIfExpr(){
      return _ifExpr;
    }

    
    public void setIfExpr(io.nop.core.lang.eval.IEvalPredicate value){
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
     *  动态确定字段长度。在表达式中_parent指向父结构，_root指向根结构。其他变量指向兄弟字段
     */
    
    public io.nop.core.lang.eval.IEvalAction getLengthExpr(){
      return _lengthExpr;
    }

    
    public void setLengthExpr(io.nop.core.lang.eval.IEvalAction value){
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
     *  对应解析得到的属性名
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
     * xml name: repeat
     *  
     */
    
    public io.nop.record.model.FieldRepeatKind getRepeat(){
      return _repeat;
    }

    
    public void setRepeat(io.nop.record.model.FieldRepeatKind value){
        checkAllowChange();
        
        this._repeat = value;
           
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
     *  虚拟字段，不解析到java bean中
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
     * xml name: wrapper
     *  
     */
    
    public boolean isWrapper(){
      return _wrapper;
    }

    
    public void setWrapper(boolean value){
        checkAllowChange();
        
        this._wrapper = value;
           
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
        out.putNotNull("content",this.getContent());
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("encoder",this.getEncoder());
        out.putNotNull("encoding",this.getEncoding());
        out.putNotNull("endian",this.getEndian());
        out.putNotNull("enum",this.getEnum());
        out.putNotNull("eosError",this.isEosError());
        out.putNotNull("excludeMax",this.getExcludeMax());
        out.putNotNull("excludeMin",this.getExcludeMin());
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
        out.putNotNull("pattern",this.getPattern());
        out.putNotNull("repeat",this.getRepeat());
        out.putNotNull("stdDomain",this.getStdDomain());
        out.putNotNull("terminator",this.getTerminator());
        out.putNotNull("tillEnd",this.isTillEnd());
        out.putNotNull("trim",this.isTrim());
        out.putNotNull("type",this.getType());
        out.putNotNull("virtual",this.isVirtual());
        out.putNotNull("wrapper",this.isWrapper());
    }

    public RecordSimpleFieldMeta cloneInstance(){
        RecordSimpleFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordSimpleFieldMeta instance){
        super.copyTo(instance);
        
        instance.setAbsoluteOffset(this.isAbsoluteOffset());
        instance.setContent(this.getContent());
        instance.setDoc(this.getDoc());
        instance.setEncoder(this.getEncoder());
        instance.setEncoding(this.getEncoding());
        instance.setEndian(this.getEndian());
        instance.setEnum(this.getEnum());
        instance.setEosError(this.isEosError());
        instance.setExcludeMax(this.getExcludeMax());
        instance.setExcludeMin(this.getExcludeMin());
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
        instance.setPattern(this.getPattern());
        instance.setRepeat(this.getRepeat());
        instance.setStdDomain(this.getStdDomain());
        instance.setTerminator(this.getTerminator());
        instance.setTillEnd(this.isTillEnd());
        instance.setTrim(this.isTrim());
        instance.setType(this.getType());
        instance.setVirtual(this.isVirtual());
        instance.setWrapper(this.isWrapper());
    }

    protected RecordSimpleFieldMeta newInstance(){
        return (RecordSimpleFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
