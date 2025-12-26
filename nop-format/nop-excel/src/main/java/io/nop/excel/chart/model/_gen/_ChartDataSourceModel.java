package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartDataSourceModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Data source configuration
 * 对应 Excel POI 中的 ChartDataSource，支持多种数据来源
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartDataSourceModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: api
     * REST API data source
     * 对应外部 API 数据获取
     */
    private io.nop.excel.chart.model.ChartApiDataModel _api ;
    
    /**
     *  
     * xml name: dataCellRef
     * 
     */
    private java.lang.String _dataCellRef ;
    
    /**
     *  
     * xml name: dataExpr
     * Dynamic expression data source
     * 对应计算字段和动态数据
     */
    private java.lang.String _dataExpr ;
    
    /**
     *  
     * xml name: staticData
     * Static inline data
     * 对应 POI 中直接设置数据值的方式
     */
    private java.lang.Object _staticData ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.chart.constants.ChartDataSourceType _type ;
    
    /**
     * 
     * xml name: api
     *  REST API data source
     * 对应外部 API 数据获取
     */
    
    public io.nop.excel.chart.model.ChartApiDataModel getApi(){
      return _api;
    }

    
    public void setApi(io.nop.excel.chart.model.ChartApiDataModel value){
        checkAllowChange();
        
        this._api = value;
           
    }

    
    /**
     * 
     * xml name: dataCellRef
     *  
     */
    
    public java.lang.String getDataCellRef(){
      return _dataCellRef;
    }

    
    public void setDataCellRef(java.lang.String value){
        checkAllowChange();
        
        this._dataCellRef = value;
           
    }

    
    /**
     * 
     * xml name: dataExpr
     *  Dynamic expression data source
     * 对应计算字段和动态数据
     */
    
    public java.lang.String getDataExpr(){
      return _dataExpr;
    }

    
    public void setDataExpr(java.lang.String value){
        checkAllowChange();
        
        this._dataExpr = value;
           
    }

    
    /**
     * 
     * xml name: staticData
     *  Static inline data
     * 对应 POI 中直接设置数据值的方式
     */
    
    public java.lang.Object getStaticData(){
      return _staticData;
    }

    
    public void setStaticData(java.lang.Object value){
        checkAllowChange();
        
        this._staticData = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.chart.constants.ChartDataSourceType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.chart.constants.ChartDataSourceType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._api = io.nop.api.core.util.FreezeHelper.deepFreeze(this._api);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("api",this.getApi());
        out.putNotNull("dataCellRef",this.getDataCellRef());
        out.putNotNull("dataExpr",this.getDataExpr());
        out.putNotNull("staticData",this.getStaticData());
        out.putNotNull("type",this.getType());
    }

    public ChartDataSourceModel cloneInstance(){
        ChartDataSourceModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartDataSourceModel instance){
        super.copyTo(instance);
        
        instance.setApi(this.getApi());
        instance.setDataCellRef(this.getDataCellRef());
        instance.setDataExpr(this.getDataExpr());
        instance.setStaticData(this.getStaticData());
        instance.setType(this.getType());
    }

    protected ChartDataSourceModel newInstance(){
        return (ChartDataSourceModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
