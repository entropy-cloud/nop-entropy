package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeDocPageTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeDocPageTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: beginLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beginLoop ;
    
    /**
     *  
     * xml name: endLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _endLoop ;
    
    /**
     *  
     * xml name: loopIndexName
     * 
     */
    private java.lang.String _loopIndexName ;
    
    /**
     *  
     * xml name: loopItemsName
     * 
     */
    private java.lang.String _loopItemsName ;
    
    /**
     *  
     * xml name: loopVarName
     * 
     */
    private java.lang.String _loopVarName ;
    
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
     * xml name: beginLoop
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeginLoop(){
      return _beginLoop;
    }

    
    public void setBeginLoop(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beginLoop = value;
           
    }

    
    /**
     * 
     * xml name: endLoop
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEndLoop(){
      return _endLoop;
    }

    
    public void setEndLoop(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._endLoop = value;
           
    }

    
    /**
     * 
     * xml name: loopIndexName
     *  
     */
    
    public java.lang.String getLoopIndexName(){
      return _loopIndexName;
    }

    
    public void setLoopIndexName(java.lang.String value){
        checkAllowChange();
        
        this._loopIndexName = value;
           
    }

    
    /**
     * 
     * xml name: loopItemsName
     *  
     */
    
    public java.lang.String getLoopItemsName(){
      return _loopItemsName;
    }

    
    public void setLoopItemsName(java.lang.String value){
        checkAllowChange();
        
        this._loopItemsName = value;
           
    }

    
    /**
     * 
     * xml name: loopVarName
     *  
     */
    
    public java.lang.String getLoopVarName(){
      return _loopVarName;
    }

    
    public void setLoopVarName(java.lang.String value){
        checkAllowChange();
        
        this._loopVarName = value;
           
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
        out.putNotNull("beginLoop",this.getBeginLoop());
        out.putNotNull("endLoop",this.getEndLoop());
        out.putNotNull("loopIndexName",this.getLoopIndexName());
        out.putNotNull("loopItemsName",this.getLoopItemsName());
        out.putNotNull("loopVarName",this.getLoopVarName());
        out.putNotNull("testExpr",this.getTestExpr());
    }

    public OfficeDocPageTemplateModel cloneInstance(){
        OfficeDocPageTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeDocPageTemplateModel instance){
        super.copyTo(instance);
        
        instance.setAfterRender(this.getAfterRender());
        instance.setBeforeRender(this.getBeforeRender());
        instance.setBeginLoop(this.getBeginLoop());
        instance.setEndLoop(this.getEndLoop());
        instance.setLoopIndexName(this.getLoopIndexName());
        instance.setLoopItemsName(this.getLoopItemsName());
        instance.setLoopVarName(this.getLoopVarName());
        instance.setTestExpr(this.getTestExpr());
    }

    protected OfficeDocPageTemplateModel newInstance(){
        return (OfficeDocPageTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
