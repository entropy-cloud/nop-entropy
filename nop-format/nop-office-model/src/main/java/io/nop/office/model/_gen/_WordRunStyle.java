package io.nop.office.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.model.WordRunStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-run-style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordRunStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: font
     * 
     */
    private io.nop.office.model.OfficeFont _font ;
    
    /**
     *  
     * xml name: highlightColor
     * 
     */
    private java.lang.String _highlightColor ;
    
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
     * xml name: highlightColor
     *  
     */
    
    public java.lang.String getHighlightColor(){
      return _highlightColor;
    }

    
    public void setHighlightColor(java.lang.String value){
        checkAllowChange();
        
        this._highlightColor = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("font",this.getFont());
        out.putNotNull("highlightColor",this.getHighlightColor());
        out.putNotNull("id",this.getId());
        out.putNotNull("name",this.getName());
    }

    public WordRunStyle cloneInstance(){
        WordRunStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordRunStyle instance){
        super.copyTo(instance);
        
        instance.setFont(this.getFont());
        instance.setHighlightColor(this.getHighlightColor());
        instance.setId(this.getId());
        instance.setName(this.getName());
    }

    protected WordRunStyle newInstance(){
        return (WordRunStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
