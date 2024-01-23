package io.nop.xlang.xpl.xlib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xpl.xlib.XplTagSlotArg;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [87:18:0:0]/nop/schema/xlib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XplTagSlotArg extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.Object _defaultValue ;
    
    /**
     *  
     * xml name: deprecated
     * 
     */
    private boolean _deprecated  = false;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: implicit
     * 
     */
    private boolean _implicit  = false;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     * 
     * xml name: defaultValue
     *  
     */
    
    public java.lang.Object getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.Object value){
        checkAllowChange();
        
        this._defaultValue = value;
           
    }

    
    /**
     * 
     * xml name: deprecated
     *  
     */
    
    public boolean isDeprecated(){
      return _deprecated;
    }

    
    public void setDeprecated(boolean value){
        checkAllowChange();
        
        this._deprecated = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: implicit
     *  
     */
    
    public boolean isImplicit(){
      return _implicit;
    }

    
    public void setImplicit(boolean value){
        checkAllowChange();
        
        this._implicit = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
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
     * xml name: stdDomain
     *  
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
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
        
        out.putNotNull("defaultValue",this.getDefaultValue());
        out.putNotNull("deprecated",this.isDeprecated());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("implicit",this.isImplicit());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("name",this.getName());
        out.putNotNull("stdDomain",this.getStdDomain());
        out.putNotNull("type",this.getType());
    }

    public XplTagSlotArg cloneInstance(){
        XplTagSlotArg instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XplTagSlotArg instance){
        super.copyTo(instance);
        
        instance.setDefaultValue(this.getDefaultValue());
        instance.setDeprecated(this.isDeprecated());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setImplicit(this.isImplicit());
        instance.setMandatory(this.isMandatory());
        instance.setName(this.getName());
        instance.setStdDomain(this.getStdDomain());
        instance.setType(this.getType());
    }

    protected XplTagSlotArg newInstance(){
        return (XplTagSlotArg) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
