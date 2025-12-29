package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartFontsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Font configurations for different chart elements
 * 对应 Excel 中不同元素的字体设置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartFontsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: axis
     * 
     */
    private io.nop.excel.model.ExcelFont _axis ;
    
    /**
     *  
     * xml name: default
     * 
     */
    private io.nop.excel.model.ExcelFont _default ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private io.nop.excel.model.ExcelFont _label ;
    
    /**
     *  
     * xml name: legend
     * 
     */
    private io.nop.excel.model.ExcelFont _legend ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private io.nop.excel.model.ExcelFont _title ;
    
    /**
     * 
     * xml name: axis
     *  
     */
    
    public io.nop.excel.model.ExcelFont getAxis(){
      return _axis;
    }

    
    public void setAxis(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._axis = value;
           
    }

    
    /**
     * 
     * xml name: default
     *  
     */
    
    public io.nop.excel.model.ExcelFont getDefault(){
      return _default;
    }

    
    public void setDefault(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._default = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public io.nop.excel.model.ExcelFont getLabel(){
      return _label;
    }

    
    public void setLabel(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: legend
     *  
     */
    
    public io.nop.excel.model.ExcelFont getLegend(){
      return _legend;
    }

    
    public void setLegend(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._legend = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  
     */
    
    public io.nop.excel.model.ExcelFont getTitle(){
      return _title;
    }

    
    public void setTitle(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._title = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._axis = io.nop.api.core.util.FreezeHelper.deepFreeze(this._axis);
            
           this._default = io.nop.api.core.util.FreezeHelper.deepFreeze(this._default);
            
           this._label = io.nop.api.core.util.FreezeHelper.deepFreeze(this._label);
            
           this._legend = io.nop.api.core.util.FreezeHelper.deepFreeze(this._legend);
            
           this._title = io.nop.api.core.util.FreezeHelper.deepFreeze(this._title);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("axis",this.getAxis());
        out.putNotNull("default",this.getDefault());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("legend",this.getLegend());
        out.putNotNull("title",this.getTitle());
    }

    public ChartFontsModel cloneInstance(){
        ChartFontsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartFontsModel instance){
        super.copyTo(instance);
        
        instance.setAxis(this.getAxis());
        instance.setDefault(this.getDefault());
        instance.setLabel(this.getLabel());
        instance.setLegend(this.getLegend());
        instance.setTitle(this.getTitle());
    }

    protected ChartFontsModel newInstance(){
        return (ChartFontsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
