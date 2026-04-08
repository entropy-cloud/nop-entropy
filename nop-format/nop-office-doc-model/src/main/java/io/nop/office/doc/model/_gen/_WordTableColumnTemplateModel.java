package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.WordTableColumnTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/word-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WordTableColumnTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: hiddenExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _hiddenExpr ;
    
    /**
     *  
     * xml name: styleIdExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _styleIdExpr ;
    
    /**
     *  
     * xml name: widthExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _widthExpr ;
    
    /**
     * 
     * xml name: hiddenExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getHiddenExpr(){
      return _hiddenExpr;
    }

    
    public void setHiddenExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._hiddenExpr = value;
           
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
     * xml name: widthExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getWidthExpr(){
      return _widthExpr;
    }

    
    public void setWidthExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._widthExpr = value;
           
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
        
        out.putNotNull("hiddenExpr",this.getHiddenExpr());
        out.putNotNull("styleIdExpr",this.getStyleIdExpr());
        out.putNotNull("widthExpr",this.getWidthExpr());
    }

    public WordTableColumnTemplateModel cloneInstance(){
        WordTableColumnTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WordTableColumnTemplateModel instance){
        super.copyTo(instance);
        
        instance.setHiddenExpr(this.getHiddenExpr());
        instance.setStyleIdExpr(this.getStyleIdExpr());
        instance.setWidthExpr(this.getWidthExpr());
    }

    protected WordTableColumnTemplateModel newInstance(){
        return (WordTableColumnTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
