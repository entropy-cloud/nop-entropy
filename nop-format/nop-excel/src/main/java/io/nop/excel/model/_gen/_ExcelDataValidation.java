package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelDataValidation;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * -
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelDataValidation extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allowBlank
     * 可选。允许为空，"1" 表示 true。
     */
    private java.lang.Boolean _allowBlank ;
    
    /**
     *  
     * xml name: error
     * 必需（当showErrorMessage="1"时）。错误消息内容。
     */
    private java.lang.String _error ;
    
    /**
     *  
     * xml name: errorStyle
     * 可选。错误样式：'stop', 'warning', 'information'。
     */
    private io.nop.excel.model.constants.ExcelDataValidationErrorStyle _errorStyle ;
    
    /**
     *  
     * xml name: errorTitle
     * 可选。错误标题。
     */
    private java.lang.String _errorTitle ;
    
    /**
     *  
     * xml name: formula1
     * <formula1> 必需。第一个公式或值。
     * - 对于数值类型：表示最小值或比较值
     * - 对于 list 类型：表示列表源，如 "选项1,选项2,选项3" 或 Sheet1!$A$1:$A$5
     * - 对于 textLength：表示文本长度限制
     */
    private java.lang.String _formula1 ;
    
    /**
     *  
     * xml name: formula2
     * <formula2> 可选。第二个公式或值。
     * - 主要用于 between 和 notBetween 操作符，表示最大值
     * - 对于其他操作符通常为空
     */
    private java.lang.String _formula2 ;
    
    /**
     *  
     * xml name: id
     * 可选。Excel内部使用的GUID标识符。
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: imeMode
     * 可选。输入法模式，主要用于东亚语言输入法控制。
     * - 'noControl'：不控制IME（默认）
     * - 'off'：关闭IME（英文模式）
     * - 'on'：开启IME（中文/日文/韩文输入模式）
     * - 'disabled'：禁用IME
     * - 'hiragana'：平假名模式（日文）
     * - 'fullKatakana'：全角片假名模式（日文）
     * - 'halfKatakana'：半角片假名模式（日文）
     * - 'fullAlpha'：全角字母模式
     * - 'halfAlpha'：半角字母模式
     * - 'fullHangul'：全角韩文模式
     * - 'halfHangul'：半角韩文模式
     */
    private io.nop.excel.model.constants.ExcelDataValidationImeMode _imeMode ;
    
    /**
     *  
     * xml name: operator
     * 
     */
    private io.nop.excel.model.constants.ExcelDataValidationOperator _operator ;
    
    /**
     *  
     * xml name: prompt
     * 可选。输入提示内容。
     */
    private java.lang.String _prompt ;
    
    /**
     *  
     * xml name: promptTitle
     * 可选。输入提示标题。
     */
    private java.lang.String _promptTitle ;
    
    /**
     *  
     * xml name: showDropDown
     * 可选。显示下拉列表，"1" 表示 true（仅对list类型有效）。
     */
    private java.lang.Boolean _showDropDown ;
    
    /**
     *  
     * xml name: showErrorMessage
     * 可选。显示错误消息，"1" 表示 true。
     */
    private java.lang.Boolean _showErrorMessage ;
    
    /**
     *  
     * xml name: showInputMessage
     * 可选。显示输入消息，"1" 表示 true。
     */
    private java.lang.Boolean _showInputMessage ;
    
    /**
     *  
     * xml name: sqref
     * 必需。应用此验证规则的单元格或范围。
     */
    private java.lang.String _sqref ;
    
    /**
     *  
     * xml name: type
     * 必需。验证类型：'whole', 'decimal', 'list', 'date', 'time', 'textLength', 'custom'。
     */
    private io.nop.excel.model.constants.ExcelDataValidationType _type ;
    
    /**
     * 
     * xml name: allowBlank
     *  可选。允许为空，"1" 表示 true。
     */
    
    public java.lang.Boolean getAllowBlank(){
      return _allowBlank;
    }

    
    public void setAllowBlank(java.lang.Boolean value){
        checkAllowChange();
        
        this._allowBlank = value;
           
    }

    
    /**
     * 
     * xml name: error
     *  必需（当showErrorMessage="1"时）。错误消息内容。
     */
    
    public java.lang.String getError(){
      return _error;
    }

    
    public void setError(java.lang.String value){
        checkAllowChange();
        
        this._error = value;
           
    }

    
    /**
     * 
     * xml name: errorStyle
     *  可选。错误样式：'stop', 'warning', 'information'。
     */
    
    public io.nop.excel.model.constants.ExcelDataValidationErrorStyle getErrorStyle(){
      return _errorStyle;
    }

    
    public void setErrorStyle(io.nop.excel.model.constants.ExcelDataValidationErrorStyle value){
        checkAllowChange();
        
        this._errorStyle = value;
           
    }

    
    /**
     * 
     * xml name: errorTitle
     *  可选。错误标题。
     */
    
    public java.lang.String getErrorTitle(){
      return _errorTitle;
    }

    
    public void setErrorTitle(java.lang.String value){
        checkAllowChange();
        
        this._errorTitle = value;
           
    }

    
    /**
     * 
     * xml name: formula1
     *  <formula1> 必需。第一个公式或值。
     * - 对于数值类型：表示最小值或比较值
     * - 对于 list 类型：表示列表源，如 "选项1,选项2,选项3" 或 Sheet1!$A$1:$A$5
     * - 对于 textLength：表示文本长度限制
     */
    
    public java.lang.String getFormula1(){
      return _formula1;
    }

    
    public void setFormula1(java.lang.String value){
        checkAllowChange();
        
        this._formula1 = value;
           
    }

    
    /**
     * 
     * xml name: formula2
     *  <formula2> 可选。第二个公式或值。
     * - 主要用于 between 和 notBetween 操作符，表示最大值
     * - 对于其他操作符通常为空
     */
    
    public java.lang.String getFormula2(){
      return _formula2;
    }

    
    public void setFormula2(java.lang.String value){
        checkAllowChange();
        
        this._formula2 = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  可选。Excel内部使用的GUID标识符。
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
     * xml name: imeMode
     *  可选。输入法模式，主要用于东亚语言输入法控制。
     * - 'noControl'：不控制IME（默认）
     * - 'off'：关闭IME（英文模式）
     * - 'on'：开启IME（中文/日文/韩文输入模式）
     * - 'disabled'：禁用IME
     * - 'hiragana'：平假名模式（日文）
     * - 'fullKatakana'：全角片假名模式（日文）
     * - 'halfKatakana'：半角片假名模式（日文）
     * - 'fullAlpha'：全角字母模式
     * - 'halfAlpha'：半角字母模式
     * - 'fullHangul'：全角韩文模式
     * - 'halfHangul'：半角韩文模式
     */
    
    public io.nop.excel.model.constants.ExcelDataValidationImeMode getImeMode(){
      return _imeMode;
    }

    
    public void setImeMode(io.nop.excel.model.constants.ExcelDataValidationImeMode value){
        checkAllowChange();
        
        this._imeMode = value;
           
    }

    
    /**
     * 
     * xml name: operator
     *  
     */
    
    public io.nop.excel.model.constants.ExcelDataValidationOperator getOperator(){
      return _operator;
    }

    
    public void setOperator(io.nop.excel.model.constants.ExcelDataValidationOperator value){
        checkAllowChange();
        
        this._operator = value;
           
    }

    
    /**
     * 
     * xml name: prompt
     *  可选。输入提示内容。
     */
    
    public java.lang.String getPrompt(){
      return _prompt;
    }

    
    public void setPrompt(java.lang.String value){
        checkAllowChange();
        
        this._prompt = value;
           
    }

    
    /**
     * 
     * xml name: promptTitle
     *  可选。输入提示标题。
     */
    
    public java.lang.String getPromptTitle(){
      return _promptTitle;
    }

    
    public void setPromptTitle(java.lang.String value){
        checkAllowChange();
        
        this._promptTitle = value;
           
    }

    
    /**
     * 
     * xml name: showDropDown
     *  可选。显示下拉列表，"1" 表示 true（仅对list类型有效）。
     */
    
    public java.lang.Boolean getShowDropDown(){
      return _showDropDown;
    }

    
    public void setShowDropDown(java.lang.Boolean value){
        checkAllowChange();
        
        this._showDropDown = value;
           
    }

    
    /**
     * 
     * xml name: showErrorMessage
     *  可选。显示错误消息，"1" 表示 true。
     */
    
    public java.lang.Boolean getShowErrorMessage(){
      return _showErrorMessage;
    }

    
    public void setShowErrorMessage(java.lang.Boolean value){
        checkAllowChange();
        
        this._showErrorMessage = value;
           
    }

    
    /**
     * 
     * xml name: showInputMessage
     *  可选。显示输入消息，"1" 表示 true。
     */
    
    public java.lang.Boolean getShowInputMessage(){
      return _showInputMessage;
    }

    
    public void setShowInputMessage(java.lang.Boolean value){
        checkAllowChange();
        
        this._showInputMessage = value;
           
    }

    
    /**
     * 
     * xml name: sqref
     *  必需。应用此验证规则的单元格或范围。
     */
    
    public java.lang.String getSqref(){
      return _sqref;
    }

    
    public void setSqref(java.lang.String value){
        checkAllowChange();
        
        this._sqref = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  必需。验证类型：'whole', 'decimal', 'list', 'date', 'time', 'textLength', 'custom'。
     */
    
    public io.nop.excel.model.constants.ExcelDataValidationType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.model.constants.ExcelDataValidationType value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("allowBlank",this.getAllowBlank());
        out.putNotNull("error",this.getError());
        out.putNotNull("errorStyle",this.getErrorStyle());
        out.putNotNull("errorTitle",this.getErrorTitle());
        out.putNotNull("formula1",this.getFormula1());
        out.putNotNull("formula2",this.getFormula2());
        out.putNotNull("id",this.getId());
        out.putNotNull("imeMode",this.getImeMode());
        out.putNotNull("operator",this.getOperator());
        out.putNotNull("prompt",this.getPrompt());
        out.putNotNull("promptTitle",this.getPromptTitle());
        out.putNotNull("showDropDown",this.getShowDropDown());
        out.putNotNull("showErrorMessage",this.getShowErrorMessage());
        out.putNotNull("showInputMessage",this.getShowInputMessage());
        out.putNotNull("sqref",this.getSqref());
        out.putNotNull("type",this.getType());
    }

    public ExcelDataValidation cloneInstance(){
        ExcelDataValidation instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelDataValidation instance){
        super.copyTo(instance);
        
        instance.setAllowBlank(this.getAllowBlank());
        instance.setError(this.getError());
        instance.setErrorStyle(this.getErrorStyle());
        instance.setErrorTitle(this.getErrorTitle());
        instance.setFormula1(this.getFormula1());
        instance.setFormula2(this.getFormula2());
        instance.setId(this.getId());
        instance.setImeMode(this.getImeMode());
        instance.setOperator(this.getOperator());
        instance.setPrompt(this.getPrompt());
        instance.setPromptTitle(this.getPromptTitle());
        instance.setShowDropDown(this.getShowDropDown());
        instance.setShowErrorMessage(this.getShowErrorMessage());
        instance.setShowInputMessage(this.getShowInputMessage());
        instance.setSqref(this.getSqref());
        instance.setType(this.getType());
    }

    protected ExcelDataValidation newInstance(){
        return (ExcelDataValidation) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
