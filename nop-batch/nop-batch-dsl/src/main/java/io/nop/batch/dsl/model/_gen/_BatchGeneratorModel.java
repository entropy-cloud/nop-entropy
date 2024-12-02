package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchGeneratorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchGeneratorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: genModelPath
     * 
     */
    private java.lang.String _genModelPath ;
    
    /**
     *  
     * xml name: totalCountExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _totalCountExpr ;
    
    /**
     * 
     * xml name: genModelPath
     *  
     */
    
    public java.lang.String getGenModelPath(){
      return _genModelPath;
    }

    
    public void setGenModelPath(java.lang.String value){
        checkAllowChange();
        
        this._genModelPath = value;
           
    }

    
    /**
     * 
     * xml name: totalCountExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getTotalCountExpr(){
      return _totalCountExpr;
    }

    
    public void setTotalCountExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._totalCountExpr = value;
           
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
        
        out.putNotNull("genModelPath",this.getGenModelPath());
        out.putNotNull("totalCountExpr",this.getTotalCountExpr());
    }

    public BatchGeneratorModel cloneInstance(){
        BatchGeneratorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchGeneratorModel instance){
        super.copyTo(instance);
        
        instance.setGenModelPath(this.getGenModelPath());
        instance.setTotalCountExpr(this.getTotalCountExpr());
    }

    protected BatchGeneratorModel newInstance(){
        return (BatchGeneratorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
