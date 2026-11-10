package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficePageMargins extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bottom
     * 
     */
    private double _bottom  = 0;
    
    /**
     *  
     * xml name: footer
     * 
     */
    private double _footer  = 0;
    
    /**
     *  
     * xml name: gutter
     * 
     */
    private double _gutter  = 0;
    
    /**
     *  
     * xml name: header
     * 
     */
    private double _header  = 0;
    
    /**
     *  
     * xml name: left
     * 
     */
    private double _left  = 0;
    
    /**
     *  
     * xml name: right
     * 
     */
    private double _right  = 0;
    
    /**
     *  
     * xml name: top
     * 
     */
    private double _top  = 0;
    
    /**
     * 
     * xml name: bottom
     *  
     */
    
    public double getBottom(){
      return _bottom;
    }

    
    public void setBottom(double value){
        checkAllowChange();
        
        this._bottom = value;
           
    }

    
    /**
     * 
     * xml name: footer
     *  
     */
    
    public double getFooter(){
      return _footer;
    }

    
    public void setFooter(double value){
        checkAllowChange();
        
        this._footer = value;
           
    }

    
    /**
     * 
     * xml name: gutter
     *  
     */
    
    public double getGutter(){
      return _gutter;
    }

    
    public void setGutter(double value){
        checkAllowChange();
        
        this._gutter = value;
           
    }

    
    /**
     * 
     * xml name: header
     *  
     */
    
    public double getHeader(){
      return _header;
    }

    
    public void setHeader(double value){
        checkAllowChange();
        
        this._header = value;
           
    }

    
    /**
     * 
     * xml name: left
     *  
     */
    
    public double getLeft(){
      return _left;
    }

    
    public void setLeft(double value){
        checkAllowChange();
        
        this._left = value;
           
    }

    
    /**
     * 
     * xml name: right
     *  
     */
    
    public double getRight(){
      return _right;
    }

    
    public void setRight(double value){
        checkAllowChange();
        
        this._right = value;
           
    }

    
    /**
     * 
     * xml name: top
     *  
     */
    
    public double getTop(){
      return _top;
    }

    
    public void setTop(double value){
        checkAllowChange();
        
        this._top = value;
           
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
        
        out.putNotNull("bottom",this.getBottom());
        out.putNotNull("footer",this.getFooter());
        out.putNotNull("gutter",this.getGutter());
        out.putNotNull("header",this.getHeader());
        out.putNotNull("left",this.getLeft());
        out.putNotNull("right",this.getRight());
        out.putNotNull("top",this.getTop());
    }

    public OfficePageMargins cloneInstance(){
        OfficePageMargins instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficePageMargins instance){
        super.copyTo(instance);
        
        instance.setBottom(this.getBottom());
        instance.setFooter(this.getFooter());
        instance.setGutter(this.getGutter());
        instance.setHeader(this.getHeader());
        instance.setLeft(this.getLeft());
        instance.setRight(this.getRight());
        instance.setTop(this.getTop());
    }

    protected OfficePageMargins newInstance(){
        return (OfficePageMargins) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
