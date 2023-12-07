package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [152:18:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _ExcelImage extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: anchor
     * 
     */
    private io.nop.excel.model.ExcelClientAnchor _anchor ;
    
    /**
     *  
     * xml name: data
     * data为对应图片数据
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
     * xml name: imgType
     * 
     */
    private java.lang.String _imgType ;
    
    /**
     *  
     * xml name: linkExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _linkExpr ;
    
    /**
     *  
     * xml name: linkUrl
     * 
     */
    private java.lang.String _linkUrl ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: noChangeAspect
     * 
     */
    private boolean _noChangeAspect  = false;
    
    /**
     *  
     * xml name: print
     * 控制图片是否被打印。套打对应的背景图片不需要被打印
     */
    private boolean _print  = true;
    
    /**
     *  
     * xml name: rotateDegree
     * 
     */
    private double _rotateDegree  = 0.0;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
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
     * xml name: data
     *  data为对应图片数据
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
     * xml name: imgType
     *  
     */
    
    public java.lang.String getImgType(){
      return _imgType;
    }

    
    public void setImgType(java.lang.String value){
        checkAllowChange();
        
        this._imgType = value;
           
    }

    
    /**
     * 
     * xml name: linkExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getLinkExpr(){
      return _linkExpr;
    }

    
    public void setLinkExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._linkExpr = value;
           
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

    
    /**
     * 
     * xml name: noChangeAspect
     *  
     */
    
    public boolean isNoChangeAspect(){
      return _noChangeAspect;
    }

    
    public void setNoChangeAspect(boolean value){
        checkAllowChange();
        
        this._noChangeAspect = value;
           
    }

    
    /**
     * 
     * xml name: print
     *  控制图片是否被打印。套打对应的背景图片不需要被打印
     */
    
    public boolean isPrint(){
      return _print;
    }

    
    public void setPrint(boolean value){
        checkAllowChange();
        
        this._print = value;
           
    }

    
    /**
     * 
     * xml name: rotateDegree
     *  
     */
    
    public double getRotateDegree(){
      return _rotateDegree;
    }

    
    public void setRotateDegree(double value){
        checkAllowChange();
        
        this._rotateDegree = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._anchor = io.nop.api.core.util.FreezeHelper.deepFreeze(this._anchor);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("anchor",this.getAnchor());
        out.put("data",this.getData());
        out.put("dataExpr",this.getDataExpr());
        out.put("description",this.getDescription());
        out.put("imgType",this.getImgType());
        out.put("linkExpr",this.getLinkExpr());
        out.put("linkUrl",this.getLinkUrl());
        out.put("name",this.getName());
        out.put("noChangeAspect",this.isNoChangeAspect());
        out.put("print",this.isPrint());
        out.put("rotateDegree",this.getRotateDegree());
        out.put("testExpr",this.getTestExpr());
    }
}
 // resume CPD analysis - CPD-ON
