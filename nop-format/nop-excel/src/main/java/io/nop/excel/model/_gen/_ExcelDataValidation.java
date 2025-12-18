package io.nop.excel.model._gen;

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
     * 可选。如果为 true (默认值)，则允许单元格为空。
     */
    private java.lang.Boolean _allowBlank ;
    
    /**
     *  
     * xml name: errorMessage
     * 可选。错误提示框的正文内容。
     */
    private java.lang.String _errorMessage ;
    
    /**
     *  
     * xml name: errorStyle
     * 可选。错误提示框的样式，可选值为 'stop' (默认), 'warning', 'information'。
     */
    private io.nop.excel.model.constants.ExcelDataValidationErrorStyle _errorStyle ;
    
    /**
     *  
     * xml name: errorTitle
     * 可选。错误提示框的标题。
     */
    private java.lang.String _errorTitle ;
    
    /**
     *  
     * xml name: formula1
     * <formula1> 可选。第一个公式或值。用于定义验证规则的边界或列表源。
     */
    private java.lang.String _formula1 ;
    
    /**
     *  
     * xml name: formula2
     * <formula2> 可选。第二个公式或值。主要与 'between' 和 'notBetween' 运算符配合使用。
     */
    private java.lang.String _formula2 ;
    
    /**
     *  
     * xml name: id
     * 可选。数据有效性规则的唯一标识符。
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: imeMode
     * 可选。控制输入法编辑器 (IME) 的默认状态，主要用于东亚语言。
     */
    private io.nop.excel.model.constants.ExcelDataValidationImeMode _imeMode ;
    
    /**
     *  
     * xml name: operator
     * 可选。比较运算符，如 'between', 'equal', 'greaterThan' 等。
     */
    private io.nop.excel.model.constants.ExcelDataValidationOperator _operator ;
    
    /**
     *  
     * xml name: prompt
     * 可选。输入提示框的正文内容。
     */
    private java.lang.String _prompt ;
    
    /**
     *  
     * xml name: promptTitle
     * 可选。输入提示框的标题。
     */
    private java.lang.String _promptTitle ;
    
    /**
     *  
     * xml name: showDropDown
     * 
     */
    private java.lang.Boolean _showDropDown ;
    
    /**
     *  
     * xml name: showErrorMessage
     * 可选。如果为 true (默认值)，则在输入无效数据时显示错误消息。
     */
    private java.lang.Boolean _showErrorMessage ;
    
    /**
     *  
     * xml name: showInputMessage
     * 可选。如果为 true (默认值)，则在选中单元格时显示输入提示。
     */
    private java.lang.Boolean _showInputMessage ;
    
    /**
     *  
     * xml name: sqref
     * 必需。应用此验证规则的单元格或范围，例如 "A1" 或 "A1:C5 B2:B10"。
     */
    private java.lang.String _sqref ;
    
    /**
     *  
     * xml name: type
     * 必需。指定验证的类型，如 'whole', 'decimal', 'list', 'date', 'time', 'textLength', 'custom'。
     */
    private io.nop.excel.model.constants.ExcelDataValidationType _type ;
    
    /**
     * 
     * xml name: allowBlank
     *  可选。如果为 true (默认值)，则允许单元格为空。
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
     * xml name: errorMessage
     *  可选。错误提示框的正文内容。
     */
    
    public java.lang.String getError(){
      return _errorMessage;
    }

    
    public void setError(java.lang.String value){
        checkAllowChange();
        
        this._errorMessage = value;
           
    }

    
    /**
     * 
     * xml name: errorStyle
     *  可选。错误提示框的样式，可选值为 'stop' (默认), 'warning', 'information'。
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
     *  可选。错误提示框的标题。
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
     *  <formula1> 可选。第一个公式或值。用于定义验证规则的边界或列表源。
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
     *  <formula2> 可选。第二个公式或值。主要与 'between' 和 'notBetween' 运算符配合使用。
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
     *  可选。数据有效性规则的唯一标识符。
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
     *  可选。控制输入法编辑器 (IME) 的默认状态，主要用于东亚语言。
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
     *  可选。比较运算符，如 'between', 'equal', 'greaterThan' 等。
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
     *  可选。输入提示框的正文内容。
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
     *  可选。输入提示框的标题。
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
     *  
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
     *  可选。如果为 true (默认值)，则在输入无效数据时显示错误消息。
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
     *  可选。如果为 true (默认值)，则在选中单元格时显示输入提示。
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
     *  必需。应用此验证规则的单元格或范围，例如 "A1" 或 "A1:C5 B2:B10"。
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
     *  必需。指定验证的类型，如 'whole', 'decimal', 'list', 'date', 'time', 'textLength', 'custom'。
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
        out.putNotNull("errorMessage",this.getError());
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
