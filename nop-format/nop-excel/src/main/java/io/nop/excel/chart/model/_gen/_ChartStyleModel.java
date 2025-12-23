package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartStyleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Global chart styling and theming
 * 对应 Excel 图表的整体样式主题
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartStyleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: background
     * Chart background styling
     * 对应 Excel POI 中的 ChartSpace 背景设置
     */
    private io.nop.excel.chart.model.ChartBackgroundModel _background ;
    
    /**
     *  
     * xml name: border
     * Chart border styling
     * 对应 Excel 图表边框设置
     */
    private io.nop.excel.chart.model.ChartBorderModel _border ;
    
    /**
     *  
     * xml name: colorPalette
     * 
     */
    private java.lang.String _colorPalette ;
    
    /**
     *  
     * xml name: colors
     * 
     */
    private java.util.List<java.lang.String> _colors ;
    
    /**
     *  
     * xml name: fonts
     * Font configurations for different chart elements
     * 对应 Excel 中不同元素的字体设置
     */
    private io.nop.excel.chart.model.ChartFontsModel _fonts ;
    
    /**
     *  
     * xml name: theme
     * 
     */
    private java.lang.String _theme ;
    
    /**
     * 
     * xml name: background
     *  Chart background styling
     * 对应 Excel POI 中的 ChartSpace 背景设置
     */
    
    public io.nop.excel.chart.model.ChartBackgroundModel getBackground(){
      return _background;
    }

    
    public void setBackground(io.nop.excel.chart.model.ChartBackgroundModel value){
        checkAllowChange();
        
        this._background = value;
           
    }

    
    /**
     * 
     * xml name: border
     *  Chart border styling
     * 对应 Excel 图表边框设置
     */
    
    public io.nop.excel.chart.model.ChartBorderModel getBorder(){
      return _border;
    }

    
    public void setBorder(io.nop.excel.chart.model.ChartBorderModel value){
        checkAllowChange();
        
        this._border = value;
           
    }

    
    /**
     * 
     * xml name: colorPalette
     *  
     */
    
    public java.lang.String getColorPalette(){
      return _colorPalette;
    }

    
    public void setColorPalette(java.lang.String value){
        checkAllowChange();
        
        this._colorPalette = value;
           
    }

    
    /**
     * 
     * xml name: colors
     *  
     */
    
    public java.util.List<java.lang.String> getColors(){
      return _colors;
    }

    
    public void setColors(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._colors = value;
           
    }

    
    /**
     * 
     * xml name: fonts
     *  Font configurations for different chart elements
     * 对应 Excel 中不同元素的字体设置
     */
    
    public io.nop.excel.chart.model.ChartFontsModel getFonts(){
      return _fonts;
    }

    
    public void setFonts(io.nop.excel.chart.model.ChartFontsModel value){
        checkAllowChange();
        
        this._fonts = value;
           
    }

    
    /**
     * 
     * xml name: theme
     *  
     */
    
    public java.lang.String getTheme(){
      return _theme;
    }

    
    public void setTheme(java.lang.String value){
        checkAllowChange();
        
        this._theme = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._background = io.nop.api.core.util.FreezeHelper.deepFreeze(this._background);
            
           this._border = io.nop.api.core.util.FreezeHelper.deepFreeze(this._border);
            
           this._fonts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fonts);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("background",this.getBackground());
        out.putNotNull("border",this.getBorder());
        out.putNotNull("colorPalette",this.getColorPalette());
        out.putNotNull("colors",this.getColors());
        out.putNotNull("fonts",this.getFonts());
        out.putNotNull("theme",this.getTheme());
    }

    public ChartStyleModel cloneInstance(){
        ChartStyleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartStyleModel instance){
        super.copyTo(instance);
        
        instance.setBackground(this.getBackground());
        instance.setBorder(this.getBorder());
        instance.setColorPalette(this.getColorPalette());
        instance.setColors(this.getColors());
        instance.setFonts(this.getFonts());
        instance.setTheme(this.getTheme());
    }

    protected ChartStyleModel newInstance(){
        return (ChartStyleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
