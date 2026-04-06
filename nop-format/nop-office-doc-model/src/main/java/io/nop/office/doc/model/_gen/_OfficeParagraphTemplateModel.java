package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeParagraphTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeParagraphTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     *  
     * xml name: visibleExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _visibleExpr ;
    
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
     * xml name: visibleExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getVisibleExpr(){
      return _visibleExpr;
    }

    
    public void setVisibleExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._visibleExpr = value;
           
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
        out.putNotNull("testExpr",this.getTestExpr());
        out.putNotNull("visibleExpr",this.getVisibleExpr());
    }

    public OfficeParagraphTemplateModel cloneInstance(){
        OfficeParagraphTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeParagraphTemplateModel instance){
        super.copyTo(instance);
        
        instance.setAfterRender(this.getAfterRender());
        instance.setBeforeRender(this.getBeforeRender());
        instance.setTestExpr(this.getTestExpr());
        instance.setVisibleExpr(this.getVisibleExpr());
    }

    protected OfficeParagraphTemplateModel newInstance(){
        return (OfficeParagraphTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
