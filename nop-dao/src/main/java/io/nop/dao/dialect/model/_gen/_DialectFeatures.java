package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DialectFeatures extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: supportBatchUpdate
     * 
     */
    private java.lang.Boolean _supportBatchUpdate ;
    
    /**
     *  
     * xml name: supportBatchUpdateCount
     * 
     */
    private java.lang.Boolean _supportBatchUpdateCount ;
    
    /**
     *  
     * xml name: supportDeleteFromJoin
     * 
     */
    private java.lang.Boolean _supportDeleteFromJoin ;
    
    /**
     *  
     * xml name: supportDeleteTableAlias
     * delete语句是否允许表别名。例如 delete my_table a where ...
     */
    private java.lang.Boolean _supportDeleteTableAlias ;
    
    /**
     *  
     * xml name: supportExecuteLargeUpdate
     * 
     */
    private java.lang.Boolean _supportExecuteLargeUpdate ;
    
    /**
     *  
     * xml name: supportFullJoin
     * 
     */
    private java.lang.Boolean _supportFullJoin ;
    
    /**
     *  
     * xml name: supportILike
     * 
     */
    private java.lang.Boolean _supportILike ;
    
    /**
     *  
     * xml name: supportLargeMaxRows
     * 
     */
    private java.lang.Boolean _supportLargeMaxRows ;
    
    /**
     *  
     * xml name: supportNullsFirst
     * 对于不支持的数据库，可以通过
     * order by if(isnull(field),0,1), field来模拟
     */
    private java.lang.Boolean _supportNullsFirst ;
    
    /**
     *  
     * xml name: supportOutputForUpdate
     * Update语句支持output子句
     */
    private java.lang.Boolean _supportOutputForUpdate ;
    
    /**
     *  
     * xml name: supportQueryTimeout
     * 
     */
    private java.lang.Boolean _supportQueryTimeout ;
    
    /**
     *  
     * xml name: supportReturningForUpdate
     * Update语句增加returning子句支持
     */
    private java.lang.Boolean _supportReturningForUpdate ;
    
    /**
     *  
     * xml name: supportRightJoin
     * 
     */
    private java.lang.Boolean _supportRightJoin ;
    
    /**
     *  
     * xml name: supportRowValueConstructor
     * 
     */
    private java.lang.Boolean _supportRowValueConstructor ;
    
    /**
     *  
     * xml name: supportSavePoint
     * 
     */
    private java.lang.Boolean _supportSavePoint ;
    
    /**
     *  
     * xml name: supportSequence
     * 
     */
    private java.lang.Boolean _supportSequence ;
    
    /**
     *  
     * xml name: supportSomeSubQuery
     * 
     */
    private java.lang.Boolean _supportSomeSubQuery ;
    
    /**
     *  
     * xml name: supportTransaction
     * 
     */
    private java.lang.Boolean _supportTransaction ;
    
    /**
     *  
     * xml name: supportTruncateTable
     * 
     */
    private java.lang.Boolean _supportTruncateTable ;
    
    /**
     *  
     * xml name: supportUpdateFromJoin
     * 
     */
    private java.lang.Boolean _supportUpdateFromJoin ;
    
    /**
     *  
     * xml name: supportUpdateTableAlias
     * update语句是否允许表别名。例如 update my_table a where ...
     */
    private java.lang.Boolean _supportUpdateTableAlias ;
    
    /**
     *  
     * xml name: supportWithAsClause
     * 
     */
    private java.lang.Boolean _supportWithAsClause ;
    
    /**
     *  
     * xml name: useAsInFrom
     * 
     */
    private java.lang.Boolean _useAsInFrom ;
    
    /**
     *  
     * xml name: useGetStringForDate
     * 
     */
    private java.lang.Boolean _useGetStringForDate ;
    
    /**
     * 
     * xml name: supportBatchUpdate
     *  
     */
    
    public java.lang.Boolean getSupportBatchUpdate(){
      return _supportBatchUpdate;
    }

    
    public void setSupportBatchUpdate(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportBatchUpdate = value;
           
    }

    
    /**
     * 
     * xml name: supportBatchUpdateCount
     *  
     */
    
    public java.lang.Boolean getSupportBatchUpdateCount(){
      return _supportBatchUpdateCount;
    }

    
    public void setSupportBatchUpdateCount(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportBatchUpdateCount = value;
           
    }

    
    /**
     * 
     * xml name: supportDeleteFromJoin
     *  
     */
    
    public java.lang.Boolean getSupportDeleteFromJoin(){
      return _supportDeleteFromJoin;
    }

    
    public void setSupportDeleteFromJoin(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportDeleteFromJoin = value;
           
    }

    
    /**
     * 
     * xml name: supportDeleteTableAlias
     *  delete语句是否允许表别名。例如 delete my_table a where ...
     */
    
    public java.lang.Boolean getSupportDeleteTableAlias(){
      return _supportDeleteTableAlias;
    }

    
    public void setSupportDeleteTableAlias(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportDeleteTableAlias = value;
           
    }

    
    /**
     * 
     * xml name: supportExecuteLargeUpdate
     *  
     */
    
    public java.lang.Boolean getSupportExecuteLargeUpdate(){
      return _supportExecuteLargeUpdate;
    }

    
    public void setSupportExecuteLargeUpdate(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportExecuteLargeUpdate = value;
           
    }

    
    /**
     * 
     * xml name: supportFullJoin
     *  
     */
    
    public java.lang.Boolean getSupportFullJoin(){
      return _supportFullJoin;
    }

    
    public void setSupportFullJoin(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportFullJoin = value;
           
    }

    
    /**
     * 
     * xml name: supportILike
     *  
     */
    
    public java.lang.Boolean getSupportILike(){
      return _supportILike;
    }

    
    public void setSupportILike(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportILike = value;
           
    }

    
    /**
     * 
     * xml name: supportLargeMaxRows
     *  
     */
    
    public java.lang.Boolean getSupportLargeMaxRows(){
      return _supportLargeMaxRows;
    }

    
    public void setSupportLargeMaxRows(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportLargeMaxRows = value;
           
    }

    
    /**
     * 
     * xml name: supportNullsFirst
     *  对于不支持的数据库，可以通过
     * order by if(isnull(field),0,1), field来模拟
     */
    
    public java.lang.Boolean getSupportNullsFirst(){
      return _supportNullsFirst;
    }

    
    public void setSupportNullsFirst(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportNullsFirst = value;
           
    }

    
    /**
     * 
     * xml name: supportOutputForUpdate
     *  Update语句支持output子句
     */
    
    public java.lang.Boolean getSupportOutputForUpdate(){
      return _supportOutputForUpdate;
    }

    
    public void setSupportOutputForUpdate(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportOutputForUpdate = value;
           
    }

    
    /**
     * 
     * xml name: supportQueryTimeout
     *  
     */
    
    public java.lang.Boolean getSupportQueryTimeout(){
      return _supportQueryTimeout;
    }

    
    public void setSupportQueryTimeout(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportQueryTimeout = value;
           
    }

    
    /**
     * 
     * xml name: supportReturningForUpdate
     *  Update语句增加returning子句支持
     */
    
    public java.lang.Boolean getSupportReturningForUpdate(){
      return _supportReturningForUpdate;
    }

    
    public void setSupportReturningForUpdate(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportReturningForUpdate = value;
           
    }

    
    /**
     * 
     * xml name: supportRightJoin
     *  
     */
    
    public java.lang.Boolean getSupportRightJoin(){
      return _supportRightJoin;
    }

    
    public void setSupportRightJoin(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportRightJoin = value;
           
    }

    
    /**
     * 
     * xml name: supportRowValueConstructor
     *  
     */
    
    public java.lang.Boolean getSupportRowValueConstructor(){
      return _supportRowValueConstructor;
    }

    
    public void setSupportRowValueConstructor(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportRowValueConstructor = value;
           
    }

    
    /**
     * 
     * xml name: supportSavePoint
     *  
     */
    
    public java.lang.Boolean getSupportSavePoint(){
      return _supportSavePoint;
    }

    
    public void setSupportSavePoint(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportSavePoint = value;
           
    }

    
    /**
     * 
     * xml name: supportSequence
     *  
     */
    
    public java.lang.Boolean getSupportSequence(){
      return _supportSequence;
    }

    
    public void setSupportSequence(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportSequence = value;
           
    }

    
    /**
     * 
     * xml name: supportSomeSubQuery
     *  
     */
    
    public java.lang.Boolean getSupportSomeSubQuery(){
      return _supportSomeSubQuery;
    }

    
    public void setSupportSomeSubQuery(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportSomeSubQuery = value;
           
    }

    
    /**
     * 
     * xml name: supportTransaction
     *  
     */
    
    public java.lang.Boolean getSupportTransaction(){
      return _supportTransaction;
    }

    
    public void setSupportTransaction(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportTransaction = value;
           
    }

    
    /**
     * 
     * xml name: supportTruncateTable
     *  
     */
    
    public java.lang.Boolean getSupportTruncateTable(){
      return _supportTruncateTable;
    }

    
    public void setSupportTruncateTable(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportTruncateTable = value;
           
    }

    
    /**
     * 
     * xml name: supportUpdateFromJoin
     *  
     */
    
    public java.lang.Boolean getSupportUpdateFromJoin(){
      return _supportUpdateFromJoin;
    }

    
    public void setSupportUpdateFromJoin(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportUpdateFromJoin = value;
           
    }

    
    /**
     * 
     * xml name: supportUpdateTableAlias
     *  update语句是否允许表别名。例如 update my_table a where ...
     */
    
    public java.lang.Boolean getSupportUpdateTableAlias(){
      return _supportUpdateTableAlias;
    }

    
    public void setSupportUpdateTableAlias(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportUpdateTableAlias = value;
           
    }

    
    /**
     * 
     * xml name: supportWithAsClause
     *  
     */
    
    public java.lang.Boolean getSupportWithAsClause(){
      return _supportWithAsClause;
    }

    
    public void setSupportWithAsClause(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportWithAsClause = value;
           
    }

    
    /**
     * 
     * xml name: useAsInFrom
     *  
     */
    
    public java.lang.Boolean getUseAsInFrom(){
      return _useAsInFrom;
    }

    
    public void setUseAsInFrom(java.lang.Boolean value){
        checkAllowChange();
        
        this._useAsInFrom = value;
           
    }

    
    /**
     * 
     * xml name: useGetStringForDate
     *  
     */
    
    public java.lang.Boolean getUseGetStringForDate(){
      return _useGetStringForDate;
    }

    
    public void setUseGetStringForDate(java.lang.Boolean value){
        checkAllowChange();
        
        this._useGetStringForDate = value;
           
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
        
        out.putNotNull("supportBatchUpdate",this.getSupportBatchUpdate());
        out.putNotNull("supportBatchUpdateCount",this.getSupportBatchUpdateCount());
        out.putNotNull("supportDeleteFromJoin",this.getSupportDeleteFromJoin());
        out.putNotNull("supportDeleteTableAlias",this.getSupportDeleteTableAlias());
        out.putNotNull("supportExecuteLargeUpdate",this.getSupportExecuteLargeUpdate());
        out.putNotNull("supportFullJoin",this.getSupportFullJoin());
        out.putNotNull("supportILike",this.getSupportILike());
        out.putNotNull("supportLargeMaxRows",this.getSupportLargeMaxRows());
        out.putNotNull("supportNullsFirst",this.getSupportNullsFirst());
        out.putNotNull("supportOutputForUpdate",this.getSupportOutputForUpdate());
        out.putNotNull("supportQueryTimeout",this.getSupportQueryTimeout());
        out.putNotNull("supportReturningForUpdate",this.getSupportReturningForUpdate());
        out.putNotNull("supportRightJoin",this.getSupportRightJoin());
        out.putNotNull("supportRowValueConstructor",this.getSupportRowValueConstructor());
        out.putNotNull("supportSavePoint",this.getSupportSavePoint());
        out.putNotNull("supportSequence",this.getSupportSequence());
        out.putNotNull("supportSomeSubQuery",this.getSupportSomeSubQuery());
        out.putNotNull("supportTransaction",this.getSupportTransaction());
        out.putNotNull("supportTruncateTable",this.getSupportTruncateTable());
        out.putNotNull("supportUpdateFromJoin",this.getSupportUpdateFromJoin());
        out.putNotNull("supportUpdateTableAlias",this.getSupportUpdateTableAlias());
        out.putNotNull("supportWithAsClause",this.getSupportWithAsClause());
        out.putNotNull("useAsInFrom",this.getUseAsInFrom());
        out.putNotNull("useGetStringForDate",this.getUseGetStringForDate());
    }

    public DialectFeatures cloneInstance(){
        DialectFeatures instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DialectFeatures instance){
        super.copyTo(instance);
        
        instance.setSupportBatchUpdate(this.getSupportBatchUpdate());
        instance.setSupportBatchUpdateCount(this.getSupportBatchUpdateCount());
        instance.setSupportDeleteFromJoin(this.getSupportDeleteFromJoin());
        instance.setSupportDeleteTableAlias(this.getSupportDeleteTableAlias());
        instance.setSupportExecuteLargeUpdate(this.getSupportExecuteLargeUpdate());
        instance.setSupportFullJoin(this.getSupportFullJoin());
        instance.setSupportILike(this.getSupportILike());
        instance.setSupportLargeMaxRows(this.getSupportLargeMaxRows());
        instance.setSupportNullsFirst(this.getSupportNullsFirst());
        instance.setSupportOutputForUpdate(this.getSupportOutputForUpdate());
        instance.setSupportQueryTimeout(this.getSupportQueryTimeout());
        instance.setSupportReturningForUpdate(this.getSupportReturningForUpdate());
        instance.setSupportRightJoin(this.getSupportRightJoin());
        instance.setSupportRowValueConstructor(this.getSupportRowValueConstructor());
        instance.setSupportSavePoint(this.getSupportSavePoint());
        instance.setSupportSequence(this.getSupportSequence());
        instance.setSupportSomeSubQuery(this.getSupportSomeSubQuery());
        instance.setSupportTransaction(this.getSupportTransaction());
        instance.setSupportTruncateTable(this.getSupportTruncateTable());
        instance.setSupportUpdateFromJoin(this.getSupportUpdateFromJoin());
        instance.setSupportUpdateTableAlias(this.getSupportUpdateTableAlias());
        instance.setSupportWithAsClause(this.getSupportWithAsClause());
        instance.setUseAsInFrom(this.getUseAsInFrom());
        instance.setUseGetStringForDate(this.getUseGetStringForDate());
    }

    protected DialectFeatures newInstance(){
        return (DialectFeatures) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
