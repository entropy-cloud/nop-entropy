package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelDataValidation;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelDataValidation extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allowBlank
     * 是否允许为空
     */
    private java.lang.Boolean _allowBlank ;
    
    /**
     *  
     * xml name: formula
     * 
     */
    private java.lang.String _formula ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: showErrorMessage
     * 是否显示错误提示
     */
    private java.lang.Boolean _showErrorMessage ;
    
    /**
     *  
     * xml name: showInputMessage
     * 是否显示输入提示
     */
    private java.lang.Boolean _showInputMessage ;
    
    /**
     *  
     * xml name: sqref
     * 指定数据验证的单元格范围
     */
    private java.lang.String _sqref ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: allowBlank
     *  是否允许为空
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
     * xml name: formula
     *  
     */
    
    public java.lang.String getFormula(){
      return _formula;
    }

    
    public void setFormula(java.lang.String value){
        checkAllowChange();
        
        this._formula = value;
           
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
     * xml name: showErrorMessage
     *  是否显示错误提示
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
     *  是否显示输入提示
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
     *  指定数据验证的单元格范围
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
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
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
        out.putNotNull("formula",this.getFormula());
        out.putNotNull("id",this.getId());
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
        instance.setFormula(this.getFormula());
        instance.setId(this.getId());
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
