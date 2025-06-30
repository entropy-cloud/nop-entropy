package io.nop.converter.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.converter.config.ConvertConverterConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/convert.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ConvertConverterConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: class
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: optional
     * 
     */
    private boolean _optional  = false;
    
    /**
     *  
     * xml name: to
     * 
     */
    private java.lang.String _to ;
    
    /**
     * 
     * xml name: class
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: from
     *  
     */
    
    public java.lang.String getFrom(){
      return _from;
    }

    
    public void setFrom(java.lang.String value){
        checkAllowChange();
        
        this._from = value;
           
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
     * xml name: optional
     *  
     */
    
    public boolean isOptional(){
      return _optional;
    }

    
    public void setOptional(boolean value){
        checkAllowChange();
        
        this._optional = value;
           
    }

    
    /**
     * 
     * xml name: to
     *  
     */
    
    public java.lang.String getTo(){
      return _to;
    }

    
    public void setTo(java.lang.String value){
        checkAllowChange();
        
        this._to = value;
           
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
        
        out.putNotNull("className",this.getClassName());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("id",this.getId());
        out.putNotNull("optional",this.isOptional());
        out.putNotNull("to",this.getTo());
    }

    public ConvertConverterConfig cloneInstance(){
        ConvertConverterConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ConvertConverterConfig instance){
        super.copyTo(instance);
        
        instance.setClassName(this.getClassName());
        instance.setFrom(this.getFrom());
        instance.setId(this.getId());
        instance.setOptional(this.isOptional());
        instance.setTo(this.getTo());
    }

    protected ConvertConverterConfig newInstance(){
        return (ConvertConverterConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
