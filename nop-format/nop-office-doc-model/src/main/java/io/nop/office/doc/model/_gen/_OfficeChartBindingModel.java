package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeChartBindingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * WI8 image binding (B1): chartBinding and codeBinding are
 * mutually exclusive; default sizes encode B5 (chart 800x400,
 * code 0 = service default / content-fit)
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeChartBindingModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: height
     * 
     */
    private double _height  = 400;
    
    /**
     *  
     * xml name: optionExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _optionExpr ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private double _width  = 800;
    
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
     * xml name: optionExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getOptionExpr(){
      return _optionExpr;
    }

    
    public void setOptionExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._optionExpr = value;
           
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
        
        out.putNotNull("height",this.getHeight());
        out.putNotNull("optionExpr",this.getOptionExpr());
        out.putNotNull("width",this.getWidth());
    }

    public OfficeChartBindingModel cloneInstance(){
        OfficeChartBindingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeChartBindingModel instance){
        super.copyTo(instance);
        
        instance.setHeight(this.getHeight());
        instance.setOptionExpr(this.getOptionExpr());
        instance.setWidth(this.getWidth());
    }

    protected OfficeChartBindingModel newInstance(){
        return (OfficeChartBindingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
