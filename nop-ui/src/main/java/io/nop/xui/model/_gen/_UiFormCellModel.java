package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiFormCellModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [90:10:0:0]/nop/schema/xui/form.xdef <p>
 * 单个字段对应的界面描述
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiFormCellModel extends io.nop.xui.model.UiDisplayMeta {
    
    /**
     *  
     * xml name: collapseTitle
     * 
     */
    private java.lang.String _collapseTitle ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private java.lang.Boolean _mandatory ;
    
    /**
     *  
     * xml name: notSubmit
     * 仅用于前台控制，不提交到后台
     */
    private boolean _notSubmit  = false;
    
    /**
     *  
     * xml name: readonly
     * 
     */
    private java.lang.Boolean _readonly ;
    
    /**
     *  
     * xml name: submitOnChange
     * 只要控件值发生改变就会自动触发表单提交。
     */
    private java.lang.Boolean _submitOnChange ;
    
    /**
     *  
     * xml name: titlePosition
     * 
     */
    private java.lang.String _titlePosition ;
    
    /**
     * 
     * xml name: collapseTitle
     *  
     */
    
    public java.lang.String getCollapseTitle(){
      return _collapseTitle;
    }

    
    public void setCollapseTitle(java.lang.String value){
        checkAllowChange();
        
        this._collapseTitle = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public java.lang.Boolean getMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(java.lang.Boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
    }

    
    /**
     * 
     * xml name: notSubmit
     *  仅用于前台控制，不提交到后台
     */
    
    public boolean isNotSubmit(){
      return _notSubmit;
    }

    
    public void setNotSubmit(boolean value){
        checkAllowChange();
        
        this._notSubmit = value;
           
    }

    
    /**
     * 
     * xml name: readonly
     *  
     */
    
    public java.lang.Boolean getReadonly(){
      return _readonly;
    }

    
    public void setReadonly(java.lang.Boolean value){
        checkAllowChange();
        
        this._readonly = value;
           
    }

    
    /**
     * 
     * xml name: submitOnChange
     *  只要控件值发生改变就会自动触发表单提交。
     */
    
    public java.lang.Boolean getSubmitOnChange(){
      return _submitOnChange;
    }

    
    public void setSubmitOnChange(java.lang.Boolean value){
        checkAllowChange();
        
        this._submitOnChange = value;
           
    }

    
    /**
     * 
     * xml name: titlePosition
     *  
     */
    
    public java.lang.String getTitlePosition(){
      return _titlePosition;
    }

    
    public void setTitlePosition(java.lang.String value){
        checkAllowChange();
        
        this._titlePosition = value;
           
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
        
        out.putNotNull("collapseTitle",this.getCollapseTitle());
        out.putNotNull("mandatory",this.getMandatory());
        out.putNotNull("notSubmit",this.isNotSubmit());
        out.putNotNull("readonly",this.getReadonly());
        out.putNotNull("submitOnChange",this.getSubmitOnChange());
        out.putNotNull("titlePosition",this.getTitlePosition());
    }

    public UiFormCellModel cloneInstance(){
        UiFormCellModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiFormCellModel instance){
        super.copyTo(instance);
        
        instance.setCollapseTitle(this.getCollapseTitle());
        instance.setMandatory(this.getMandatory());
        instance.setNotSubmit(this.isNotSubmit());
        instance.setReadonly(this.getReadonly());
        instance.setSubmitOnChange(this.getSubmitOnChange());
        instance.setTitlePosition(this.getTitlePosition());
    }

    protected UiFormCellModel newInstance(){
        return (UiFormCellModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
