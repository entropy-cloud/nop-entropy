package io.nop.xlang.xpl.xlib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xpl.xlib.XplTag;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [41:10:0:0]/nop/schema/xlib.xdef <p>
 * 自定义标签具有返回值和输出文本。所有解析器未识别的没有名字空间的标签都会被直接输出。标签的返回值可以通过xpl:return参数获取，例如
 * <my:MyTag c:return="x" />
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XplTag extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: attr
     * 
     */
    private KeyedList<io.nop.xlang.xpl.xlib.XplTagAttribute> _attrs = KeyedList.emptyList();
    
    /**
     *  已知属性变量名
     * xml name: attrsVar
     * 所有属性构成一个Map类型的变量。如果此参数非空，则所有属性作为一个整体参数传递，而不是每个属性对应一个变量。
     */
    private java.lang.String _attrsVar ;
    
    /**
     *  
     * xml name: bodyType
     * 
     */
    private io.nop.xlang.xpl.XplSlotType _bodyType ;
    
    /**
     *  调用位置
     * xml name: callLocationVar
     * 用于记录调用此标签时的源码位置的变量名。通过此变量可以获知此标签是在哪里被调用的，从而可以进行相对路径计算等
     */
    private java.lang.String _callLocationVar ;
    
    /**
     *  
     * xml name: checkNs
     * 
     */
    private java.util.Set<java.lang.String> _checkNs ;
    
    /**
     *  
     * xml name: conditionTag
     * 
     */
    private boolean _conditionTag  = false;
    
    /**
     *  是否废弃
     * xml name: deprecated
     * 是否标签已经被废弃。如果已经被废弃，则调用时会打印出调试信息
     */
    private boolean _deprecated  = false;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  显示名称
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: dump
     * 内部调试支持，用于打印编译期的调试信息。
     */
    private boolean _dump  = false;
    
    /**
     *  
     * xml name: example
     * 
     */
    private io.nop.core.lang.xml.XNode _example ;
    
    /**
     *  
     * xml name: ignoreUnknownAttrs
     * 
     */
    private boolean _ignoreUnknownAttrs  = false;
    
    /**
     *  
     * xml name: internal
     * 
     */
    private boolean _internal  = false;
    
    /**
     *  是否宏标签
     * xml name: macro
     * 宏标签在编译期会自动执行，然后再对它的输出结果进行编译。
     */
    private boolean _macro  = false;
    
    /**
     *  输出模式
     * xml name: outputMode
     * 设置xpl标签的输出模式
     */
    private io.nop.xlang.ast.XLangOutputMode _outputMode ;
    
    /**
     *  结构定义
     * xml name: schema
     * 在实际编译之前，经过schema验证
     */
    private java.lang.String _schema ;
    
    /**
     *  
     * xml name: slot
     * 在普通标签上标记xpl:slot，表示将slot和该节点合并，然后再调用render
     */
    private KeyedList<io.nop.xlang.xpl.xlib.XplTagSlot> _slots = KeyedList.emptyList();
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.xml.XNode _source ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _tagName ;
    
    /**
     *  
     * xml name: return
     * 
     */
    private io.nop.xlang.xpl.xlib.XplTagReturn _tagReturn ;
    
    /**
     *  
     * xml name: transform
     * 对调用标签进行编译期转化
     */
    private io.nop.core.lang.eval.IEvalFunction _transform ;
    
    /**
     *  转换器
     * xml name: transformer
     * 在实际进行编译之前先经过transformer转换
     */
    private java.lang.String _transformer ;
    
    /**
     *  未知属性变量名
     * xml name: unknownAttrsVar
     * 所有未知属性构成一个Map类型的变量。只有此参数非空时，才允许调用时传入未定义的属性。
     */
    private java.lang.String _unknownAttrsVar ;
    
    /**
     *  
     * xml name: validator
     * 对标签的参数进行校验
     */
    private io.nop.core.model.validator.ValidatorModel _validator ;
    
    /**
     * 
     * xml name: attr
     *  
     */
    
    public java.util.List<io.nop.xlang.xpl.xlib.XplTagAttribute> getAttrs(){
      return _attrs;
    }

    
    public void setAttrs(java.util.List<io.nop.xlang.xpl.xlib.XplTagAttribute> value){
        checkAllowChange();
        
        this._attrs = KeyedList.fromList(value, io.nop.xlang.xpl.xlib.XplTagAttribute::getName);
           
    }

    
    public io.nop.xlang.xpl.xlib.XplTagAttribute getAttr(String name){
        return this._attrs.getByKey(name);
    }

    public boolean hasAttr(String name){
        return this._attrs.containsKey(name);
    }

    public void addAttr(io.nop.xlang.xpl.xlib.XplTagAttribute item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xpl.xlib.XplTagAttribute> list = this.getAttrs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xpl.xlib.XplTagAttribute::getName);
            setAttrs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_attrs(){
        return this._attrs.keySet();
    }

    public boolean hasAttrs(){
        return !this._attrs.isEmpty();
    }
    
    /**
     * 已知属性变量名
     * xml name: attrsVar
     *  所有属性构成一个Map类型的变量。如果此参数非空，则所有属性作为一个整体参数传递，而不是每个属性对应一个变量。
     */
    
    public java.lang.String getAttrsVar(){
      return _attrsVar;
    }

    
    public void setAttrsVar(java.lang.String value){
        checkAllowChange();
        
        this._attrsVar = value;
           
    }

    
    /**
     * 
     * xml name: bodyType
     *  
     */
    
    public io.nop.xlang.xpl.XplSlotType getBodyType(){
      return _bodyType;
    }

    
    public void setBodyType(io.nop.xlang.xpl.XplSlotType value){
        checkAllowChange();
        
        this._bodyType = value;
           
    }

    
    /**
     * 调用位置
     * xml name: callLocationVar
     *  用于记录调用此标签时的源码位置的变量名。通过此变量可以获知此标签是在哪里被调用的，从而可以进行相对路径计算等
     */
    
    public java.lang.String getCallLocationVar(){
      return _callLocationVar;
    }

    
    public void setCallLocationVar(java.lang.String value){
        checkAllowChange();
        
        this._callLocationVar = value;
           
    }

    
    /**
     * 
     * xml name: checkNs
     *  
     */
    
    public java.util.Set<java.lang.String> getCheckNs(){
      return _checkNs;
    }

    
    public void setCheckNs(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._checkNs = value;
           
    }

    
    /**
     * 
     * xml name: conditionTag
     *  
     */
    
    public boolean isConditionTag(){
      return _conditionTag;
    }

    
    public void setConditionTag(boolean value){
        checkAllowChange();
        
        this._conditionTag = value;
           
    }

    
    /**
     * 是否废弃
     * xml name: deprecated
     *  是否标签已经被废弃。如果已经被废弃，则调用时会打印出调试信息
     */
    
    public boolean isDeprecated(){
      return _deprecated;
    }

    
    public void setDeprecated(boolean value){
        checkAllowChange();
        
        this._deprecated = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 显示名称
     * xml name: displayName
     *  
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
     * xml name: dump
     *  内部调试支持，用于打印编译期的调试信息。
     */
    @Deprecated
    public boolean isDump(){
      return _dump;
    }

    @Deprecated
    public void setDump(boolean value){
        checkAllowChange();
        
        this._dump = value;
           
    }

    
    /**
     * 
     * xml name: example
     *  
     */
    
    public io.nop.core.lang.xml.XNode getExample(){
      return _example;
    }

    
    public void setExample(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._example = value;
           
    }

    
    /**
     * 
     * xml name: ignoreUnknownAttrs
     *  
     */
    
    public boolean isIgnoreUnknownAttrs(){
      return _ignoreUnknownAttrs;
    }

    
    public void setIgnoreUnknownAttrs(boolean value){
        checkAllowChange();
        
        this._ignoreUnknownAttrs = value;
           
    }

    
    /**
     * 
     * xml name: internal
     *  
     */
    
    public boolean isInternal(){
      return _internal;
    }

    
    public void setInternal(boolean value){
        checkAllowChange();
        
        this._internal = value;
           
    }

    
    /**
     * 是否宏标签
     * xml name: macro
     *  宏标签在编译期会自动执行，然后再对它的输出结果进行编译。
     */
    
    public boolean isMacro(){
      return _macro;
    }

    
    public void setMacro(boolean value){
        checkAllowChange();
        
        this._macro = value;
           
    }

    
    /**
     * 输出模式
     * xml name: outputMode
     *  设置xpl标签的输出模式
     */
    
    public io.nop.xlang.ast.XLangOutputMode getOutputMode(){
      return _outputMode;
    }

    
    public void setOutputMode(io.nop.xlang.ast.XLangOutputMode value){
        checkAllowChange();
        
        this._outputMode = value;
           
    }

    
    /**
     * 结构定义
     * xml name: schema
     *  在实际编译之前，经过schema验证
     */
    
    public java.lang.String getSchema(){
      return _schema;
    }

    
    public void setSchema(java.lang.String value){
        checkAllowChange();
        
        this._schema = value;
           
    }

    
    /**
     * 
     * xml name: slot
     *  在普通标签上标记xpl:slot，表示将slot和该节点合并，然后再调用render
     */
    
    public java.util.List<io.nop.xlang.xpl.xlib.XplTagSlot> getSlots(){
      return _slots;
    }

    
    public void setSlots(java.util.List<io.nop.xlang.xpl.xlib.XplTagSlot> value){
        checkAllowChange();
        
        this._slots = KeyedList.fromList(value, io.nop.xlang.xpl.xlib.XplTagSlot::getName);
           
    }

    
    public io.nop.xlang.xpl.xlib.XplTagSlot getSlot(String name){
        return this._slots.getByKey(name);
    }

    public boolean hasSlot(String name){
        return this._slots.containsKey(name);
    }

    public void addSlot(io.nop.xlang.xpl.xlib.XplTagSlot item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xpl.xlib.XplTagSlot> list = this.getSlots();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xpl.xlib.XplTagSlot::getName);
            setSlots(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_slots(){
        return this._slots.keySet();
    }

    public boolean hasSlots(){
        return !this._slots.isEmpty();
    }
    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.xml.XNode getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getTagName(){
      return _tagName;
    }

    
    public void setTagName(java.lang.String value){
        checkAllowChange();
        
        this._tagName = value;
           
    }

    
    /**
     * 
     * xml name: return
     *  
     */
    
    public io.nop.xlang.xpl.xlib.XplTagReturn getTagReturn(){
      return _tagReturn;
    }

    
    public void setTagReturn(io.nop.xlang.xpl.xlib.XplTagReturn value){
        checkAllowChange();
        
        this._tagReturn = value;
           
    }

    
    /**
     * 
     * xml name: transform
     *  对调用标签进行编译期转化
     */
    
    public io.nop.core.lang.eval.IEvalFunction getTransform(){
      return _transform;
    }

    
    public void setTransform(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._transform = value;
           
    }

    
    /**
     * 转换器
     * xml name: transformer
     *  在实际进行编译之前先经过transformer转换
     */
    
    public java.lang.String getTransformer(){
      return _transformer;
    }

    
    public void setTransformer(java.lang.String value){
        checkAllowChange();
        
        this._transformer = value;
           
    }

    
    /**
     * 未知属性变量名
     * xml name: unknownAttrsVar
     *  所有未知属性构成一个Map类型的变量。只有此参数非空时，才允许调用时传入未定义的属性。
     */
    
    public java.lang.String getUnknownAttrsVar(){
      return _unknownAttrsVar;
    }

    
    public void setUnknownAttrsVar(java.lang.String value){
        checkAllowChange();
        
        this._unknownAttrsVar = value;
           
    }

    
    /**
     * 
     * xml name: validator
     *  对标签的参数进行校验
     */
    
    public io.nop.core.model.validator.ValidatorModel getValidator(){
      return _validator;
    }

    
    public void setValidator(io.nop.core.model.validator.ValidatorModel value){
        checkAllowChange();
        
        this._validator = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._attrs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._attrs);
            
           this._slots = io.nop.api.core.util.FreezeHelper.deepFreeze(this._slots);
            
           this._tagReturn = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tagReturn);
            
           this._validator = io.nop.api.core.util.FreezeHelper.deepFreeze(this._validator);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("attrs",this.getAttrs());
        out.putNotNull("attrsVar",this.getAttrsVar());
        out.putNotNull("bodyType",this.getBodyType());
        out.putNotNull("callLocationVar",this.getCallLocationVar());
        out.putNotNull("checkNs",this.getCheckNs());
        out.putNotNull("conditionTag",this.isConditionTag());
        out.putNotNull("deprecated",this.isDeprecated());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("dump",this.isDump());
        out.putNotNull("example",this.getExample());
        out.putNotNull("ignoreUnknownAttrs",this.isIgnoreUnknownAttrs());
        out.putNotNull("internal",this.isInternal());
        out.putNotNull("macro",this.isMacro());
        out.putNotNull("outputMode",this.getOutputMode());
        out.putNotNull("schema",this.getSchema());
        out.putNotNull("slots",this.getSlots());
        out.putNotNull("source",this.getSource());
        out.putNotNull("tagName",this.getTagName());
        out.putNotNull("tagReturn",this.getTagReturn());
        out.putNotNull("transform",this.getTransform());
        out.putNotNull("transformer",this.getTransformer());
        out.putNotNull("unknownAttrsVar",this.getUnknownAttrsVar());
        out.putNotNull("validator",this.getValidator());
    }

    public XplTag cloneInstance(){
        XplTag instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XplTag instance){
        super.copyTo(instance);
        
        instance.setAttrs(this.getAttrs());
        instance.setAttrsVar(this.getAttrsVar());
        instance.setBodyType(this.getBodyType());
        instance.setCallLocationVar(this.getCallLocationVar());
        instance.setCheckNs(this.getCheckNs());
        instance.setConditionTag(this.isConditionTag());
        instance.setDeprecated(this.isDeprecated());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setDump(this.isDump());
        instance.setExample(this.getExample());
        instance.setIgnoreUnknownAttrs(this.isIgnoreUnknownAttrs());
        instance.setInternal(this.isInternal());
        instance.setMacro(this.isMacro());
        instance.setOutputMode(this.getOutputMode());
        instance.setSchema(this.getSchema());
        instance.setSlots(this.getSlots());
        instance.setSource(this.getSource());
        instance.setTagName(this.getTagName());
        instance.setTagReturn(this.getTagReturn());
        instance.setTransform(this.getTransform());
        instance.setTransformer(this.getTransformer());
        instance.setUnknownAttrsVar(this.getUnknownAttrsVar());
        instance.setValidator(this.getValidator());
    }

    protected XplTag newInstance(){
        return (XplTag) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
