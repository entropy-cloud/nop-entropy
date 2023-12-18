package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [15:2:0:0]/nop/schema/xui/disp.xdef <p>
 * 单个字段对应的界面描述
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiDisplayMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bizObjName
     * 
     */
    private java.lang.String _bizObjName ;
    
    /**
     *  
     * xml name: charCase
     * 
     */
    private java.lang.String _charCase ;
    
    /**
     *  
     * xml name: className
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: classNameExpr
     * 
     */
    private java.lang.String _classNameExpr ;
    
    /**
     *  
     * xml name: control
     * 直接指定使用的控件类型
     */
    private java.lang.String _control ;
    
    /**
     *  
     * xml name: custom
     * 如果为false，则id必须是meta中定义的字段名。如果不是，则会报错。
     * 用于防止拼写错误或者字段从数据模型中删除后出现无效引用
     */
    private boolean _custom  = false;
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.String _defaultValue ;
    
    /**
     *  
     * xml name: depends
     * 
     */
    private java.util.Set<java.lang.String> _depends ;
    
    /**
     *  
     * xml name: desc
     * 
     */
    private java.lang.String _desc ;
    
    /**
     *  
     * xml name: disabledOn
     * 
     */
    private java.lang.String _disabledOn ;
    
    /**
     *  
     * xml name: displayProp
     * 
     */
    private java.lang.String _displayProp ;
    
    /**
     *  
     * xml name: domain
     * 
     */
    private java.lang.String _domain ;
    
    /**
     *  
     * xml name: editMode
     * 编辑模式。view/edit/query/list-view/list-edit/list-query等
     */
    private java.lang.String _editMode ;
    
    /**
     *  
     * xml name: filterOp
     * 
     */
    private java.lang.String _filterOp ;
    
    /**
     *  
     * xml name: gen-control
     * 根据propMeta, dispMeta, mode等参数生成控件描述
     */
    private io.nop.core.lang.eval.IEvalAction _genControl ;
    
    /**
     *  
     * xml name: hint
     * 录入界面上显示的提示信息
     */
    private java.lang.String _hint ;
    
    /**
     *  
     * xml name: href
     * 
     */
    private io.nop.xui.model.UiHrefModel _href ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: idProp
     * 
     */
    private java.lang.String _idProp ;
    
    /**
     *  
     * xml name: if
     * 
     */
    private java.lang.String _if ;
    
    /**
     *  
     * xml name: joinValues
     * 当multiValue为true的时候，表示是否是逗号分隔的字符串
     */
    private boolean _joinValues  = true;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: matchRegexp
     * 
     */
    private java.lang.String _matchRegexp ;
    
    /**
     *  
     * xml name: maxLength
     * 
     */
    private java.lang.Integer _maxLength ;
    
    /**
     *  
     * xml name: maxUploadSize
     * 
     */
    private java.lang.Long _maxUploadSize ;
    
    /**
     *  
     * xml name: minLength
     * 
     */
    private java.lang.Integer _minLength ;
    
    /**
     *  
     * xml name: minRows
     * 
     */
    private java.lang.Integer _minRows ;
    
    /**
     *  
     * xml name: multiValue
     * 如果为true，则表示是多个值。一般为逗号分隔的字符串
     */
    private boolean _multiValue  = false;
    
    /**
     *  
     * xml name: placeholder
     * 输入框中的缺省提示信息
     */
    private java.lang.String _placeholder ;
    
    /**
     *  
     * xml name: prop
     * 
     */
    private java.lang.String _prop ;
    
    /**
     *  
     * xml name: readonlyOn
     * 
     */
    private java.lang.String _readonlyOn ;
    
    /**
     *  
     * xml name: requiredOn
     * 
     */
    private java.lang.String _requiredOn ;
    
    /**
     *  
     * xml name: selectFirst
     * 对于Options选项，默认选中第一个
     */
    private java.lang.Boolean _selectFirst ;
    
    /**
     *  
     * xml name: selection
     * 对于对象属性或者对象列表属性，通过selection来指定graphql查询字段。
     * 如果不指定，则可以根据view配置的form或者grid来推定
     */
    private io.nop.api.core.beans.FieldSelectionBean _selection ;
    
    /**
     *  
     * xml name: sourceUrl
     * 
     */
    private java.lang.String _sourceUrl ;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: uploadUrl
     * 
     */
    private java.lang.String _uploadUrl ;
    
    /**
     *  
     * xml name: validator
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _validator ;
    
    /**
     *  
     * xml name: view
     * 对于对象属性或者对象列表属性，使用xview文件中定义的page去显示
     */
    private io.nop.xui.model.UiRefViewModel _view ;
    
    /**
     *  
     * xml name: visibleOn
     * 
     */
    private java.lang.String _visibleOn ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.String _width ;
    
    /**
     * 
     * xml name: bizObjName
     *  
     */
    
    public java.lang.String getBizObjName(){
      return _bizObjName;
    }

    
    public void setBizObjName(java.lang.String value){
        checkAllowChange();
        
        this._bizObjName = value;
           
    }

    
    /**
     * 
     * xml name: charCase
     *  
     */
    
    public java.lang.String getCharCase(){
      return _charCase;
    }

    
    public void setCharCase(java.lang.String value){
        checkAllowChange();
        
        this._charCase = value;
           
    }

    
    /**
     * 
     * xml name: className
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: classNameExpr
     *  
     */
    
    public java.lang.String getClassNameExpr(){
      return _classNameExpr;
    }

    
    public void setClassNameExpr(java.lang.String value){
        checkAllowChange();
        
        this._classNameExpr = value;
           
    }

    
    /**
     * 
     * xml name: control
     *  直接指定使用的控件类型
     */
    
    public java.lang.String getControl(){
      return _control;
    }

    
    public void setControl(java.lang.String value){
        checkAllowChange();
        
        this._control = value;
           
    }

    
    /**
     * 
     * xml name: custom
     *  如果为false，则id必须是meta中定义的字段名。如果不是，则会报错。
     * 用于防止拼写错误或者字段从数据模型中删除后出现无效引用
     */
    
    public boolean isCustom(){
      return _custom;
    }

    
    public void setCustom(boolean value){
        checkAllowChange();
        
        this._custom = value;
           
    }

    
    /**
     * 
     * xml name: defaultValue
     *  
     */
    
    public java.lang.String getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.String value){
        checkAllowChange();
        
        this._defaultValue = value;
           
    }

    
    /**
     * 
     * xml name: depends
     *  
     */
    
    public java.util.Set<java.lang.String> getDepends(){
      return _depends;
    }

    
    public void setDepends(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._depends = value;
           
    }

    
    /**
     * 
     * xml name: desc
     *  
     */
    
    public java.lang.String getDesc(){
      return _desc;
    }

    
    public void setDesc(java.lang.String value){
        checkAllowChange();
        
        this._desc = value;
           
    }

    
    /**
     * 
     * xml name: disabledOn
     *  
     */
    
    public java.lang.String getDisabledOn(){
      return _disabledOn;
    }

    
    public void setDisabledOn(java.lang.String value){
        checkAllowChange();
        
        this._disabledOn = value;
           
    }

    
    /**
     * 
     * xml name: displayProp
     *  
     */
    
    public java.lang.String getDisplayProp(){
      return _displayProp;
    }

    
    public void setDisplayProp(java.lang.String value){
        checkAllowChange();
        
        this._displayProp = value;
           
    }

    
    /**
     * 
     * xml name: domain
     *  
     */
    
    public java.lang.String getDomain(){
      return _domain;
    }

    
    public void setDomain(java.lang.String value){
        checkAllowChange();
        
        this._domain = value;
           
    }

    
    /**
     * 
     * xml name: editMode
     *  编辑模式。view/edit/query/list-view/list-edit/list-query等
     */
    
    public java.lang.String getEditMode(){
      return _editMode;
    }

    
    public void setEditMode(java.lang.String value){
        checkAllowChange();
        
        this._editMode = value;
           
    }

    
    /**
     * 
     * xml name: filterOp
     *  
     */
    
    public java.lang.String getFilterOp(){
      return _filterOp;
    }

    
    public void setFilterOp(java.lang.String value){
        checkAllowChange();
        
        this._filterOp = value;
           
    }

    
    /**
     * 
     * xml name: gen-control
     *  根据propMeta, dispMeta, mode等参数生成控件描述
     */
    
    public io.nop.core.lang.eval.IEvalAction getGenControl(){
      return _genControl;
    }

    
    public void setGenControl(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._genControl = value;
           
    }

    
    /**
     * 
     * xml name: hint
     *  录入界面上显示的提示信息
     */
    
    public java.lang.String getHint(){
      return _hint;
    }

    
    public void setHint(java.lang.String value){
        checkAllowChange();
        
        this._hint = value;
           
    }

    
    /**
     * 
     * xml name: href
     *  
     */
    
    public io.nop.xui.model.UiHrefModel getHref(){
      return _href;
    }

    
    public void setHref(io.nop.xui.model.UiHrefModel value){
        checkAllowChange();
        
        this._href = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: idProp
     *  
     */
    
    public java.lang.String getIdProp(){
      return _idProp;
    }

    
    public void setIdProp(java.lang.String value){
        checkAllowChange();
        
        this._idProp = value;
           
    }

    
    /**
     * 
     * xml name: if
     *  
     */
    
    public java.lang.String getIf(){
      return _if;
    }

    
    public void setIf(java.lang.String value){
        checkAllowChange();
        
        this._if = value;
           
    }

    
    /**
     * 
     * xml name: joinValues
     *  当multiValue为true的时候，表示是否是逗号分隔的字符串
     */
    
    public boolean isJoinValues(){
      return _joinValues;
    }

    
    public void setJoinValues(boolean value){
        checkAllowChange();
        
        this._joinValues = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
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
     * xml name: matchRegexp
     *  
     */
    
    public java.lang.String getMatchRegexp(){
      return _matchRegexp;
    }

    
    public void setMatchRegexp(java.lang.String value){
        checkAllowChange();
        
        this._matchRegexp = value;
           
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
     * xml name: maxUploadSize
     *  
     */
    
    public java.lang.Long getMaxUploadSize(){
      return _maxUploadSize;
    }

    
    public void setMaxUploadSize(java.lang.Long value){
        checkAllowChange();
        
        this._maxUploadSize = value;
           
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
     * xml name: minRows
     *  
     */
    
    public java.lang.Integer getMinRows(){
      return _minRows;
    }

    
    public void setMinRows(java.lang.Integer value){
        checkAllowChange();
        
        this._minRows = value;
           
    }

    
    /**
     * 
     * xml name: multiValue
     *  如果为true，则表示是多个值。一般为逗号分隔的字符串
     */
    
    public boolean isMultiValue(){
      return _multiValue;
    }

    
    public void setMultiValue(boolean value){
        checkAllowChange();
        
        this._multiValue = value;
           
    }

    
    /**
     * 
     * xml name: placeholder
     *  输入框中的缺省提示信息
     */
    
    public java.lang.String getPlaceholder(){
      return _placeholder;
    }

    
    public void setPlaceholder(java.lang.String value){
        checkAllowChange();
        
        this._placeholder = value;
           
    }

    
    /**
     * 
     * xml name: prop
     *  
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
     * xml name: readonlyOn
     *  
     */
    
    public java.lang.String getReadonlyOn(){
      return _readonlyOn;
    }

    
    public void setReadonlyOn(java.lang.String value){
        checkAllowChange();
        
        this._readonlyOn = value;
           
    }

    
    /**
     * 
     * xml name: requiredOn
     *  
     */
    
    public java.lang.String getRequiredOn(){
      return _requiredOn;
    }

    
    public void setRequiredOn(java.lang.String value){
        checkAllowChange();
        
        this._requiredOn = value;
           
    }

    
    /**
     * 
     * xml name: selectFirst
     *  对于Options选项，默认选中第一个
     */
    
    public java.lang.Boolean getSelectFirst(){
      return _selectFirst;
    }

    
    public void setSelectFirst(java.lang.Boolean value){
        checkAllowChange();
        
        this._selectFirst = value;
           
    }

    
    /**
     * 
     * xml name: selection
     *  对于对象属性或者对象列表属性，通过selection来指定graphql查询字段。
     * 如果不指定，则可以根据view配置的form或者grid来推定
     */
    
    public io.nop.api.core.beans.FieldSelectionBean getSelection(){
      return _selection;
    }

    
    public void setSelection(io.nop.api.core.beans.FieldSelectionBean value){
        checkAllowChange();
        
        this._selection = value;
           
    }

    
    /**
     * 
     * xml name: sourceUrl
     *  
     */
    
    public java.lang.String getSourceUrl(){
      return _sourceUrl;
    }

    
    public void setSourceUrl(java.lang.String value){
        checkAllowChange();
        
        this._sourceUrl = value;
           
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
     * xml name: uploadUrl
     *  
     */
    
    public java.lang.String getUploadUrl(){
      return _uploadUrl;
    }

    
    public void setUploadUrl(java.lang.String value){
        checkAllowChange();
        
        this._uploadUrl = value;
           
    }

    
    /**
     * 
     * xml name: validator
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getValidator(){
      return _validator;
    }

    
    public void setValidator(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._validator = value;
           
    }

    
    public boolean hasValidator(){
        return this._validator != null && !this._validator.isEmpty();
    }
    
    /**
     * 
     * xml name: view
     *  对于对象属性或者对象列表属性，使用xview文件中定义的page去显示
     */
    
    public io.nop.xui.model.UiRefViewModel getView(){
      return _view;
    }

    
    public void setView(io.nop.xui.model.UiRefViewModel value){
        checkAllowChange();
        
        this._view = value;
           
    }

    
    /**
     * 
     * xml name: visibleOn
     *  
     */
    
    public java.lang.String getVisibleOn(){
      return _visibleOn;
    }

    
    public void setVisibleOn(java.lang.String value){
        checkAllowChange();
        
        this._visibleOn = value;
           
    }

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public java.lang.String getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.String value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._href = io.nop.api.core.util.FreezeHelper.deepFreeze(this._href);
            
           this._view = io.nop.api.core.util.FreezeHelper.deepFreeze(this._view);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("bizObjName",this.getBizObjName());
        out.put("charCase",this.getCharCase());
        out.put("className",this.getClassName());
        out.put("classNameExpr",this.getClassNameExpr());
        out.put("control",this.getControl());
        out.put("custom",this.isCustom());
        out.put("defaultValue",this.getDefaultValue());
        out.put("depends",this.getDepends());
        out.put("desc",this.getDesc());
        out.put("disabledOn",this.getDisabledOn());
        out.put("displayProp",this.getDisplayProp());
        out.put("domain",this.getDomain());
        out.put("editMode",this.getEditMode());
        out.put("filterOp",this.getFilterOp());
        out.put("genControl",this.getGenControl());
        out.put("hint",this.getHint());
        out.put("href",this.getHref());
        out.put("id",this.getId());
        out.put("idProp",this.getIdProp());
        out.put("if",this.getIf());
        out.put("joinValues",this.isJoinValues());
        out.put("label",this.getLabel());
        out.put("matchRegexp",this.getMatchRegexp());
        out.put("maxLength",this.getMaxLength());
        out.put("maxUploadSize",this.getMaxUploadSize());
        out.put("minLength",this.getMinLength());
        out.put("minRows",this.getMinRows());
        out.put("multiValue",this.isMultiValue());
        out.put("placeholder",this.getPlaceholder());
        out.put("prop",this.getProp());
        out.put("readonlyOn",this.getReadonlyOn());
        out.put("requiredOn",this.getRequiredOn());
        out.put("selectFirst",this.getSelectFirst());
        out.put("selection",this.getSelection());
        out.put("sourceUrl",this.getSourceUrl());
        out.put("stdDomain",this.getStdDomain());
        out.put("uploadUrl",this.getUploadUrl());
        out.put("validator",this.getValidator());
        out.put("view",this.getView());
        out.put("visibleOn",this.getVisibleOn());
        out.put("width",this.getWidth());
    }
}
 // resume CPD analysis - CPD-ON
