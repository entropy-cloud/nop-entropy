package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelHeaderFooter;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [199:18:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelHeaderFooter extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: center
     * 
     */
    private java.lang.String _center ;
    
    /**
     *  
     * xml name: centerExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _centerExpr ;
    
    /**
     *  
     * xml name: left
     * 
     */
    private java.lang.String _left ;
    
    /**
     *  
     * xml name: leftExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _leftExpr ;
    
    /**
     *  
     * xml name: right
     * 
     */
    private java.lang.String _right ;
    
    /**
     *  
     * xml name: rightExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _rightExpr ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private io.nop.excel.model.ExcelStyle _style ;
    
    /**
     * 
     * xml name: center
     *  
     */
    
    public java.lang.String getCenter(){
      return _center;
    }

    
    public void setCenter(java.lang.String value){
        checkAllowChange();
        
        this._center = value;
           
    }

    
    /**
     * 
     * xml name: centerExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getCenterExpr(){
      return _centerExpr;
    }

    
    public void setCenterExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._centerExpr = value;
           
    }

    
    /**
     * 
     * xml name: left
     *  
     */
    
    public java.lang.String getLeft(){
      return _left;
    }

    
    public void setLeft(java.lang.String value){
        checkAllowChange();
        
        this._left = value;
           
    }

    
    /**
     * 
     * xml name: leftExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getLeftExpr(){
      return _leftExpr;
    }

    
    public void setLeftExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._leftExpr = value;
           
    }

    
    /**
     * 
     * xml name: right
     *  
     */
    
    public java.lang.String getRight(){
      return _right;
    }

    
    public void setRight(java.lang.String value){
        checkAllowChange();
        
        this._right = value;
           
    }

    
    /**
     * 
     * xml name: rightExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getRightExpr(){
      return _rightExpr;
    }

    
    public void setRightExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._rightExpr = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public io.nop.excel.model.ExcelStyle getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.excel.model.ExcelStyle value){
        checkAllowChange();
        
        this._style = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("center",this.getCenter());
        out.putNotNull("centerExpr",this.getCenterExpr());
        out.putNotNull("left",this.getLeft());
        out.putNotNull("leftExpr",this.getLeftExpr());
        out.putNotNull("right",this.getRight());
        out.putNotNull("rightExpr",this.getRightExpr());
        out.putNotNull("style",this.getStyle());
    }

    public ExcelHeaderFooter cloneInstance(){
        ExcelHeaderFooter instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelHeaderFooter instance){
        super.copyTo(instance);
        
        instance.setCenter(this.getCenter());
        instance.setCenterExpr(this.getCenterExpr());
        instance.setLeft(this.getLeft());
        instance.setLeftExpr(this.getLeftExpr());
        instance.setRight(this.getRight());
        instance.setRightExpr(this.getRightExpr());
        instance.setStyle(this.getStyle());
    }

    protected ExcelHeaderFooter newInstance(){
        return (ExcelHeaderFooter) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
