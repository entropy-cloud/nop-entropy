package io.nop.rule.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [50:6:0:0]/nop/schema/rule.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _RuleDecisionTableModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
    private io.nop.rule.core.model.RuleDeciderModel _colDecider ;
    
    /**
     *  
     * xml name: resultStartPos
     * 
     */
    private io.nop.core.model.table.CellPosition _resultStartPos ;
    
    /**
     *  
     * xml name: rowDecider
     * 
     */
    private io.nop.rule.core.model.RuleDeciderModel _rowDecider ;
    
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
    
    public io.nop.rule.core.model.RuleDeciderModel getColDecider(){
      return _colDecider;
    }

    
    public void setColDecider(io.nop.rule.core.model.RuleDeciderModel value){
        checkAllowChange();
        
        this._colDecider = value;
           
    }

    
    /**
     * 
     * xml name: resultStartPos
     *  
     */
    
    public io.nop.core.model.table.CellPosition getResultStartPos(){
      return _resultStartPos;
    }

    
    public void setResultStartPos(io.nop.core.model.table.CellPosition value){
        checkAllowChange();
        
        this._resultStartPos = value;
           
    }

    
    /**
     * 
     * xml name: rowDecider
     *  
     */
    
    public io.nop.rule.core.model.RuleDeciderModel getRowDecider(){
      return _rowDecider;
    }

    
    public void setRowDecider(io.nop.rule.core.model.RuleDeciderModel value){
        checkAllowChange();
        
        this._rowDecider = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cells = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cells);
            
           this._colDecider = io.nop.api.core.util.FreezeHelper.deepFreeze(this._colDecider);
            
           this._rowDecider = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rowDecider);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("cells",this.getCells());
        out.put("colDecider",this.getColDecider());
        out.put("resultStartPos",this.getResultStartPos());
        out.put("rowDecider",this.getRowDecider());
    }
}
 // resume CPD analysis - CPD-ON
