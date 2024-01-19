package io.nop.rpc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rpc.model.ApiImportModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [7:10:0:0]/nop/schema/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiImportModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: as
     * 
     */
    private java.lang.String _as ;
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: public
     * 
     */
    private java.lang.Boolean _public ;
    
    /**
     * 
     * xml name: as
     *  
     */
    
    public java.lang.String getAs(){
      return _as;
    }

    
    public void setAs(java.lang.String value){
        checkAllowChange();
        
        this._as = value;
           
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
     * xml name: public
     *  
     */
    
    public java.lang.Boolean getPublic(){
      return _public;
    }

    
    public void setPublic(java.lang.Boolean value){
        checkAllowChange();
        
        this._public = value;
           
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
        
        out.put("as",this.getAs());
        out.put("from",this.getFrom());
        out.put("public",this.getPublic());
    }

    public ApiImportModel cloneInstance(){
        ApiImportModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiImportModel instance){
        super.copyTo(instance);
        
        instance.setAs(this.getAs());
        instance.setFrom(this.getFrom());
        instance.setPublic(this.getPublic());
    }

    protected ApiImportModel newInstance(){
        return (ApiImportModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
