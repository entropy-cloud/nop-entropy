package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeRunTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeRunTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: formatExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _formatExpr ;
    
    /**
     *  
     * xml name: linkExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _linkExpr ;
    
    /**
     *  
     * xml name: templateExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _templateExpr ;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _valueExpr ;
    
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
     * xml name: formatExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getFormatExpr(){
      return _formatExpr;
    }

    
    public void setFormatExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._formatExpr = value;
           
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
     * xml name: templateExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getTemplateExpr(){
      return _templateExpr;
    }

    
    public void setTemplateExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._templateExpr = value;
           
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
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
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
        out.putNotNull("formatExpr",this.getFormatExpr());
        out.putNotNull("linkExpr",this.getLinkExpr());
        out.putNotNull("templateExpr",this.getTemplateExpr());
        out.putNotNull("testExpr",this.getTestExpr());
        out.putNotNull("valueExpr",this.getValueExpr());
    }

    public OfficeRunTemplateModel cloneInstance(){
        OfficeRunTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeRunTemplateModel instance){
        super.copyTo(instance);
        
        instance.setAfterRender(this.getAfterRender());
        instance.setBeforeRender(this.getBeforeRender());
        instance.setFormatExpr(this.getFormatExpr());
        instance.setLinkExpr(this.getLinkExpr());
        instance.setTemplateExpr(this.getTemplateExpr());
        instance.setTestExpr(this.getTestExpr());
        instance.setValueExpr(this.getValueExpr());
    }

    protected OfficeRunTemplateModel newInstance(){
        return (OfficeRunTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
