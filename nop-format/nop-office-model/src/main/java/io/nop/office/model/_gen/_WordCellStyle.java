package io.nop.office.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.model.WordCellStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-cell-style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordCellStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: backgroundColor
     * 
     */
    private java.lang.String _backgroundColor ;
    
    /**
     *  
     * xml name: bottomBorder
     * 
     */
    private io.nop.office.model.WordBorderStyle _bottomBorder ;
    
    /**
     *  
     * xml name: font
     * 
     */
    private io.nop.office.model.OfficeFont _font ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: leftBorder
     * 
     */
    private io.nop.office.model.WordBorderStyle _leftBorder ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: rightBorder
     * 
     */
    private io.nop.office.model.WordBorderStyle _rightBorder ;
    
    /**
     *  
     * xml name: topBorder
     * 
     */
    private io.nop.office.model.WordBorderStyle _topBorder ;
    
    /**
     *  
     * xml name: verticalAlign
     * 
     */
    private io.nop.office.model.constants.OfficeVerticalAlignment _verticalAlign ;
    
    /**
     * 
     * xml name: backgroundColor
     *  
     */
    
    public java.lang.String getBackgroundColor(){
      return _backgroundColor;
    }

    
    public void setBackgroundColor(java.lang.String value){
        checkAllowChange();
        
        this._backgroundColor = value;
           
    }

    
    /**
     * 
     * xml name: bottomBorder
     *  
     */
    
    public io.nop.office.model.WordBorderStyle getBottomBorder(){
      return _bottomBorder;
    }

    
    public void setBottomBorder(io.nop.office.model.WordBorderStyle value){
        checkAllowChange();
        
        this._bottomBorder = value;
           
    }

    
    /**
     * 
     * xml name: font
     *  
     */
    
    public io.nop.office.model.OfficeFont getFont(){
      return _font;
    }

    
    public void setFont(io.nop.office.model.OfficeFont value){
        checkAllowChange();
        
        this._font = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: leftBorder
     *  
     */
    
    public io.nop.office.model.WordBorderStyle getLeftBorder(){
      return _leftBorder;
    }

    
    public void setLeftBorder(io.nop.office.model.WordBorderStyle value){
        checkAllowChange();
        
        this._leftBorder = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: rightBorder
     *  
     */
    
    public io.nop.office.model.WordBorderStyle getRightBorder(){
      return _rightBorder;
    }

    
    public void setRightBorder(io.nop.office.model.WordBorderStyle value){
        checkAllowChange();
        
        this._rightBorder = value;
           
    }

    
    /**
     * 
     * xml name: topBorder
     *  
     */
    
    public io.nop.office.model.WordBorderStyle getTopBorder(){
      return _topBorder;
    }

    
    public void setTopBorder(io.nop.office.model.WordBorderStyle value){
        checkAllowChange();
        
        this._topBorder = value;
           
    }

    
    /**
     * 
     * xml name: verticalAlign
     *  
     */
    
    public io.nop.office.model.constants.OfficeVerticalAlignment getVerticalAlign(){
      return _verticalAlign;
    }

    
    public void setVerticalAlign(io.nop.office.model.constants.OfficeVerticalAlignment value){
        checkAllowChange();
        
        this._verticalAlign = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._bottomBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._bottomBorder);
            
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
           this._leftBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._leftBorder);
            
           this._rightBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rightBorder);
            
           this._topBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._topBorder);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("backgroundColor",this.getBackgroundColor());
        out.putNotNull("bottomBorder",this.getBottomBorder());
        out.putNotNull("font",this.getFont());
        out.putNotNull("id",this.getId());
        out.putNotNull("leftBorder",this.getLeftBorder());
        out.putNotNull("name",this.getName());
        out.putNotNull("rightBorder",this.getRightBorder());
        out.putNotNull("topBorder",this.getTopBorder());
        out.putNotNull("verticalAlign",this.getVerticalAlign());
    }

    public WordCellStyle cloneInstance(){
        WordCellStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordCellStyle instance){
        super.copyTo(instance);
        
        instance.setBackgroundColor(this.getBackgroundColor());
        instance.setBottomBorder(this.getBottomBorder());
        instance.setFont(this.getFont());
        instance.setId(this.getId());
        instance.setLeftBorder(this.getLeftBorder());
        instance.setName(this.getName());
        instance.setRightBorder(this.getRightBorder());
        instance.setTopBorder(this.getTopBorder());
        instance.setVerticalAlign(this.getVerticalAlign());
    }

    protected WordCellStyle newInstance(){
        return (WordCellStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
