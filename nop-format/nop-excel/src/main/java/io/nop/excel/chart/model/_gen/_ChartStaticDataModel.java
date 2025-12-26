package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartStaticDataModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Static inline data
 * 对应 POI 中直接设置数据值的方式
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartStaticDataModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: data
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _data ;
    
    /**
     * 
     * xml name: data
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getData(){
      return _data;
    }

    
    public void setData(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._data = value;
           
    }

    
    public boolean hasData(){
        return this._data != null && !this._data.isEmpty();
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
        
        out.putNotNull("data",this.getData());
    }

    public ChartStaticDataModel cloneInstance(){
        ChartStaticDataModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartStaticDataModel instance){
        super.copyTo(instance);
        
        instance.setData(this.getData());
    }

    protected ChartStaticDataModel newInstance(){
        return (ChartStaticDataModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
