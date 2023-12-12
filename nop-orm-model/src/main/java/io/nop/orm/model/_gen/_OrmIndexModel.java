package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [188:10:0:0]/nop/schema/orm/entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
public abstract class _OrmIndexModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: column
     * 
     */
    private KeyedList<io.nop.orm.model.OrmIndexColumnModel> _columns = KeyedList.emptyList();
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: indexType
     * 
     */
    private java.lang.String _indexType ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: unique
     * 
     */
    private java.lang.Boolean _unique ;
    
    /**
     * 
     * xml name: column
     *  
     */
    
    public java.util.List<io.nop.orm.model.OrmIndexColumnModel> getColumns(){
      return _columns;
    }

    
    public void setColumns(java.util.List<io.nop.orm.model.OrmIndexColumnModel> value){
        checkAllowChange();
        
        this._columns = KeyedList.fromList(value, io.nop.orm.model.OrmIndexColumnModel::getName);
           
    }

    
    public io.nop.orm.model.OrmIndexColumnModel getColumn(String name){
        return this._columns.getByKey(name);
    }

    public boolean hasColumn(String name){
        return this._columns.containsKey(name);
    }

    public void addColumn(io.nop.orm.model.OrmIndexColumnModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.model.OrmIndexColumnModel> list = this.getColumns();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.model.OrmIndexColumnModel::getName);
            setColumns(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_columns(){
        return this._columns.keySet();
    }

    public boolean hasColumns(){
        return !this._columns.isEmpty();
    }
    
    /**
     * 
     * xml name: comment
     *  
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: indexType
     *  
     */
    
    public java.lang.String getIndexType(){
      return _indexType;
    }

    
    public void setIndexType(java.lang.String value){
        checkAllowChange();
        
        this._indexType = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: unique
     *  
     */
    
    public java.lang.Boolean getUnique(){
      return _unique;
    }

    
    public void setUnique(java.lang.Boolean value){
        checkAllowChange();
        
        this._unique = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._columns = io.nop.api.core.util.FreezeHelper.deepFreeze(this._columns);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("columns",this.getColumns());
        out.put("comment",this.getComment());
        out.put("displayName",this.getDisplayName());
        out.put("indexType",this.getIndexType());
        out.put("name",this.getName());
        out.put("unique",this.getUnique());
    }
}
 // resume CPD analysis - CPD-ON
