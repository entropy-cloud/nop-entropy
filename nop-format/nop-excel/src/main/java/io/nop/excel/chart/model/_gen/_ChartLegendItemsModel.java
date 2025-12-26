package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartLegendItemsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Legend items configuration
 * 对应 Legend 中各项的显示设置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartLegendItemsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: itemHeight
     * 
     */
    private java.lang.Double _itemHeight ;
    
    /**
     *  
     * xml name: itemWidth
     * 
     */
    private java.lang.Double _itemWidth ;
    
    /**
     *  
     * xml name: selectable
     * 
     */
    private java.lang.Boolean _selectable  = true;
    
    /**
     *  
     * xml name: spacing
     * 
     */
    private java.lang.Double _spacing ;
    
    /**
     * 
     * xml name: itemHeight
     *  
     */
    
    public java.lang.Double getItemHeight(){
      return _itemHeight;
    }

    
    public void setItemHeight(java.lang.Double value){
        checkAllowChange();
        
        this._itemHeight = value;
           
    }

    
    /**
     * 
     * xml name: itemWidth
     *  
     */
    
    public java.lang.Double getItemWidth(){
      return _itemWidth;
    }

    
    public void setItemWidth(java.lang.Double value){
        checkAllowChange();
        
        this._itemWidth = value;
           
    }

    
    /**
     * 
     * xml name: selectable
     *  
     */
    
    public java.lang.Boolean getSelectable(){
      return _selectable;
    }

    
    public void setSelectable(java.lang.Boolean value){
        checkAllowChange();
        
        this._selectable = value;
           
    }

    
    /**
     * 
     * xml name: spacing
     *  
     */
    
    public java.lang.Double getSpacing(){
      return _spacing;
    }

    
    public void setSpacing(java.lang.Double value){
        checkAllowChange();
        
        this._spacing = value;
           
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
        
        out.putNotNull("itemHeight",this.getItemHeight());
        out.putNotNull("itemWidth",this.getItemWidth());
        out.putNotNull("selectable",this.getSelectable());
        out.putNotNull("spacing",this.getSpacing());
    }

    public ChartLegendItemsModel cloneInstance(){
        ChartLegendItemsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartLegendItemsModel instance){
        super.copyTo(instance);
        
        instance.setItemHeight(this.getItemHeight());
        instance.setItemWidth(this.getItemWidth());
        instance.setSelectable(this.getSelectable());
        instance.setSpacing(this.getSpacing());
    }

    protected ChartLegendItemsModel newInstance(){
        return (ChartLegendItemsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
