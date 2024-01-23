package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.XptXplModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [257:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XptXplModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _body ;
    
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
    
    public io.nop.core.lang.eval.IEvalAction getBody(){
      return _body;
    }

    
    public void setBody(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._body = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("id",this.getId());
    }

    public XptXplModel cloneInstance(){
        XptXplModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XptXplModel instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setId(this.getId());
    }

    protected XptXplModel newInstance(){
        return (XptXplModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
