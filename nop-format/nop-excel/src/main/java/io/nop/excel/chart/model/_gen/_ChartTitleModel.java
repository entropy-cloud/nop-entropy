package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Chart title configuration
 * 对应 Excel POI 中的 XSSFChart.getTitle() 和 ChartTitle
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTitleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: font
     * Title font styling
     * 对应 Excel POI 中的 ChartTitle.getTextRun().setFontSize() 等字体属性
     */
    private io.nop.excel.model.ExcelFont _font ;
    
    /**
     *  
     * xml name: position
     * 
     */
    private io.nop.excel.chart.constants.ChartTitlePosition _position ;
    
    /**
     *  
     * xml name: text
     * 
     */
    private java.lang.String _text ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private java.lang.Boolean _visible  = true;
    
    /**
     * 
     * xml name: font
     *  Title font styling
     * 对应 Excel POI 中的 ChartTitle.getTextRun().setFontSize() 等字体属性
     */
    
    public io.nop.excel.model.ExcelFont getFont(){
      return _font;
    }

    
    public void setFont(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._font = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  
     */
    
    public io.nop.excel.chart.constants.ChartTitlePosition getPosition(){
      return _position;
    }

    
    public void setPosition(io.nop.excel.chart.constants.ChartTitlePosition value){
        checkAllowChange();
        
        this._position = value;
           
    }

    
    /**
     * 
     * xml name: text
     *  
     */
    
    public java.lang.String getText(){
      return _text;
    }

    
    public void setText(java.lang.String value){
        checkAllowChange();
        
        this._text = value;
           
    }

    
    /**
     * 
     * xml name: visible
     *  
     */
    
    public java.lang.Boolean getVisible(){
      return _visible;
    }

    
    public void setVisible(java.lang.Boolean value){
        checkAllowChange();
        
        this._visible = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("font",this.getFont());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("text",this.getText());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartTitleModel cloneInstance(){
        ChartTitleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTitleModel instance){
        super.copyTo(instance);
        
        instance.setFont(this.getFont());
        instance.setPosition(this.getPosition());
        instance.setText(this.getText());
        instance.setVisible(this.getVisible());
    }

    protected ChartTitleModel newInstance(){
        return (ChartTitleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
