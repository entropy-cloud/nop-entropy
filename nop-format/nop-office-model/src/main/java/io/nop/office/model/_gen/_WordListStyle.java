package io.nop.office.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.model.WordListStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-list-style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordListStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: basedOn
     * 
     */
    private java.lang.String _basedOn ;
    
    /**
     *  
     * xml name: default
     * 
     */
    private boolean _default  = false;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: indent
     * 
     */
    private java.lang.Double _indent ;
    
    /**
     *  
     * xml name: level
     * 
     */
    private int _level  = 0;
    
    /**
     *  
     * xml name: lvlText
     * 
     */
    private java.lang.String _lvlText ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: numFmt
     * 
     */
    private java.lang.String _numFmt ;
    
    /**
     *  
     * xml name: start
     * 
     */
    private int _start  = 1;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: basedOn
     *  
     */
    
    public java.lang.String getBasedOn(){
      return _basedOn;
    }

    
    public void setBasedOn(java.lang.String value){
        checkAllowChange();
        
        this._basedOn = value;
           
    }

    
    /**
     * 
     * xml name: default
     *  
     */
    
    public boolean isDefault(){
      return _default;
    }

    
    public void setDefault(boolean value){
        checkAllowChange();
        
        this._default = value;
           
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
     * xml name: indent
     *  
     */
    
    public java.lang.Double getIndent(){
      return _indent;
    }

    
    public void setIndent(java.lang.Double value){
        checkAllowChange();
        
        this._indent = value;
           
    }

    
    /**
     * 
     * xml name: level
     *  
     */
    
    public int getLevel(){
      return _level;
    }

    
    public void setLevel(int value){
        checkAllowChange();
        
        this._level = value;
           
    }

    
    /**
     * 
     * xml name: lvlText
     *  
     */
    
    public java.lang.String getLvlText(){
      return _lvlText;
    }

    
    public void setLvlText(java.lang.String value){
        checkAllowChange();
        
        this._lvlText = value;
           
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
     * xml name: numFmt
     *  
     */
    
    public java.lang.String getNumFmt(){
      return _numFmt;
    }

    
    public void setNumFmt(java.lang.String value){
        checkAllowChange();
        
        this._numFmt = value;
           
    }

    
    /**
     * 
     * xml name: start
     *  
     */
    
    public int getStart(){
      return _start;
    }

    
    public void setStart(int value){
        checkAllowChange();
        
        this._start = value;
           
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
        
        out.putNotNull("basedOn",this.getBasedOn());
        out.putNotNull("default",this.isDefault());
        out.putNotNull("id",this.getId());
        out.putNotNull("indent",this.getIndent());
        out.putNotNull("level",this.getLevel());
        out.putNotNull("lvlText",this.getLvlText());
        out.putNotNull("name",this.getName());
        out.putNotNull("numFmt",this.getNumFmt());
        out.putNotNull("start",this.getStart());
        out.putNotNull("type",this.getType());
    }

    public WordListStyle cloneInstance(){
        WordListStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordListStyle instance){
        super.copyTo(instance);
        
        instance.setBasedOn(this.getBasedOn());
        instance.setDefault(this.isDefault());
        instance.setId(this.getId());
        instance.setIndent(this.getIndent());
        instance.setLevel(this.getLevel());
        instance.setLvlText(this.getLvlText());
        instance.setName(this.getName());
        instance.setNumFmt(this.getNumFmt());
        instance.setStart(this.getStart());
        instance.setType(this.getType());
    }

    protected WordListStyle newInstance(){
        return (WordListStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
