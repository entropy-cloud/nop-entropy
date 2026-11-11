package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeImageTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeImageTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: chartBinding
     * WI8 image binding (B1): chartBinding and codeBinding are
     * mutually exclusive; default sizes encode B5 (chart 800x400,
     * code 0 = service default / content-fit)
     */
    private io.nop.office.doc.model.OfficeChartBindingModel _chartBinding ;
    
    /**
     *  
     * xml name: codeBinding
     * 
     */
    private io.nop.office.doc.model.OfficeCodeBindingModel _codeBinding ;
    
    /**
     *  
     * xml name: dataExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _dataExpr ;
    
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
     * xml name: chartBinding
     *  WI8 image binding (B1): chartBinding and codeBinding are
     * mutually exclusive; default sizes encode B5 (chart 800x400,
     * code 0 = service default / content-fit)
     */
    
    public io.nop.office.doc.model.OfficeChartBindingModel getChartBinding(){
      return _chartBinding;
    }

    
    public void setChartBinding(io.nop.office.doc.model.OfficeChartBindingModel value){
        checkAllowChange();
        
        this._chartBinding = value;
           
    }

    
    /**
     * 
     * xml name: codeBinding
     *  
     */
    
    public io.nop.office.doc.model.OfficeCodeBindingModel getCodeBinding(){
      return _codeBinding;
    }

    
    public void setCodeBinding(io.nop.office.doc.model.OfficeCodeBindingModel value){
        checkAllowChange();
        
        this._codeBinding = value;
           
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
        
           this._chartBinding = io.nop.api.core.util.FreezeHelper.deepFreeze(this._chartBinding);
            
           this._codeBinding = io.nop.api.core.util.FreezeHelper.deepFreeze(this._codeBinding);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterRender",this.getAfterRender());
        out.putNotNull("beforeRender",this.getBeforeRender());
        out.putNotNull("chartBinding",this.getChartBinding());
        out.putNotNull("codeBinding",this.getCodeBinding());
        out.putNotNull("dataExpr",this.getDataExpr());
        out.putNotNull("testExpr",this.getTestExpr());
    }

    public OfficeImageTemplateModel cloneInstance(){
        OfficeImageTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeImageTemplateModel instance){
        super.copyTo(instance);
        
        instance.setAfterRender(this.getAfterRender());
        instance.setBeforeRender(this.getBeforeRender());
        instance.setChartBinding(this.getChartBinding());
        instance.setCodeBinding(this.getCodeBinding());
        instance.setDataExpr(this.getDataExpr());
        instance.setTestExpr(this.getTestExpr());
    }

    protected OfficeImageTemplateModel newInstance(){
        return (OfficeImageTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
