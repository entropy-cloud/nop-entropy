package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableCellTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableCellTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: ds
     * 
     */
    private java.lang.String _ds ;
    
    /**
     *  
     * xml name: editorId
     * 
     */
    private java.lang.String _editorId ;
    
    /**
     *  
     * xml name: field
     * 
     */
    private java.lang.String _field ;
    
    /**
     *  
     * xml name: formatExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _formatExpr ;
    
    /**
     *  
     * xml name: linkExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _linkExpr ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: processExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _processExpr ;
    
    /**
     *  
     * xml name: styleIdExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _styleIdExpr ;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _valueExpr ;
    
    /**
     *  
     * xml name: viewerId
     * 
     */
    private java.lang.String _viewerId ;
    
    /**
     * 
     * xml name: ds
     *  
     */
    
    public java.lang.String getDs(){
      return _ds;
    }

    
    public void setDs(java.lang.String value){
        checkAllowChange();
        
        this._ds = value;
           
    }

    
    /**
     * 
     * xml name: editorId
     *  
     */
    
    public java.lang.String getEditorId(){
      return _editorId;
    }

    
    public void setEditorId(java.lang.String value){
        checkAllowChange();
        
        this._editorId = value;
           
    }

    
    /**
     * 
     * xml name: field
     *  
     */
    
    public java.lang.String getField(){
      return _field;
    }

    
    public void setField(java.lang.String value){
        checkAllowChange();
        
        this._field = value;
           
    }

    
    /**
     * 
     * xml name: formatExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getFormatExpr(){
      return _formatExpr;
    }

    
    public void setFormatExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._formatExpr = value;
           
    }

    
    /**
     * 
     * xml name: linkExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getLinkExpr(){
      return _linkExpr;
    }

    
    public void setLinkExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._linkExpr = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
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
     * xml name: processExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getProcessExpr(){
      return _processExpr;
    }

    
    public void setProcessExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._processExpr = value;
           
    }

    
    /**
     * 
     * xml name: styleIdExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getStyleIdExpr(){
      return _styleIdExpr;
    }

    
    public void setStyleIdExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._styleIdExpr = value;
           
    }

    
    /**
     * 
     * xml name: testExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getTestExpr(){
      return _testExpr;
    }

    
    public void setTestExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._testExpr = value;
           
    }

    
    /**
     * 
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
    }

    
    /**
     * 
     * xml name: viewerId
     *  
     */
    
    public java.lang.String getViewerId(){
      return _viewerId;
    }

    
    public void setViewerId(java.lang.String value){
        checkAllowChange();
        
        this._viewerId = value;
           
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
        
        out.putNotNull("ds",this.getDs());
        out.putNotNull("editorId",this.getEditorId());
        out.putNotNull("field",this.getField());
        out.putNotNull("formatExpr",this.getFormatExpr());
        out.putNotNull("linkExpr",this.getLinkExpr());
        out.putNotNull("name",this.getName());
        out.putNotNull("processExpr",this.getProcessExpr());
        out.putNotNull("styleIdExpr",this.getStyleIdExpr());
        out.putNotNull("testExpr",this.getTestExpr());
        out.putNotNull("valueExpr",this.getValueExpr());
        out.putNotNull("viewerId",this.getViewerId());
    }

    public WordTableCellTemplateModel cloneInstance(){
        WordTableCellTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableCellTemplateModel instance){
        super.copyTo(instance);
        
        instance.setDs(this.getDs());
        instance.setEditorId(this.getEditorId());
        instance.setField(this.getField());
        instance.setFormatExpr(this.getFormatExpr());
        instance.setLinkExpr(this.getLinkExpr());
        instance.setName(this.getName());
        instance.setProcessExpr(this.getProcessExpr());
        instance.setStyleIdExpr(this.getStyleIdExpr());
        instance.setTestExpr(this.getTestExpr());
        instance.setValueExpr(this.getValueExpr());
        instance.setViewerId(this.getViewerId());
    }

    protected WordTableCellTemplateModel newInstance(){
        return (WordTableCellTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
