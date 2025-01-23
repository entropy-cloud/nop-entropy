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
     * xml name: csvFormat
     * 
     */
    private java.lang.String _csvFormat ;
    
    /**
     *  
     * xml name: encoding
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _encoding ;
    
    /**
     *  
     * xml name: fileModelPath
     * 
     */
    private java.lang.String _fileModelPath ;
    
    /**
     *  
     * xml name: filePath
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _filePath ;
    
    /**
     *  
     * xml name: headerLabels
     * 
     */
    private java.util.List<java.lang.String> _headerLabels ;
    
    /**
     *  
     * xml name: headers
     * 
     */
    private java.util.List<java.lang.String> _headers ;
    
    /**
     *  
     * xml name: newRecordOutputProvider
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _newRecordOutputProvider ;
    
    /**
     *  
     * xml name: resourceIO
     * 
     */
    private java.lang.String _resourceIO ;
    
    /**
     *  
     * xml name: resourceLocator
     * 
     */
    private java.lang.String _resourceLocator ;
    
    /**
     * 
     * xml name: csvFormat
     *  
     */
    
    public java.lang.String getCsvFormat(){
      return _csvFormat;
    }

    
    public void setCsvFormat(java.lang.String value){
        checkAllowChange();
        
        this._csvFormat = value;
           
    }

    
    /**
     * 
     * xml name: encoding
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEncoding(){
      return _encoding;
    }

    
    public void setEncoding(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._encoding = value;
           
    }

    
    /**
     * 
     * xml name: fileModelPath
     *  
     */
    
    public java.lang.String getFileModelPath(){
      return _fileModelPath;
    }

    
    public void setFileModelPath(java.lang.String value){
        checkAllowChange();
        
        this._fileModelPath = value;
           
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
     * xml name: headerLabels
     *  
     */
    
    public java.util.List<java.lang.String> getHeaderLabels(){
      return _headerLabels;
    }

    
    public void setHeaderLabels(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._headerLabels = value;
           
    }

    
    /**
     * 
     * xml name: headers
     *  
     */
    
    public java.util.List<java.lang.String> getHeaders(){
      return _headers;
    }

    
    public void setHeaders(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._headers = value;
           
    }

    
    /**
     * 
     * xml name: newRecordOutputProvider
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getNewRecordOutputProvider(){
      return _newRecordOutputProvider;
    }

    
    public void setNewRecordOutputProvider(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._newRecordOutputProvider = value;
           
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
     * xml name: resourceLocator
     *  
     */
    
    public java.lang.String getResourceLocator(){
      return _resourceLocator;
    }

    
    public void setResourceLocator(java.lang.String value){
        checkAllowChange();
        
        this._resourceLocator = value;
           
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
        
        out.putNotNull("csvFormat",this.getCsvFormat());
        out.putNotNull("encoding",this.getEncoding());
        out.putNotNull("fileModelPath",this.getFileModelPath());
        out.putNotNull("filePath",this.getFilePath());
        out.putNotNull("headerLabels",this.getHeaderLabels());
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("newRecordOutputProvider",this.getNewRecordOutputProvider());
        out.putNotNull("resourceIO",this.getResourceIO());
        out.putNotNull("resourceLocator",this.getResourceLocator());
    }

    public BatchFileWriterModel cloneInstance(){
        BatchFileWriterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchFileWriterModel instance){
        super.copyTo(instance);
        
        instance.setCsvFormat(this.getCsvFormat());
        instance.setEncoding(this.getEncoding());
        instance.setFileModelPath(this.getFileModelPath());
        instance.setFilePath(this.getFilePath());
        instance.setHeaderLabels(this.getHeaderLabels());
        instance.setHeaders(this.getHeaders());
        instance.setNewRecordOutputProvider(this.getNewRecordOutputProvider());
        instance.setResourceIO(this.getResourceIO());
        instance.setResourceLocator(this.getResourceLocator());
    }

    protected BatchFileWriterModel newInstance(){
        return (BatchFileWriterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
