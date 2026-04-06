package io.nop.office.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.model.WordParagraphStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-paragraph-style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordParagraphStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: align
     * 
     */
    private io.nop.office.model.constants.OfficeHorizontalAlignment _align ;
    
    /**
     *  
     * xml name: firstLineIndent
     * 
     */
    private java.lang.Double _firstLineIndent ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: leftIndent
     * 
     */
    private java.lang.Double _leftIndent ;
    
    /**
     *  
     * xml name: lineSpacing
     * 
     */
    private java.lang.Double _lineSpacing ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: rightIndent
     * 
     */
    private java.lang.Double _rightIndent ;
    
    /**
     *  
     * xml name: spaceAfter
     * 
     */
    private java.lang.Double _spaceAfter ;
    
    /**
     *  
     * xml name: spaceBefore
     * 
     */
    private java.lang.Double _spaceBefore ;
    
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
     * xml name: firstLineIndent
     *  
     */
    
    public java.lang.Double getFirstLineIndent(){
      return _firstLineIndent;
    }

    
    public void setFirstLineIndent(java.lang.Double value){
        checkAllowChange();
        
        this._firstLineIndent = value;
           
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
     * xml name: leftIndent
     *  
     */
    
    public java.lang.Double getLeftIndent(){
      return _leftIndent;
    }

    
    public void setLeftIndent(java.lang.Double value){
        checkAllowChange();
        
        this._leftIndent = value;
           
    }

    
    /**
     * 
     * xml name: lineSpacing
     *  
     */
    
    public java.lang.Double getLineSpacing(){
      return _lineSpacing;
    }

    
    public void setLineSpacing(java.lang.Double value){
        checkAllowChange();
        
        this._lineSpacing = value;
           
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
     * xml name: rightIndent
     *  
     */
    
    public java.lang.Double getRightIndent(){
      return _rightIndent;
    }

    
    public void setRightIndent(java.lang.Double value){
        checkAllowChange();
        
        this._rightIndent = value;
           
    }

    
    /**
     * 
     * xml name: spaceAfter
     *  
     */
    
    public java.lang.Double getSpaceAfter(){
      return _spaceAfter;
    }

    
    public void setSpaceAfter(java.lang.Double value){
        checkAllowChange();
        
        this._spaceAfter = value;
           
    }

    
    /**
     * 
     * xml name: spaceBefore
     *  
     */
    
    public java.lang.Double getSpaceBefore(){
      return _spaceBefore;
    }

    
    public void setSpaceBefore(java.lang.Double value){
        checkAllowChange();
        
        this._spaceBefore = value;
           
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
        
        out.putNotNull("align",this.getAlign());
        out.putNotNull("firstLineIndent",this.getFirstLineIndent());
        out.putNotNull("id",this.getId());
        out.putNotNull("leftIndent",this.getLeftIndent());
        out.putNotNull("lineSpacing",this.getLineSpacing());
        out.putNotNull("name",this.getName());
        out.putNotNull("rightIndent",this.getRightIndent());
        out.putNotNull("spaceAfter",this.getSpaceAfter());
        out.putNotNull("spaceBefore",this.getSpaceBefore());
    }

    public WordParagraphStyle cloneInstance(){
        WordParagraphStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordParagraphStyle instance){
        super.copyTo(instance);
        
        instance.setAlign(this.getAlign());
        instance.setFirstLineIndent(this.getFirstLineIndent());
        instance.setId(this.getId());
        instance.setLeftIndent(this.getLeftIndent());
        instance.setLineSpacing(this.getLineSpacing());
        instance.setName(this.getName());
        instance.setRightIndent(this.getRightIndent());
        instance.setSpaceAfter(this.getSpaceAfter());
        instance.setSpaceBefore(this.getSpaceBefore());
    }

    protected WordParagraphStyle newInstance(){
        return (WordParagraphStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
