package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTrendLineModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 双向转换规则：
 * Excel/POI                 ↔ ECharts
 * type=LINEAR              ↔ markLine.type='average' 或 regression.type='linear'
 * type=MOVING_AVG          ↔ markLine.type='average' (带 period)
 * displayEquation=true     ↔ label.formatter 显示公式
 * 不支持的类型             ↔ 转换为 linear 或 忽略
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTrendLineModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: displayEquation
     * 是否显示公式，对应 ECharts label.formatter
     */
    private java.lang.Boolean _displayEquation  = false;
    
    /**
     *  
     * xml name: id
     * 趋势线唯一标识，转换时作为 ECharts markLine 的 name
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: lineStyle
     * Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    private io.nop.excel.chart.model.ChartLineStyleModel _lineStyle ;
    
    /**
     *  
     * xml name: name
     * 显示名称，对应 ECharts 中的 name
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: period
     * 移动平均周期（仅 MOVING_AVG 有效），对应 ECharts markLine 的 period
     */
    private java.lang.Integer _period  = 2;
    
    /**
     *  
     * xml name: type
     * 支持转换的类型：LINEAR(线性)/MOVING_AVG(移动平均)
     * ⚠️ 注意：POLYNOMIAL/POWER/EXPONENTIAL/LOGARITHMIC 在 ECharts 中需要自定义实现
     */
    private io.nop.excel.chart.constants.ChartTrendLineType _type ;
    
    /**
     * 
     * xml name: displayEquation
     *  是否显示公式，对应 ECharts label.formatter
     */
    
    public java.lang.Boolean getDisplayEquation(){
      return _displayEquation;
    }

    
    public void setDisplayEquation(java.lang.Boolean value){
        checkAllowChange();
        
        this._displayEquation = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  趋势线唯一标识，转换时作为 ECharts markLine 的 name
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
     * xml name: lineStyle
     *  Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    
    public io.nop.excel.chart.model.ChartLineStyleModel getLineStyle(){
      return _lineStyle;
    }

    
    public void setLineStyle(io.nop.excel.chart.model.ChartLineStyleModel value){
        checkAllowChange();
        
        this._lineStyle = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  显示名称，对应 ECharts 中的 name
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
     * xml name: period
     *  移动平均周期（仅 MOVING_AVG 有效），对应 ECharts markLine 的 period
     */
    
    public java.lang.Integer getPeriod(){
      return _period;
    }

    
    public void setPeriod(java.lang.Integer value){
        checkAllowChange();
        
        this._period = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  支持转换的类型：LINEAR(线性)/MOVING_AVG(移动平均)
     * ⚠️ 注意：POLYNOMIAL/POWER/EXPONENTIAL/LOGARITHMIC 在 ECharts 中需要自定义实现
     */
    
    public io.nop.excel.chart.constants.ChartTrendLineType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.chart.constants.ChartTrendLineType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._lineStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._lineStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("displayEquation",this.getDisplayEquation());
        out.putNotNull("id",this.getId());
        out.putNotNull("lineStyle",this.getLineStyle());
        out.putNotNull("name",this.getName());
        out.putNotNull("period",this.getPeriod());
        out.putNotNull("type",this.getType());
    }

    public ChartTrendLineModel cloneInstance(){
        ChartTrendLineModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTrendLineModel instance){
        super.copyTo(instance);
        
        instance.setDisplayEquation(this.getDisplayEquation());
        instance.setId(this.getId());
        instance.setLineStyle(this.getLineStyle());
        instance.setName(this.getName());
        instance.setPeriod(this.getPeriod());
        instance.setType(this.getType());
    }

    protected ChartTrendLineModel newInstance(){
        return (ChartTrendLineModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
