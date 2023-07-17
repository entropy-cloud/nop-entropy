package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [144:6:0:0]/nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _DialectDiscoverySqls extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: selectCatalogs
     * 
     */
    private java.lang.String _selectCatalogs ;
    
    /**
     *  
     * xml name: selectConstaints
     * 
     */
    private java.lang.String _selectConstaints ;
    
    /**
     *  
     * xml name: selectIndexes
     * 
     */
    private java.lang.String _selectIndexes ;
    
    /**
     *  
     * xml name: selectPrimaryKeys
     * 
     */
    private java.lang.String _selectPrimaryKeys ;
    
    /**
     *  
     * xml name: selectSchemas
     * 
     */
    private java.lang.String _selectSchemas ;
    
    /**
     *  
     * xml name: selectSequences
     * 
     */
    private java.lang.String _selectSequences ;
    
    /**
     *  
     * xml name: selectTableColumnComments
     * 
     */
    private java.lang.String _selectTableColumnComments ;
    
    /**
     *  
     * xml name: selectTableComments
     * 
     */
    private java.lang.String _selectTableComments ;
    
    /**
     *  
     * xml name: selectTables
     * 
     */
    private java.lang.String _selectTables ;
    
    /**
     *  
     * xml name: selectViewColumnComments
     * 
     */
    private java.lang.String _selectViewColumnComments ;
    
    /**
     *  
     * xml name: selectViewComments
     * 
     */
    private java.lang.String _selectViewComments ;
    
    /**
     *  
     * xml name: selectViews
     * 查询所有视图的定义
     */
    private java.lang.String _selectViews ;
    
    /**
     * 
     * xml name: selectCatalogs
     *  
     */
    
    public java.lang.String getSelectCatalogs(){
      return _selectCatalogs;
    }

    
    public void setSelectCatalogs(java.lang.String value){
        checkAllowChange();
        
        this._selectCatalogs = value;
           
    }

    
    /**
     * 
     * xml name: selectConstaints
     *  
     */
    
    public java.lang.String getSelectConstaints(){
      return _selectConstaints;
    }

    
    public void setSelectConstaints(java.lang.String value){
        checkAllowChange();
        
        this._selectConstaints = value;
           
    }

    
    /**
     * 
     * xml name: selectIndexes
     *  
     */
    
    public java.lang.String getSelectIndexes(){
      return _selectIndexes;
    }

    
    public void setSelectIndexes(java.lang.String value){
        checkAllowChange();
        
        this._selectIndexes = value;
           
    }

    
    /**
     * 
     * xml name: selectPrimaryKeys
     *  
     */
    
    public java.lang.String getSelectPrimaryKeys(){
      return _selectPrimaryKeys;
    }

    
    public void setSelectPrimaryKeys(java.lang.String value){
        checkAllowChange();
        
        this._selectPrimaryKeys = value;
           
    }

    
    /**
     * 
     * xml name: selectSchemas
     *  
     */
    
    public java.lang.String getSelectSchemas(){
      return _selectSchemas;
    }

    
    public void setSelectSchemas(java.lang.String value){
        checkAllowChange();
        
        this._selectSchemas = value;
           
    }

    
    /**
     * 
     * xml name: selectSequences
     *  
     */
    
    public java.lang.String getSelectSequences(){
      return _selectSequences;
    }

    
    public void setSelectSequences(java.lang.String value){
        checkAllowChange();
        
        this._selectSequences = value;
           
    }

    
    /**
     * 
     * xml name: selectTableColumnComments
     *  
     */
    
    public java.lang.String getSelectTableColumnComments(){
      return _selectTableColumnComments;
    }

    
    public void setSelectTableColumnComments(java.lang.String value){
        checkAllowChange();
        
        this._selectTableColumnComments = value;
           
    }

    
    /**
     * 
     * xml name: selectTableComments
     *  
     */
    
    public java.lang.String getSelectTableComments(){
      return _selectTableComments;
    }

    
    public void setSelectTableComments(java.lang.String value){
        checkAllowChange();
        
        this._selectTableComments = value;
           
    }

    
    /**
     * 
     * xml name: selectTables
     *  
     */
    
    public java.lang.String getSelectTables(){
      return _selectTables;
    }

    
    public void setSelectTables(java.lang.String value){
        checkAllowChange();
        
        this._selectTables = value;
           
    }

    
    /**
     * 
     * xml name: selectViewColumnComments
     *  
     */
    
    public java.lang.String getSelectViewColumnComments(){
      return _selectViewColumnComments;
    }

    
    public void setSelectViewColumnComments(java.lang.String value){
        checkAllowChange();
        
        this._selectViewColumnComments = value;
           
    }

    
    /**
     * 
     * xml name: selectViewComments
     *  
     */
    
    public java.lang.String getSelectViewComments(){
      return _selectViewComments;
    }

    
    public void setSelectViewComments(java.lang.String value){
        checkAllowChange();
        
        this._selectViewComments = value;
           
    }

    
    /**
     * 
     * xml name: selectViews
     *  查询所有视图的定义
     */
    
    public java.lang.String getSelectViews(){
      return _selectViews;
    }

    
    public void setSelectViews(java.lang.String value){
        checkAllowChange();
        
        this._selectViews = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("selectCatalogs",this.getSelectCatalogs());
        out.put("selectConstaints",this.getSelectConstaints());
        out.put("selectIndexes",this.getSelectIndexes());
        out.put("selectPrimaryKeys",this.getSelectPrimaryKeys());
        out.put("selectSchemas",this.getSelectSchemas());
        out.put("selectSequences",this.getSelectSequences());
        out.put("selectTableColumnComments",this.getSelectTableColumnComments());
        out.put("selectTableComments",this.getSelectTableComments());
        out.put("selectTables",this.getSelectTables());
        out.put("selectViewColumnComments",this.getSelectViewColumnComments());
        out.put("selectViewComments",this.getSelectViewComments());
        out.put("selectViews",this.getSelectViews());
    }
}
 // resume CPD analysis - CPD-ON
