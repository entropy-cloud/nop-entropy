package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartHierarchicalConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartHierarchicalConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: algorithm
     * 
     */
    private io.nop.excel.chart.constants.ChartHierarchicalLayout _algorithm ;
    
    /**
     *  
     * xml name: leaves
     * 
     */
    private io.nop.excel.chart.model.ChartHierarchicalLeafModel _leaves ;
    
    /**
     *  
     * xml name: levels
     * 
     */
    private io.nop.excel.chart.model.ChartHierarchicalLevelModel _levels ;
    
    /**
     * 
     * xml name: algorithm
     *  
     */
    
    public io.nop.excel.chart.constants.ChartHierarchicalLayout getAlgorithm(){
      return _algorithm;
    }

    
    public void setAlgorithm(io.nop.excel.chart.constants.ChartHierarchicalLayout value){
        checkAllowChange();
        
        this._algorithm = value;
           
    }

    
    /**
     * 
     * xml name: leaves
     *  
     */
    
    public io.nop.excel.chart.model.ChartHierarchicalLeafModel getLeaves(){
      return _leaves;
    }

    
    public void setLeaves(io.nop.excel.chart.model.ChartHierarchicalLeafModel value){
        checkAllowChange();
        
        this._leaves = value;
           
    }

    
    /**
     * 
     * xml name: levels
     *  
     */
    
    public io.nop.excel.chart.model.ChartHierarchicalLevelModel getLevels(){
      return _levels;
    }

    
    public void setLevels(io.nop.excel.chart.model.ChartHierarchicalLevelModel value){
        checkAllowChange();
        
        this._levels = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._leaves = io.nop.api.core.util.FreezeHelper.deepFreeze(this._leaves);
            
           this._levels = io.nop.api.core.util.FreezeHelper.deepFreeze(this._levels);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("algorithm",this.getAlgorithm());
        out.putNotNull("leaves",this.getLeaves());
        out.putNotNull("levels",this.getLevels());
    }

    public ChartHierarchicalConfigModel cloneInstance(){
        ChartHierarchicalConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartHierarchicalConfigModel instance){
        super.copyTo(instance);
        
        instance.setAlgorithm(this.getAlgorithm());
        instance.setLeaves(this.getLeaves());
        instance.setLevels(this.getLevels());
    }

    protected ChartHierarchicalConfigModel newInstance(){
        return (ChartHierarchicalConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
