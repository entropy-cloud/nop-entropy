package io.nop.converter.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.converter.config.ConvertBuilderConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/convert.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ConvertBuilderConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: class
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: fileType
     * 
     */
    private java.lang.String _fileType ;
    
    /**
     *  
     * xml name: optional
     * 
     */
    private boolean _optional  = false;
    
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
     * xml name: fileType
     *  
     */
    
    public java.lang.String getFileType(){
      return _fileType;
    }

    
    public void setFileType(java.lang.String value){
        checkAllowChange();
        
        this._fileType = value;
           
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
        out.putNotNull("fileType",this.getFileType());
        out.putNotNull("optional",this.isOptional());
    }

    public ConvertBuilderConfig cloneInstance(){
        ConvertBuilderConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ConvertBuilderConfig instance){
        super.copyTo(instance);
        
        instance.setClassName(this.getClassName());
        instance.setFileType(this.getFileType());
        instance.setOptional(this.isOptional());
    }

    protected ConvertBuilderConfig newInstance(){
        return (ConvertBuilderConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
