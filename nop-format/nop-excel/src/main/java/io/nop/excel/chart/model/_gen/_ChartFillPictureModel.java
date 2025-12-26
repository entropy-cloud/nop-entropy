package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartFillPictureModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartFillPictureModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: data
     * 
     */
    private io.nop.commons.bytes.ByteString _data ;
    
    /**
     *  
     * xml name: stretch
     * 
     */
    private java.lang.Boolean _stretch ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.Boolean _title ;
    
    /**
     * 
     * xml name: data
     *  
     */
    
    public io.nop.commons.bytes.ByteString getData(){
      return _data;
    }

    
    public void setData(io.nop.commons.bytes.ByteString value){
        checkAllowChange();
        
        this._data = value;
           
    }

    
    /**
     * 
     * xml name: stretch
     *  
     */
    
    public java.lang.Boolean getStretch(){
      return _stretch;
    }

    
    public void setStretch(java.lang.Boolean value){
        checkAllowChange();
        
        this._stretch = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  
     */
    
    public java.lang.Boolean getTitle(){
      return _title;
    }

    
    public void setTitle(java.lang.Boolean value){
        checkAllowChange();
        
        this._title = value;
           
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
        out.putNotNull("stretch",this.getStretch());
        out.putNotNull("title",this.getTitle());
    }

    public ChartFillPictureModel cloneInstance(){
        ChartFillPictureModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartFillPictureModel instance){
        super.copyTo(instance);
        
        instance.setData(this.getData());
        instance.setStretch(this.getStretch());
        instance.setTitle(this.getTitle());
    }

    protected ChartFillPictureModel newInstance(){
        return (ChartFillPictureModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
