package io.nop.rule.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rule.core.model.RuleDecisionMatrixModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [53:6:0:0]/nop/schema/rule.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RuleDecisionMatrixModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cells
     * 
     */
    private KeyedList<io.nop.rule.core.model.RuleTableCellModel> _cells = KeyedList.emptyList();
    
    /**
     *  
     * xml name: colDecider
     * 
     */
    private io.nop.rule.core.model.RuleDecisionTreeModel _colDecider ;
    
    /**
     *  
     * xml name: rowDecider
     * 
     */
    private io.nop.rule.core.model.RuleDecisionTreeModel _rowDecider ;
    
    /**
     * 
     * xml name: cells
     *  
     */
    
    public java.util.List<io.nop.rule.core.model.RuleTableCellModel> getCells(){
      return _cells;
    }

    
    public void setCells(java.util.List<io.nop.rule.core.model.RuleTableCellModel> value){
        checkAllowChange();
        
        this._cells = KeyedList.fromList(value, io.nop.rule.core.model.RuleTableCellModel::getPos);
           
    }

    
    public io.nop.rule.core.model.RuleTableCellModel getCell(String name){
        return this._cells.getByKey(name);
    }

    public boolean hasCell(String name){
        return this._cells.containsKey(name);
    }

    public void addCell(io.nop.rule.core.model.RuleTableCellModel item) {
        checkAllowChange();
        java.util.List<io.nop.rule.core.model.RuleTableCellModel> list = this.getCells();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rule.core.model.RuleTableCellModel::getPos);
            setCells(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_cells(){
        return this._cells.keySet();
    }

    public boolean hasCells(){
        return !this._cells.isEmpty();
    }
    
    /**
     * 
     * xml name: colDecider
     *  
     */
    
    public io.nop.rule.core.model.RuleDecisionTreeModel getColDecider(){
      return _colDecider;
    }

    
    public void setColDecider(io.nop.rule.core.model.RuleDecisionTreeModel value){
        checkAllowChange();
        
        this._colDecider = value;
           
    }

    
    /**
     * 
     * xml name: rowDecider
     *  
     */
    
    public io.nop.rule.core.model.RuleDecisionTreeModel getRowDecider(){
      return _rowDecider;
    }

    
    public void setRowDecider(io.nop.rule.core.model.RuleDecisionTreeModel value){
        checkAllowChange();
        
        this._rowDecider = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cells = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cells);
            
           this._colDecider = io.nop.api.core.util.FreezeHelper.deepFreeze(this._colDecider);
            
           this._rowDecider = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rowDecider);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("cells",this.getCells());
        out.put("colDecider",this.getColDecider());
        out.put("rowDecider",this.getRowDecider());
    }

    public RuleDecisionMatrixModel cloneInstance(){
        RuleDecisionMatrixModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RuleDecisionMatrixModel instance){
        super.copyTo(instance);
        
        instance.setCells(this.getCells());
        instance.setColDecider(this.getColDecider());
        instance.setRowDecider(this.getRowDecider());
    }

    protected RuleDecisionMatrixModel newInstance(){
        return (RuleDecisionMatrixModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
