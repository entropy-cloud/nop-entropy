package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterRender
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterRender ;
    
    /**
     *  
     * xml name: beforeRender
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeRender ;
    
    /**
     *  
     * xml name: dataExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _dataExpr ;
    
    /**
     *  
     * xml name: expandExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _expandExpr ;
    
    /**
     *  
     * xml name: renderType
     * 
     */
    private java.lang.String _renderType ;
    
    /**
     *  
     * xml name: styleIdExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _styleIdExpr ;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     * 
     * xml name: afterRender
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterRender(){
      return _afterRender;
    }

    
    public void setAfterRender(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterRender = value;
           
    }

    
    /**
     * 
     * xml name: beforeRender
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeRender(){
      return _beforeRender;
    }

    
    public void setBeforeRender(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeRender = value;
           
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
     * xml name: expandExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getExpandExpr(){
      return _expandExpr;
    }

    
    public void setExpandExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._expandExpr = value;
           
    }

    
    /**
     * 
     * xml name: renderType
     *  
     */
    
    public java.lang.String getRenderType(){
      return _renderType;
    }

    
    public void setRenderType(java.lang.String value){
        checkAllowChange();
        
        this._renderType = value;
           
    }

    
    /**
     * 
     * xml name: styleIdExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getStyleIdExpr(){
      return _styleIdExpr;
    }

    
    public void setStyleIdExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._styleIdExpr = value;
           
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterRender",this.getAfterRender());
        out.putNotNull("beforeRender",this.getBeforeRender());
        out.putNotNull("dataExpr",this.getDataExpr());
        out.putNotNull("expandExpr",this.getExpandExpr());
        out.putNotNull("renderType",this.getRenderType());
        out.putNotNull("styleIdExpr",this.getStyleIdExpr());
        out.putNotNull("testExpr",this.getTestExpr());
    }

    public WordTableTemplateModel cloneInstance(){
        WordTableTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableTemplateModel instance){
        super.copyTo(instance);
        
        instance.setAfterRender(this.getAfterRender());
        instance.setBeforeRender(this.getBeforeRender());
        instance.setDataExpr(this.getDataExpr());
        instance.setExpandExpr(this.getExpandExpr());
        instance.setRenderType(this.getRenderType());
        instance.setStyleIdExpr(this.getStyleIdExpr());
        instance.setTestExpr(this.getTestExpr());
    }

    protected WordTableTemplateModel newInstance(){
        return (WordTableTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
