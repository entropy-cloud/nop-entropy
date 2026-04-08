package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableRowTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableRowTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: heightExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _heightExpr ;
    
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
     * xml name: visibleExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _visibleExpr ;
    
    /**
     * 
     * xml name: heightExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getHeightExpr(){
      return _heightExpr;
    }

    
    public void setHeightExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._heightExpr = value;
           
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
        
        out.putNotNull("heightExpr",this.getHeightExpr());
        out.putNotNull("styleIdExpr",this.getStyleIdExpr());
        out.putNotNull("testExpr",this.getTestExpr());
        out.putNotNull("visibleExpr",this.getVisibleExpr());
    }

    public WordTableRowTemplateModel cloneInstance(){
        WordTableRowTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableRowTemplateModel instance){
        super.copyTo(instance);
        
        instance.setHeightExpr(this.getHeightExpr());
        instance.setStyleIdExpr(this.getStyleIdExpr());
        instance.setTestExpr(this.getTestExpr());
        instance.setVisibleExpr(this.getVisibleExpr());
    }

    protected WordTableRowTemplateModel newInstance(){
        return (WordTableRowTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
