package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartBubbleConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Bubble chart specific settings
 * 对应 Excel POI 中 BubbleChart 的特殊属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartBubbleConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bubble3D
     * 是否3D气泡：true/false
     */
    private java.lang.Boolean _bubble3D ;
    
    /**
     *  
     * xml name: bubbleScale
     * 气泡缩放比例：默认100%
     */
    private java.lang.Double _bubbleScale ;
    
    /**
     *  
     * xml name: sizeRepresents
     * 气泡大小表示：area/width
     */
    private java.lang.String _sizeRepresents ;
    
    /**
     * 
     * xml name: bubble3D
     *  是否3D气泡：true/false
     */
    
    public java.lang.Boolean getBubble3D(){
      return _bubble3D;
    }

    
    public void setBubble3D(java.lang.Boolean value){
        checkAllowChange();
        
        this._bubble3D = value;
           
    }

    
    /**
     * 
     * xml name: bubbleScale
     *  气泡缩放比例：默认100%
     */
    
    public java.lang.Double getBubbleScale(){
      return _bubbleScale;
    }

    
    public void setBubbleScale(java.lang.Double value){
        checkAllowChange();
        
        this._bubbleScale = value;
           
    }

    
    /**
     * 
     * xml name: sizeRepresents
     *  气泡大小表示：area/width
     */
    
    public java.lang.String getSizeRepresents(){
      return _sizeRepresents;
    }

    
    public void setSizeRepresents(java.lang.String value){
        checkAllowChange();
        
        this._sizeRepresents = value;
           
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
        
        out.putNotNull("bubble3D",this.getBubble3D());
        out.putNotNull("bubbleScale",this.getBubbleScale());
        out.putNotNull("sizeRepresents",this.getSizeRepresents());
    }

    public ChartBubbleConfigModel cloneInstance(){
        ChartBubbleConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartBubbleConfigModel instance){
        super.copyTo(instance);
        
        instance.setBubble3D(this.getBubble3D());
        instance.setBubbleScale(this.getBubbleScale());
        instance.setSizeRepresents(this.getSizeRepresents());
    }

    protected ChartBubbleConfigModel newInstance(){
        return (ChartBubbleConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
