package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [207:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _ExcelPageMargins extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bottom
     * 
     */
    private java.lang.Double _bottom ;
    
    /**
     *  
     * xml name: footer
     * 
     */
    private java.lang.Double _footer ;
    
    /**
     *  
     * xml name: header
     * 
     */
    private java.lang.Double _header ;
    
    /**
     *  
     * xml name: left
     * 
     */
    private java.lang.Double _left ;
    
    /**
     *  
     * xml name: right
     * 
     */
    private java.lang.Double _right ;
    
    /**
     *  
     * xml name: top
     * 
     */
    private java.lang.Double _top ;
    
    /**
     * 
     * xml name: bottom
     *  
     */
    
    public java.lang.Double getBottom(){
      return _bottom;
    }

    
    public void setBottom(java.lang.Double value){
        checkAllowChange();
        
        this._bottom = value;
           
    }

    
    /**
     * 
     * xml name: footer
     *  
     */
    
    public java.lang.Double getFooter(){
      return _footer;
    }

    
    public void setFooter(java.lang.Double value){
        checkAllowChange();
        
        this._footer = value;
           
    }

    
    /**
     * 
     * xml name: header
     *  
     */
    
    public java.lang.Double getHeader(){
      return _header;
    }

    
    public void setHeader(java.lang.Double value){
        checkAllowChange();
        
        this._header = value;
           
    }

    
    /**
     * 
     * xml name: left
     *  
     */
    
    public java.lang.Double getLeft(){
      return _left;
    }

    
    public void setLeft(java.lang.Double value){
        checkAllowChange();
        
        this._left = value;
           
    }

    
    /**
     * 
     * xml name: right
     *  
     */
    
    public java.lang.Double getRight(){
      return _right;
    }

    
    public void setRight(java.lang.Double value){
        checkAllowChange();
        
        this._right = value;
           
    }

    
    /**
     * 
     * xml name: top
     *  
     */
    
    public java.lang.Double getTop(){
      return _top;
    }

    
    public void setTop(java.lang.Double value){
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
        
        out.put("bottom",this.getBottom());
        out.put("footer",this.getFooter());
        out.put("header",this.getHeader());
        out.put("left",this.getLeft());
        out.put("right",this.getRight());
        out.put("top",this.getTop());
    }
}
 // resume CPD analysis - CPD-ON
