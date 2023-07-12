package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [125:18:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelImage extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: anchor
     * 
     */
    private io.nop.excel.model.ExcelClientAnchor _anchor ;
    
    /**
     *  
     * xml name: autoSize
     * 
     */
    private boolean _autoSize  = false;
    
    /**
     *  
     * xml name: data
     * 
     */
    private io.nop.commons.bytes.ByteString _data ;
    
    /**
     *  
     * xml name: dataExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _dataExpr ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: linkUrl
     * 
     */
    private java.lang.String _linkUrl ;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
    /**
     * 
     * xml name: anchor
     *  
     */
    
    public io.nop.excel.model.ExcelClientAnchor getAnchor(){
      return _anchor;
    }

    
    public void setAnchor(io.nop.excel.model.ExcelClientAnchor value){
        checkAllowChange();
        
        this._anchor = value;
           
    }

    
    /**
     * 
     * xml name: autoSize
     *  
     */
    
    public boolean isAutoSize(){
      return _autoSize;
    }

    
    public void setAutoSize(boolean value){
        checkAllowChange();
        
        this._autoSize = value;
           
    }

    
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
     * xml name: dataExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getDataExpr(){
      return _dataExpr;
    }

    
    public void setDataExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._dataExpr = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
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
     * xml name: linkUrl
     *  
     */
    
    public java.lang.String getLinkUrl(){
      return _linkUrl;
    }

    
    public void setLinkUrl(java.lang.String value){
        checkAllowChange();
        
        this._linkUrl = value;
           
    }

    
    /**
     * 
     * xml name: testExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getTestExpr(){
      return _testExpr;
    }

    
    public void setTestExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._testExpr = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  
     */
    
    public java.lang.String getTitle(){
      return _title;
    }

    
    public void setTitle(java.lang.String value){
        checkAllowChange();
        
        this._title = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._anchor = io.nop.api.core.util.FreezeHelper.deepFreeze(this._anchor);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("anchor",this.getAnchor());
        out.put("autoSize",this.isAutoSize());
        out.put("data",this.getData());
        out.put("dataExpr",this.getDataExpr());
        out.put("description",this.getDescription());
        out.put("id",this.getId());
        out.put("linkUrl",this.getLinkUrl());
        out.put("testExpr",this.getTestExpr());
        out.put("title",this.getTitle());
    }
}
 // resume CPD analysis - CPD-ON
