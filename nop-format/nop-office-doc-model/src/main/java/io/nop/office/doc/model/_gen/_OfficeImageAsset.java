package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeImageAsset extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: data
     * 
     */
    private io.nop.commons.bytes.ByteString _data ;
    
    /**
     *  
     * xml name: ext
     * 
     */
    private java.lang.String _ext ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: data
     *  
     */
    
    public io.nop.commons.bytes.ByteString getData(){
      return _data;
    }

    
    public void setData(io.nop.commons.bytes.ByteString value){
        checkAllowChange();
        
        this._data = value;
           
    }

    
    /**
     * 
     * xml name: ext
     *  
     */
    
    public java.lang.String getExt(){
      return _ext;
    }

    
    public void setExt(java.lang.String value){
        checkAllowChange();
        
        this._ext = value;
           
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("data",this.getData());
        out.putNotNull("ext",this.getExt());
        out.putNotNull("name",this.getName());
    }

    public OfficeImageAsset cloneInstance(){
        OfficeImageAsset instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeImageAsset instance){
        super.copyTo(instance);
        
        instance.setData(this.getData());
        instance.setExt(this.getExt());
        instance.setName(this.getName());
    }

    protected OfficeImageAsset newInstance(){
        return (OfficeImageAsset) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
