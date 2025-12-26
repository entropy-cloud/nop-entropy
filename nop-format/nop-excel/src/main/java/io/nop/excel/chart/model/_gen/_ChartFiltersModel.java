package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartFiltersModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartFiltersModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: categoryFilter
     * 类别筛选器
     */
    private io.nop.excel.chart.model.ChartCategoryFilterModel _categoryFilter ;
    
    /**
     *  
     * xml name: seriesFilter
     * 系列筛选器
     */
    private io.nop.excel.chart.model.ChartSeriesFilterModel _seriesFilter ;
    
    /**
     *  
     * xml name: topNFilter
     * 顶部N筛选器
     */
    private io.nop.excel.chart.model.ChartTopNFilterModel _topNFilter ;
    
    /**
     *  
     * xml name: valueFilter
     * 值筛选器
     */
    private io.nop.excel.chart.model.ChartValueFilterModel _valueFilter ;
    
    /**
     * 
     * xml name: categoryFilter
     *  类别筛选器
     */
    
    public io.nop.excel.chart.model.ChartCategoryFilterModel getCategoryFilter(){
      return _categoryFilter;
    }

    
    public void setCategoryFilter(io.nop.excel.chart.model.ChartCategoryFilterModel value){
        checkAllowChange();
        
        this._categoryFilter = value;
           
    }

    
    /**
     * 
     * xml name: seriesFilter
     *  系列筛选器
     */
    
    public io.nop.excel.chart.model.ChartSeriesFilterModel getSeriesFilter(){
      return _seriesFilter;
    }

    
    public void setSeriesFilter(io.nop.excel.chart.model.ChartSeriesFilterModel value){
        checkAllowChange();
        
        this._seriesFilter = value;
           
    }

    
    /**
     * 
     * xml name: topNFilter
     *  顶部N筛选器
     */
    
    public io.nop.excel.chart.model.ChartTopNFilterModel getTopNFilter(){
      return _topNFilter;
    }

    
    public void setTopNFilter(io.nop.excel.chart.model.ChartTopNFilterModel value){
        checkAllowChange();
        
        this._topNFilter = value;
           
    }

    
    /**
     * 
     * xml name: valueFilter
     *  值筛选器
     */
    
    public io.nop.excel.chart.model.ChartValueFilterModel getValueFilter(){
      return _valueFilter;
    }

    
    public void setValueFilter(io.nop.excel.chart.model.ChartValueFilterModel value){
        checkAllowChange();
        
        this._valueFilter = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._categoryFilter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._categoryFilter);
            
           this._seriesFilter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._seriesFilter);
            
           this._topNFilter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._topNFilter);
            
           this._valueFilter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._valueFilter);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("categoryFilter",this.getCategoryFilter());
        out.putNotNull("seriesFilter",this.getSeriesFilter());
        out.putNotNull("topNFilter",this.getTopNFilter());
        out.putNotNull("valueFilter",this.getValueFilter());
    }

    public ChartFiltersModel cloneInstance(){
        ChartFiltersModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartFiltersModel instance){
        super.copyTo(instance);
        
        instance.setCategoryFilter(this.getCategoryFilter());
        instance.setSeriesFilter(this.getSeriesFilter());
        instance.setTopNFilter(this.getTopNFilter());
        instance.setValueFilter(this.getValueFilter());
    }

    protected ChartFiltersModel newInstance(){
        return (ChartFiltersModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
