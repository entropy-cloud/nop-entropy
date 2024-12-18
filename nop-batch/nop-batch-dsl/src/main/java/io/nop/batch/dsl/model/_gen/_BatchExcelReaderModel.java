package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchExcelReaderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchExcelReaderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: dataSheetName
     * 
     */
    private java.lang.String _dataSheetName ;
    
    /**
     *  
     * xml name: filePath
     * 
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
     * xml name: headerSheetName
     * 
     */
    private java.lang.String _headerSheetName ;
    
    /**
     *  
     * xml name: headers
     * 
     */
    private java.util.List<java.lang.String> _headers ;
    
    /**
     *  
     * xml name: templatePath
     * 
     */
    private java.lang.String _templatePath ;
    
    /**
     *  
     * xml name: trailerSheetName
     * 
     */
    private java.lang.String _trailerSheetName ;
    
    /**
     * 
     * xml name: dataSheetName
     *  
     */
    
    public java.lang.String getDataSheetName(){
      return _dataSheetName;
    }

    
    public void setDataSheetName(java.lang.String value){
        checkAllowChange();
        
        this._dataSheetName = value;
           
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
     * xml name: headerSheetName
     *  
     */
    
    public java.lang.String getHeaderSheetName(){
      return _headerSheetName;
    }

    
    public void setHeaderSheetName(java.lang.String value){
        checkAllowChange();
        
        this._headerSheetName = value;
           
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
     * xml name: templatePath
     *  
     */
    
    public java.lang.String getTemplatePath(){
      return _templatePath;
    }

    
    public void setTemplatePath(java.lang.String value){
        checkAllowChange();
        
        this._templatePath = value;
           
    }

    
    /**
     * 
     * xml name: trailerSheetName
     *  
     */
    
    public java.lang.String getTrailerSheetName(){
      return _trailerSheetName;
    }

    
    public void setTrailerSheetName(java.lang.String value){
        checkAllowChange();
        
        this._trailerSheetName = value;
           
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
        
        out.putNotNull("dataSheetName",this.getDataSheetName());
        out.putNotNull("filePath",this.getFilePath());
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("headerLabels",this.getHeaderLabels());
        out.putNotNull("headerSheetName",this.getHeaderSheetName());
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("templatePath",this.getTemplatePath());
        out.putNotNull("trailerSheetName",this.getTrailerSheetName());
    }

    public BatchExcelReaderModel cloneInstance(){
        BatchExcelReaderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchExcelReaderModel instance){
        super.copyTo(instance);
        
        instance.setDataSheetName(this.getDataSheetName());
        instance.setFilePath(this.getFilePath());
        instance.setFilter(this.getFilter());
        instance.setHeaderLabels(this.getHeaderLabels());
        instance.setHeaderSheetName(this.getHeaderSheetName());
        instance.setHeaders(this.getHeaders());
        instance.setTemplatePath(this.getTemplatePath());
        instance.setTrailerSheetName(this.getTrailerSheetName());
    }

    protected BatchExcelReaderModel newInstance(){
        return (BatchExcelReaderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
