package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartZoomModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Zoom and pan capabilities
 * 主要用于 Web 图表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartZoomModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
    /**
     *  
     * xml name: panZoom
     * 
     */
    private java.lang.Boolean _panZoom  = true;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: wheelZoom
     * 
     */
    private java.lang.Boolean _wheelZoom  = true;
    
    /**
     * 
     * xml name: enabled
     *  
     */
    
    public java.lang.Boolean getEnabled(){
      return _enabled;
    }

    
    public void setEnabled(java.lang.Boolean value){
        checkAllowChange();
        
        this._enabled = value;
           
    }

    
    /**
     * 
     * xml name: panZoom
     *  
     */
    
    public java.lang.Boolean getPanZoom(){
      return _panZoom;
    }

    
    public void setPanZoom(java.lang.Boolean value){
        checkAllowChange();
        
        this._panZoom = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: wheelZoom
     *  
     */
    
    public java.lang.Boolean getWheelZoom(){
      return _wheelZoom;
    }

    
    public void setWheelZoom(java.lang.Boolean value){
        checkAllowChange();
        
        this._wheelZoom = value;
           
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
        
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("panZoom",this.getPanZoom());
        out.putNotNull("type",this.getType());
        out.putNotNull("wheelZoom",this.getWheelZoom());
    }

    public ChartZoomModel cloneInstance(){
        ChartZoomModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartZoomModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setPanZoom(this.getPanZoom());
        instance.setType(this.getType());
        instance.setWheelZoom(this.getWheelZoom());
    }

    protected ChartZoomModel newInstance(){
        return (ChartZoomModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
