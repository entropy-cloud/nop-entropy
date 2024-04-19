package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchFileReaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [43:10:0:0]/nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchFileReaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: headers
     * 
     */
    private java.util.Set<java.lang.String> _headers ;
    
    /**
     *  
     * xml name: pathExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _pathExpr ;
    
    /**
     * 
     * xml name: headers
     *  
     */
    
    public java.util.Set<java.lang.String> getHeaders(){
      return _headers;
    }

    
    public void setHeaders(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._headers = value;
           
    }

    
    /**
     * 
     * xml name: pathExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getPathExpr(){
      return _pathExpr;
    }

    
    public void setPathExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._pathExpr = value;
           
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
        
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("pathExpr",this.getPathExpr());
    }

    public BatchFileReaderModel cloneInstance(){
        BatchFileReaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchFileReaderModel instance){
        super.copyTo(instance);
        
        instance.setHeaders(this.getHeaders());
        instance.setPathExpr(this.getPathExpr());
    }

    protected BatchFileReaderModel newInstance(){
        return (BatchFileReaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
