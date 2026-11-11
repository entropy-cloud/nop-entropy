package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeCodeBindingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeCodeBindingModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: codeExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _codeExpr ;
    
    /**
     *  
     * xml name: codeType
     * 
     */
    private java.lang.String _codeType ;
    
    /**
     *  
     * xml name: height
     * 
     */
    private double _height  = 0;
    
    /**
     *  
     * xml name: width
     * 
     */
    private double _width  = 0;
    
    /**
     * 
     * xml name: codeExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getCodeExpr(){
      return _codeExpr;
    }

    
    public void setCodeExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._codeExpr = value;
           
    }

    
    /**
     * 
     * xml name: codeType
     *  
     */
    
    public java.lang.String getCodeType(){
      return _codeType;
    }

    
    public void setCodeType(java.lang.String value){
        checkAllowChange();
        
        this._codeType = value;
           
    }

    
    /**
     * 
     * xml name: height
     *  
     */
    
    public double getHeight(){
      return _height;
    }

    
    public void setHeight(double value){
        checkAllowChange();
        
        this._height = value;
           
    }

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public double getWidth(){
      return _width;
    }

    
    public void setWidth(double value){
        checkAllowChange();
        
        this._width = value;
           
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
        
        out.putNotNull("codeExpr",this.getCodeExpr());
        out.putNotNull("codeType",this.getCodeType());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("width",this.getWidth());
    }

    public OfficeCodeBindingModel cloneInstance(){
        OfficeCodeBindingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeCodeBindingModel instance){
        super.copyTo(instance);
        
        instance.setCodeExpr(this.getCodeExpr());
        instance.setCodeType(this.getCodeType());
        instance.setHeight(this.getHeight());
        instance.setWidth(this.getWidth());
    }

    protected OfficeCodeBindingModel newInstance(){
        return (OfficeCodeBindingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
