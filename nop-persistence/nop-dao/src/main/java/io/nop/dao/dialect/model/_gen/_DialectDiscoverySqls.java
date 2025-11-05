package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.DialectDiscoverySqls;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("selectCatalogs",this.getSelectCatalogs());
        out.putNotNull("selectConstaints",this.getSelectConstaints());
        out.putNotNull("selectIndexes",this.getSelectIndexes());
        out.putNotNull("selectPrimaryKeys",this.getSelectPrimaryKeys());
        out.putNotNull("selectSchemas",this.getSelectSchemas());
        out.putNotNull("selectSequences",this.getSelectSequences());
        out.putNotNull("selectTableColumnComments",this.getSelectTableColumnComments());
        out.putNotNull("selectTableComments",this.getSelectTableComments());
        out.putNotNull("selectTables",this.getSelectTables());
        out.putNotNull("selectViewColumnComments",this.getSelectViewColumnComments());
        out.putNotNull("selectViewComments",this.getSelectViewComments());
        out.putNotNull("selectViews",this.getSelectViews());
    }

    public DialectDiscoverySqls cloneInstance(){
        DialectDiscoverySqls instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DialectDiscoverySqls instance){
        super.copyTo(instance);
        
        instance.setSelectCatalogs(this.getSelectCatalogs());
        instance.setSelectConstaints(this.getSelectConstaints());
        instance.setSelectIndexes(this.getSelectIndexes());
        instance.setSelectPrimaryKeys(this.getSelectPrimaryKeys());
        instance.setSelectSchemas(this.getSelectSchemas());
        instance.setSelectSequences(this.getSelectSequences());
        instance.setSelectTableColumnComments(this.getSelectTableColumnComments());
        instance.setSelectTableComments(this.getSelectTableComments());
        instance.setSelectTables(this.getSelectTables());
        instance.setSelectViewColumnComments(this.getSelectViewColumnComments());
        instance.setSelectViewComments(this.getSelectViewComments());
        instance.setSelectViews(this.getSelectViews());
    }

    protected DialectDiscoverySqls newInstance(){
        return (DialectDiscoverySqls) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
