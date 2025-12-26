package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 控制plotArea的位置和大小。缺省情况下title和legend自动布局。
 * x/y/w/h → 左/上/宽/高，数值为百分比。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartManualLayoutModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: percentH
     * 
     */
    private java.lang.Double _percentH ;
    
    /**
     *  
     * xml name: percentW
     * 
     */
    private java.lang.Double _percentW ;
    
    /**
     *  
     * xml name: percentX
     * 
     */
    private java.lang.Double _percentX ;
    
    /**
     *  
     * xml name: percentY
     * 
     */
    private java.lang.Double _percentY ;
    
    /**
     * 
     * xml name: percentH
     *  
     */
    
    public java.lang.Double getPercentH(){
      return _percentH;
    }

    
    public void setPercentH(java.lang.Double value){
        checkAllowChange();
        
        this._percentH = value;
           
    }

    
    /**
     * 
     * xml name: percentW
     *  
     */
    
    public java.lang.Double getPercentW(){
      return _percentW;
    }

    
    public void setPercentW(java.lang.Double value){
        checkAllowChange();
        
        this._percentW = value;
           
    }

    
    /**
     * 
     * xml name: percentX
     *  
     */
    
    public java.lang.Double getPercentX(){
      return _percentX;
    }

    
    public void setPercentX(java.lang.Double value){
        checkAllowChange();
        
        this._percentX = value;
           
    }

    
    /**
     * 
     * xml name: percentY
     *  
     */
    
    public java.lang.Double getPercentY(){
      return _percentY;
    }

    
    public void setPercentY(java.lang.Double value){
        checkAllowChange();
        
        this._percentY = value;
           
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
        
        out.putNotNull("percentH",this.getPercentH());
        out.putNotNull("percentW",this.getPercentW());
        out.putNotNull("percentX",this.getPercentX());
        out.putNotNull("percentY",this.getPercentY());
    }

    public ChartManualLayoutModel cloneInstance(){
        ChartManualLayoutModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartManualLayoutModel instance){
        super.copyTo(instance);
        
        instance.setPercentH(this.getPercentH());
        instance.setPercentW(this.getPercentW());
        instance.setPercentX(this.getPercentX());
        instance.setPercentY(this.getPercentY());
    }

    protected ChartManualLayoutModel newInstance(){
        return (ChartManualLayoutModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
