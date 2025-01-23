package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchFileReaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 当resourceIO/newRecordInputProvider/fileModelPath都没有指定的时候，会使用CsvResourceIO
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchFileReaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: csvFormat
     * 
     */
    private java.lang.String _csvFormat ;
    
    /**
     *  
     * xml name: encoding
     * 文件编码，缺省值为UTF-8
     */
    private io.nop.core.lang.eval.IEvalAction _encoding ;
    
    /**
     *  
     * xml name: fileModelPath
     * 文件模型路径。当没有指定resourceIO和newRecordInputProvider时，根据fileModelPath自动生成resourceIO
     */
    private java.lang.String _fileModelPath ;
    
    /**
     *  
     * xml name: filePath
     * 用于定位数据文件。支持表达式，支持使用${}引用变量
     */
    private io.nop.core.lang.eval.IEvalAction _filePath ;
    
    /**
     *  
     * xml name: filter
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _filter ;
    
    /**
     *  
     * xml name: headerLabels
     * 
     */
    private java.util.List<java.lang.String> _headerLabels ;
    
    /**
     *  
     * xml name: headers
     * 仅当使用缺省的CsvResourceIO时会使用这里的配置，它用于指定从数据文件中导入哪些列，如果不指定，则导入所有列。假定数据文件的第一行是列名
     */
    private java.util.List<java.lang.String> _headers ;
    
    /**
     *  
     * xml name: headersNormalizer
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _headersNormalizer ;
    
    /**
     *  
     * xml name: maxCountExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _maxCountExpr ;
    
    /**
     *  
     * xml name: newRecordInputProvider
     * 动态创建resourceIO
     */
    private io.nop.core.lang.eval.IEvalAction _newRecordInputProvider ;
    
    /**
     *  
     * xml name: resourceIO
     * 指定resourceIO对应的bean的名称。用于读取数据文件，如果不指定，则使用newRecordInputProvider，或者根据fileModelPath自动生成
     */
    private java.lang.String _resourceIO ;
    
    /**
     *  
     * xml name: resourceLocator
     * 用于定位filePath对应的数据文件。如果不指定，则使用ZipResourceLocator
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
     *  文件编码，缺省值为UTF-8
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
     *  文件模型路径。当没有指定resourceIO和newRecordInputProvider时，根据fileModelPath自动生成resourceIO
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
     *  用于定位数据文件。支持表达式，支持使用${}引用变量
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
     * xml name: filter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getFilter(){
      return _filter;
    }

    
    public void setFilter(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._filter = value;
           
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
     *  仅当使用缺省的CsvResourceIO时会使用这里的配置，它用于指定从数据文件中导入哪些列，如果不指定，则导入所有列。假定数据文件的第一行是列名
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
     * xml name: headersNormalizer
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getHeadersNormalizer(){
      return _headersNormalizer;
    }

    
    public void setHeadersNormalizer(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._headersNormalizer = value;
           
    }

    
    /**
     * 
     * xml name: maxCountExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getMaxCountExpr(){
      return _maxCountExpr;
    }

    
    public void setMaxCountExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._maxCountExpr = value;
           
    }

    
    /**
     * 
     * xml name: newRecordInputProvider
     *  动态创建resourceIO
     */
    
    public io.nop.core.lang.eval.IEvalAction getNewRecordInputProvider(){
      return _newRecordInputProvider;
    }

    
    public void setNewRecordInputProvider(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._newRecordInputProvider = value;
           
    }

    
    /**
     * 
     * xml name: resourceIO
     *  指定resourceIO对应的bean的名称。用于读取数据文件，如果不指定，则使用newRecordInputProvider，或者根据fileModelPath自动生成
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
     *  用于定位filePath对应的数据文件。如果不指定，则使用ZipResourceLocator
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
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("headerLabels",this.getHeaderLabels());
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("headersNormalizer",this.getHeadersNormalizer());
        out.putNotNull("maxCountExpr",this.getMaxCountExpr());
        out.putNotNull("newRecordInputProvider",this.getNewRecordInputProvider());
        out.putNotNull("resourceIO",this.getResourceIO());
        out.putNotNull("resourceLocator",this.getResourceLocator());
    }

    public BatchFileReaderModel cloneInstance(){
        BatchFileReaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchFileReaderModel instance){
        super.copyTo(instance);
        
        instance.setCsvFormat(this.getCsvFormat());
        instance.setEncoding(this.getEncoding());
        instance.setFileModelPath(this.getFileModelPath());
        instance.setFilePath(this.getFilePath());
        instance.setFilter(this.getFilter());
        instance.setHeaderLabels(this.getHeaderLabels());
        instance.setHeaders(this.getHeaders());
        instance.setHeadersNormalizer(this.getHeadersNormalizer());
        instance.setMaxCountExpr(this.getMaxCountExpr());
        instance.setNewRecordInputProvider(this.getNewRecordInputProvider());
        instance.setResourceIO(this.getResourceIO());
        instance.setResourceLocator(this.getResourceLocator());
    }

    protected BatchFileReaderModel newInstance(){
        return (BatchFileReaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
