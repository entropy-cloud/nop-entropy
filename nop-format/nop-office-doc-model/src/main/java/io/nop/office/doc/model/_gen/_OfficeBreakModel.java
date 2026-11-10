package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeBreakModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: brType
     * 
     */
    private java.lang.String _brType ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: brType
     *  
     */
    
    public java.lang.String getBrType(){
      return _brType;
    }

    
    public void setBrType(java.lang.String value){
        checkAllowChange();
        
        this._brType = value;
           
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
     * xml name: 
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
        
        out.putNotNull("brType",this.getBrType());
        out.putNotNull("id",this.getId());
        out.putNotNull("type",this.getType());
    }

    public OfficeBreakModel cloneInstance(){
        OfficeBreakModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeBreakModel instance){
        super.copyTo(instance);
        
        instance.setBrType(this.getBrType());
        instance.setId(this.getId());
        instance.setType(this.getType());
    }

    protected OfficeBreakModel newInstance(){
        return (OfficeBreakModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
