package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchFileWriterModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchFileWriterModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: encoding
     * 
     */
    private java.lang.String _encoding ;
    
    /**
     *  
     * xml name: filePath
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _filePath ;
    
    /**
     *  
     * xml name: headers
     * 
     */
    private java.util.Set<java.lang.String> _headers ;
    
    /**
     *  
     * xml name: recordOutputProvider
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _recordOutputProvider ;
    
    /**
     *  
     * xml name: resourceIO
     * 
     */
    private java.lang.String _resourceIO ;
    
    /**
     *  
     * xml name: resourceLoader
     * 
     */
    private java.lang.String _resourceLoader ;
    
    /**
     * 
     * xml name: encoding
     *  
     */
    
    public java.lang.String getEncoding(){
      return _encoding;
    }

    
    public void setEncoding(java.lang.String value){
        checkAllowChange();
        
        this._encoding = value;
           
    }

    
    /**
     * 
     * xml name: filePath
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getFilePath(){
      return _filePath;
    }

    
    public void setFilePath(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._filePath = value;
           
    }

    
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
     * xml name: recordOutputProvider
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getRecordOutputProvider(){
      return _recordOutputProvider;
    }

    
    public void setRecordOutputProvider(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._recordOutputProvider = value;
           
    }

    
    /**
     * 
     * xml name: resourceIO
     *  
     */
    
    public java.lang.String getResourceIO(){
      return _resourceIO;
    }

    
    public void setResourceIO(java.lang.String value){
        checkAllowChange();
        
        this._resourceIO = value;
           
    }

    
    /**
     * 
     * xml name: resourceLoader
     *  
     */
    
    public java.lang.String getResourceLoader(){
      return _resourceLoader;
    }

    
    public void setResourceLoader(java.lang.String value){
        checkAllowChange();
        
        this._resourceLoader = value;
           
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
        
        out.putNotNull("encoding",this.getEncoding());
        out.putNotNull("filePath",this.getFilePath());
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("recordOutputProvider",this.getRecordOutputProvider());
        out.putNotNull("resourceIO",this.getResourceIO());
        out.putNotNull("resourceLoader",this.getResourceLoader());
    }

    public BatchFileWriterModel cloneInstance(){
        BatchFileWriterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchFileWriterModel instance){
        super.copyTo(instance);
        
        instance.setEncoding(this.getEncoding());
        instance.setFilePath(this.getFilePath());
        instance.setHeaders(this.getHeaders());
        instance.setRecordOutputProvider(this.getRecordOutputProvider());
        instance.setResourceIO(this.getResourceIO());
        instance.setResourceLoader(this.getResourceLoader());
    }

    protected BatchFileWriterModel newInstance(){
        return (BatchFileWriterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
