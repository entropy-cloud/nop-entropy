package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartBackgroundModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Chart background styling
 * 对应 Excel POI 中的 ChartSpace 背景设置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartBackgroundModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: image
     * 
     */
    private java.lang.String _image ;
    
    /**
     *  
     * xml name: pattern
     * 
     */
    private java.lang.String _pattern ;
    
    /**
     * 
     * xml name: color
     *  
     */
    
    public java.lang.String getColor(){
      return _color;
    }

    
    public void setColor(java.lang.String value){
        checkAllowChange();
        
        this._color = value;
           
    }

    
    /**
     * 
     * xml name: image
     *  
     */
    
    public java.lang.String getImage(){
      return _image;
    }

    
    public void setImage(java.lang.String value){
        checkAllowChange();
        
        this._image = value;
           
    }

    
    /**
     * 
     * xml name: pattern
     *  
     */
    
    public java.lang.String getPattern(){
      return _pattern;
    }

    
    public void setPattern(java.lang.String value){
        checkAllowChange();
        
        this._pattern = value;
           
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
        
        out.putNotNull("color",this.getColor());
        out.putNotNull("image",this.getImage());
        out.putNotNull("pattern",this.getPattern());
    }

    public ChartBackgroundModel cloneInstance(){
        ChartBackgroundModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartBackgroundModel instance){
        super.copyTo(instance);
        
        instance.setColor(this.getColor());
        instance.setImage(this.getImage());
        instance.setPattern(this.getPattern());
    }

    protected ChartBackgroundModel newInstance(){
        return (ChartBackgroundModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
