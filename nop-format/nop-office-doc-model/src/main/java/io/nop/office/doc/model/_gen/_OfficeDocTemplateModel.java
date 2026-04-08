package io.nop.office.doc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.office.doc.model.OfficeDocTemplateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/office/doc.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OfficeDocTemplateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterGen
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterGen ;
    
    /**
     *  
     * xml name: beforeGen
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeGen ;
    
    /**
     *  
     * xml name: beginLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beginLoop ;
    
    /**
     *  
     * xml name: deleteAllAfterConfigTable
     * 
     */
    private boolean _deleteAllAfterConfigTable  = false;
    
    /**
     *  
     * xml name: dump
     * 
     */
    private boolean _dump  = false;
    
    /**
     *  
     * xml name: dumpFile
     * 
     */
    private java.lang.String _dumpFile ;
    
    /**
     *  
     * xml name: endLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _endLoop ;
    
    /**
     *  
     * xml name: importLibs
     * 
     */
    private java.util.List<java.lang.String> _importLibs ;
    
    /**
     *  
     * xml name: loopIndexName
     * 
     */
    private java.lang.String _loopIndexName ;
    
    /**
     *  
     * xml name: loopItemsName
     * 
     */
    private java.lang.String _loopItemsName ;
    
    /**
     *  
     * xml name: loopVarName
     * 
     */
    private java.lang.String _loopVarName ;
    
    /**
     *  
     * xml name: normalizeQuote
     * 
     */
    private boolean _normalizeQuote  = false;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     * 
     * xml name: afterGen
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterGen(){
      return _afterGen;
    }

    
    public void setAfterGen(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterGen = value;
           
    }

    
    /**
     * 
     * xml name: beforeGen
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeGen(){
      return _beforeGen;
    }

    
    public void setBeforeGen(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeGen = value;
           
    }

    
    /**
     * 
     * xml name: beginLoop
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeginLoop(){
      return _beginLoop;
    }

    
    public void setBeginLoop(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beginLoop = value;
           
    }

    
    /**
     * 
     * xml name: deleteAllAfterConfigTable
     *  
     */
    
    public boolean isDeleteAllAfterConfigTable(){
      return _deleteAllAfterConfigTable;
    }

    
    public void setDeleteAllAfterConfigTable(boolean value){
        checkAllowChange();
        
        this._deleteAllAfterConfigTable = value;
           
    }

    
    /**
     * 
     * xml name: dump
     *  
     */
    
    public boolean isDump(){
      return _dump;
    }

    
    public void setDump(boolean value){
        checkAllowChange();
        
        this._dump = value;
           
    }

    
    /**
     * 
     * xml name: dumpFile
     *  
     */
    
    public java.lang.String getDumpFile(){
      return _dumpFile;
    }

    
    public void setDumpFile(java.lang.String value){
        checkAllowChange();
        
        this._dumpFile = value;
           
    }

    
    /**
     * 
     * xml name: endLoop
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEndLoop(){
      return _endLoop;
    }

    
    public void setEndLoop(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._endLoop = value;
           
    }

    
    /**
     * 
     * xml name: importLibs
     *  
     */
    
    public java.util.List<java.lang.String> getImportLibs(){
      return _importLibs;
    }

    
    public void setImportLibs(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._importLibs = value;
           
    }

    
    /**
     * 
     * xml name: loopIndexName
     *  
     */
    
    public java.lang.String getLoopIndexName(){
      return _loopIndexName;
    }

    
    public void setLoopIndexName(java.lang.String value){
        checkAllowChange();
        
        this._loopIndexName = value;
           
    }

    
    /**
     * 
     * xml name: loopItemsName
     *  
     */
    
    public java.lang.String getLoopItemsName(){
      return _loopItemsName;
    }

    
    public void setLoopItemsName(java.lang.String value){
        checkAllowChange();
        
        this._loopItemsName = value;
           
    }

    
    /**
     * 
     * xml name: loopVarName
     *  
     */
    
    public java.lang.String getLoopVarName(){
      return _loopVarName;
    }

    
    public void setLoopVarName(java.lang.String value){
        checkAllowChange();
        
        this._loopVarName = value;
           
    }

    
    /**
     * 
     * xml name: normalizeQuote
     *  
     */
    
    public boolean isNormalizeQuote(){
      return _normalizeQuote;
    }

    
    public void setNormalizeQuote(boolean value){
        checkAllowChange();
        
        this._normalizeQuote = value;
           
    }

    
    /**
     * 
     * xml name: testExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getTestExpr(){
      return _testExpr;
    }

    
    public void setTestExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._testExpr = value;
           
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
        
        out.putNotNull("afterGen",this.getAfterGen());
        out.putNotNull("beforeGen",this.getBeforeGen());
        out.putNotNull("beginLoop",this.getBeginLoop());
        out.putNotNull("deleteAllAfterConfigTable",this.isDeleteAllAfterConfigTable());
        out.putNotNull("dump",this.isDump());
        out.putNotNull("dumpFile",this.getDumpFile());
        out.putNotNull("endLoop",this.getEndLoop());
        out.putNotNull("importLibs",this.getImportLibs());
        out.putNotNull("loopIndexName",this.getLoopIndexName());
        out.putNotNull("loopItemsName",this.getLoopItemsName());
        out.putNotNull("loopVarName",this.getLoopVarName());
        out.putNotNull("normalizeQuote",this.isNormalizeQuote());
        out.putNotNull("testExpr",this.getTestExpr());
    }

    public OfficeDocTemplateModel cloneInstance(){
        OfficeDocTemplateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OfficeDocTemplateModel instance){
        super.copyTo(instance);
        
        instance.setAfterGen(this.getAfterGen());
        instance.setBeforeGen(this.getBeforeGen());
        instance.setBeginLoop(this.getBeginLoop());
        instance.setDeleteAllAfterConfigTable(this.isDeleteAllAfterConfigTable());
        instance.setDump(this.isDump());
        instance.setDumpFile(this.getDumpFile());
        instance.setEndLoop(this.getEndLoop());
        instance.setImportLibs(this.getImportLibs());
        instance.setLoopIndexName(this.getLoopIndexName());
        instance.setLoopItemsName(this.getLoopItemsName());
        instance.setLoopVarName(this.getLoopVarName());
        instance.setNormalizeQuote(this.isNormalizeQuote());
        instance.setTestExpr(this.getTestExpr());
    }

    protected OfficeDocTemplateModel newInstance(){
        return (OfficeDocTemplateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
