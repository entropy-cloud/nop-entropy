package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [68:6:0:0]/nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DialectSqls extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: createSequence
     * 
     */
    private java.lang.String _createSequence ;
    
    /**
     *  
     * xml name: dateLiteral
     * 
     */
    private java.lang.String _dateLiteral ;
    
    /**
     *  
     * xml name: dateTimeLiteral
     * 
     */
    private java.lang.String _dateTimeLiteral ;
    
    /**
     *  
     * xml name: dropSequence
     * 
     */
    private java.lang.String _dropSequence ;
    
    /**
     *  
     * xml name: dropTable
     * 
     */
    private java.lang.String _dropTable ;
    
    /**
     *  
     * xml name: escapeSlash
     * 对sql字符串字面量进行转义时，是否需要将\转义为\\
     */
    private java.lang.Boolean _escapeSlash ;
    
    /**
     *  
     * xml name: exceptKeyword
     * 
     */
    private java.lang.String _exceptKeyword ;
    
    /**
     *  
     * xml name: falseString
     * 
     */
    private java.lang.String _falseString  = "0";
    
    /**
     *  
     * xml name: forUpdate
     * 
     */
    private java.lang.String _forUpdate ;
    
    /**
     *  
     * xml name: insertKeyword
     * 
     */
    private java.lang.String _insertKeyword ;
    
    /**
     *  
     * xml name: lockHint
     * 
     */
    private java.lang.String _lockHint ;
    
    /**
     *  
     * xml name: selectFromDual
     * 
     */
    private java.lang.String _selectFromDual ;
    
    /**
     *  
     * xml name: sequenceNextVal
     * 通过 {sequenceName}来表示参数
     */
    private java.lang.String _sequenceNextVal ;
    
    /**
     *  
     * xml name: timeLiteral
     * 
     */
    private java.lang.String _timeLiteral ;
    
    /**
     *  
     * xml name: timestampLiteral
     * 
     */
    private java.lang.String _timestampLiteral ;
    
    /**
     *  
     * xml name: trueString
     * 
     */
    private java.lang.String _trueString  = "1";
    
    /**
     *  
     * xml name: updateKeyword
     * 
     */
    private java.lang.String _updateKeyword ;
    
    /**
     *  
     * xml name: validationQuery
     * 
     */
    private java.lang.String _validationQuery ;
    
    /**
     * 
     * xml name: createSequence
     *  
     */
    
    public java.lang.String getCreateSequence(){
      return _createSequence;
    }

    
    public void setCreateSequence(java.lang.String value){
        checkAllowChange();
        
        this._createSequence = value;
           
    }

    
    /**
     * 
     * xml name: dateLiteral
     *  
     */
    
    public java.lang.String getDateLiteral(){
      return _dateLiteral;
    }

    
    public void setDateLiteral(java.lang.String value){
        checkAllowChange();
        
        this._dateLiteral = value;
           
    }

    
    /**
     * 
     * xml name: dateTimeLiteral
     *  
     */
    
    public java.lang.String getDateTimeLiteral(){
      return _dateTimeLiteral;
    }

    
    public void setDateTimeLiteral(java.lang.String value){
        checkAllowChange();
        
        this._dateTimeLiteral = value;
           
    }

    
    /**
     * 
     * xml name: dropSequence
     *  
     */
    
    public java.lang.String getDropSequence(){
      return _dropSequence;
    }

    
    public void setDropSequence(java.lang.String value){
        checkAllowChange();
        
        this._dropSequence = value;
           
    }

    
    /**
     * 
     * xml name: dropTable
     *  
     */
    
    public java.lang.String getDropTable(){
      return _dropTable;
    }

    
    public void setDropTable(java.lang.String value){
        checkAllowChange();
        
        this._dropTable = value;
           
    }

    
    /**
     * 
     * xml name: escapeSlash
     *  对sql字符串字面量进行转义时，是否需要将\转义为\\
     */
    
    public java.lang.Boolean getEscapeSlash(){
      return _escapeSlash;
    }

    
    public void setEscapeSlash(java.lang.Boolean value){
        checkAllowChange();
        
        this._escapeSlash = value;
           
    }

    
    /**
     * 
     * xml name: exceptKeyword
     *  
     */
    
    public java.lang.String getExceptKeyword(){
      return _exceptKeyword;
    }

    
    public void setExceptKeyword(java.lang.String value){
        checkAllowChange();
        
        this._exceptKeyword = value;
           
    }

    
    /**
     * 
     * xml name: falseString
     *  
     */
    
    public java.lang.String getFalseString(){
      return _falseString;
    }

    
    public void setFalseString(java.lang.String value){
        checkAllowChange();
        
        this._falseString = value;
           
    }

    
    /**
     * 
     * xml name: forUpdate
     *  
     */
    
    public java.lang.String getForUpdate(){
      return _forUpdate;
    }

    
    public void setForUpdate(java.lang.String value){
        checkAllowChange();
        
        this._forUpdate = value;
           
    }

    
    /**
     * 
     * xml name: insertKeyword
     *  
     */
    
    public java.lang.String getInsertKeyword(){
      return _insertKeyword;
    }

    
    public void setInsertKeyword(java.lang.String value){
        checkAllowChange();
        
        this._insertKeyword = value;
           
    }

    
    /**
     * 
     * xml name: lockHint
     *  
     */
    
    public java.lang.String getLockHint(){
      return _lockHint;
    }

    
    public void setLockHint(java.lang.String value){
        checkAllowChange();
        
        this._lockHint = value;
           
    }

    
    /**
     * 
     * xml name: selectFromDual
     *  
     */
    
    public java.lang.String getSelectFromDual(){
      return _selectFromDual;
    }

    
    public void setSelectFromDual(java.lang.String value){
        checkAllowChange();
        
        this._selectFromDual = value;
           
    }

    
    /**
     * 
     * xml name: sequenceNextVal
     *  通过 {sequenceName}来表示参数
     */
    
    public java.lang.String getSequenceNextVal(){
      return _sequenceNextVal;
    }

    
    public void setSequenceNextVal(java.lang.String value){
        checkAllowChange();
        
        this._sequenceNextVal = value;
           
    }

    
    /**
     * 
     * xml name: timeLiteral
     *  
     */
    
    public java.lang.String getTimeLiteral(){
      return _timeLiteral;
    }

    
    public void setTimeLiteral(java.lang.String value){
        checkAllowChange();
        
        this._timeLiteral = value;
           
    }

    
    /**
     * 
     * xml name: timestampLiteral
     *  
     */
    
    public java.lang.String getTimestampLiteral(){
      return _timestampLiteral;
    }

    
    public void setTimestampLiteral(java.lang.String value){
        checkAllowChange();
        
        this._timestampLiteral = value;
           
    }

    
    /**
     * 
     * xml name: trueString
     *  
     */
    
    public java.lang.String getTrueString(){
      return _trueString;
    }

    
    public void setTrueString(java.lang.String value){
        checkAllowChange();
        
        this._trueString = value;
           
    }

    
    /**
     * 
     * xml name: updateKeyword
     *  
     */
    
    public java.lang.String getUpdateKeyword(){
      return _updateKeyword;
    }

    
    public void setUpdateKeyword(java.lang.String value){
        checkAllowChange();
        
        this._updateKeyword = value;
           
    }

    
    /**
     * 
     * xml name: validationQuery
     *  
     */
    
    public java.lang.String getValidationQuery(){
      return _validationQuery;
    }

    
    public void setValidationQuery(java.lang.String value){
        checkAllowChange();
        
        this._validationQuery = value;
           
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
        
        out.putNotNull("createSequence",this.getCreateSequence());
        out.putNotNull("dateLiteral",this.getDateLiteral());
        out.putNotNull("dateTimeLiteral",this.getDateTimeLiteral());
        out.putNotNull("dropSequence",this.getDropSequence());
        out.putNotNull("dropTable",this.getDropTable());
        out.putNotNull("escapeSlash",this.getEscapeSlash());
        out.putNotNull("exceptKeyword",this.getExceptKeyword());
        out.putNotNull("falseString",this.getFalseString());
        out.putNotNull("forUpdate",this.getForUpdate());
        out.putNotNull("insertKeyword",this.getInsertKeyword());
        out.putNotNull("lockHint",this.getLockHint());
        out.putNotNull("selectFromDual",this.getSelectFromDual());
        out.putNotNull("sequenceNextVal",this.getSequenceNextVal());
        out.putNotNull("timeLiteral",this.getTimeLiteral());
        out.putNotNull("timestampLiteral",this.getTimestampLiteral());
        out.putNotNull("trueString",this.getTrueString());
        out.putNotNull("updateKeyword",this.getUpdateKeyword());
        out.putNotNull("validationQuery",this.getValidationQuery());
    }

    public DialectSqls cloneInstance(){
        DialectSqls instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DialectSqls instance){
        super.copyTo(instance);
        
        instance.setCreateSequence(this.getCreateSequence());
        instance.setDateLiteral(this.getDateLiteral());
        instance.setDateTimeLiteral(this.getDateTimeLiteral());
        instance.setDropSequence(this.getDropSequence());
        instance.setDropTable(this.getDropTable());
        instance.setEscapeSlash(this.getEscapeSlash());
        instance.setExceptKeyword(this.getExceptKeyword());
        instance.setFalseString(this.getFalseString());
        instance.setForUpdate(this.getForUpdate());
        instance.setInsertKeyword(this.getInsertKeyword());
        instance.setLockHint(this.getLockHint());
        instance.setSelectFromDual(this.getSelectFromDual());
        instance.setSequenceNextVal(this.getSequenceNextVal());
        instance.setTimeLiteral(this.getTimeLiteral());
        instance.setTimestampLiteral(this.getTimestampLiteral());
        instance.setTrueString(this.getTrueString());
        instance.setUpdateKeyword(this.getUpdateKeyword());
        instance.setValidationQuery(this.getValidationQuery());
    }

    protected DialectSqls newInstance(){
        return (DialectSqls) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
