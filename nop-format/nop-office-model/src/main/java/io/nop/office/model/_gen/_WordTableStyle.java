package io.nop.office.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.model.WordTableStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table-style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: align
     * 
     */
    private io.nop.office.model.constants.OfficeHorizontalAlignment _align ;
    
    /**
     *  
     * xml name: cellStyle
     * 
     */
    private io.nop.office.model.WordCellStyle _cellStyle ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Double _width ;
    
    /**
     * 
     * xml name: align
     *  
     */
    
    public io.nop.office.model.constants.OfficeHorizontalAlignment getAlign(){
      return _align;
    }

    
    public void setAlign(io.nop.office.model.constants.OfficeHorizontalAlignment value){
        checkAllowChange();
        
        this._align = value;
           
    }

    
    /**
     * 
     * xml name: cellStyle
     *  
     */
    
    public io.nop.office.model.WordCellStyle getCellStyle(){
      return _cellStyle;
    }

    
    public void setCellStyle(io.nop.office.model.WordCellStyle value){
        checkAllowChange();
        
        this._cellStyle = value;
           
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
     * xml name: width
     *  
     */
    
    public java.lang.Double getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Double value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cellStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cellStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("align",this.getAlign());
        out.putNotNull("cellStyle",this.getCellStyle());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
        out.putNotNull("width",this.getWidth());
    }

    public WordTableStyle cloneInstance(){
        WordTableStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableStyle instance){
        super.copyTo(instance);
        
        instance.setAlign(this.getAlign());
        instance.setCellStyle(this.getCellStyle());
        instance.setId(this.getId());
        instance.setName(this.getName());
        instance.setWidth(this.getWidth());
    }

    protected WordTableStyle newInstance(){
        return (WordTableStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
