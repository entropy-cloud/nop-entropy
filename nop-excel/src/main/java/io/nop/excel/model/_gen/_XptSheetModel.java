package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.XptSheetModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [224:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XptSheetModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterExpand
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterExpand ;
    
    /**
     *  
     * xml name: beforeExpand
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeExpand ;
    
    /**
     *  
     * xml name: beginLoop
     * 可以根据模板生成多个sheet。 beginLoop如果返回数组，则针对数组中的每一项都生成一个Sheet
     */
    private io.nop.core.lang.eval.IEvalAction _beginLoop ;
    
    /**
     *  
     * xml name: endLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _endLoop ;
    
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
     * xml name: sheetNameExpr
     * 动态生成sheet的名称，返回值类型必须是字符串
     */
    private io.nop.core.lang.eval.IEvalAction _sheetNameExpr ;
    
    /**
     *  
     * xml name: sheetVarName
     * 如果非空，则所有顶层的非展开单元格中的field实际都对应于此对象中的field。
     * 例如 sheetVarName=entity, 则field=x 实际对应 entity.x
     * 如果没有指定sheetVarName, 则field=x，实际对应scope.getValue('x')
     */
    private java.lang.String _sheetVarName ;
    
    /**
     *  
     * xml name: testExpr
     * 如果返回false，则跳过当前sheet的生成。判断通过之后才会执行beginLoop
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     * 
     * xml name: afterExpand
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterExpand(){
      return _afterExpand;
    }

    
    public void setAfterExpand(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterExpand = value;
           
    }

    
    /**
     * 
     * xml name: beforeExpand
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeExpand(){
      return _beforeExpand;
    }

    
    public void setBeforeExpand(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeExpand = value;
           
    }

    
    /**
     * 
     * xml name: beginLoop
     *  可以根据模板生成多个sheet。 beginLoop如果返回数组，则针对数组中的每一项都生成一个Sheet
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
     * xml name: sheetNameExpr
     *  动态生成sheet的名称，返回值类型必须是字符串
     */
    
    public io.nop.core.lang.eval.IEvalAction getSheetNameExpr(){
      return _sheetNameExpr;
    }

    
    public void setSheetNameExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._sheetNameExpr = value;
           
    }

    
    /**
     * 
     * xml name: sheetVarName
     *  如果非空，则所有顶层的非展开单元格中的field实际都对应于此对象中的field。
     * 例如 sheetVarName=entity, 则field=x 实际对应 entity.x
     * 如果没有指定sheetVarName, 则field=x，实际对应scope.getValue('x')
     */
    
    public java.lang.String getSheetVarName(){
      return _sheetVarName;
    }

    
    public void setSheetVarName(java.lang.String value){
        checkAllowChange();
        
        this._sheetVarName = value;
           
    }

    
    /**
     * 
     * xml name: testExpr
     *  如果返回false，则跳过当前sheet的生成。判断通过之后才会执行beginLoop
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
        
        out.putNotNull("afterExpand",this.getAfterExpand());
        out.putNotNull("beforeExpand",this.getBeforeExpand());
        out.putNotNull("beginLoop",this.getBeginLoop());
        out.putNotNull("endLoop",this.getEndLoop());
        out.putNotNull("loopIndexName",this.getLoopIndexName());
        out.putNotNull("loopItemsName",this.getLoopItemsName());
        out.putNotNull("loopVarName",this.getLoopVarName());
        out.putNotNull("sheetNameExpr",this.getSheetNameExpr());
        out.putNotNull("sheetVarName",this.getSheetVarName());
        out.putNotNull("testExpr",this.getTestExpr());
    }

    public XptSheetModel cloneInstance(){
        XptSheetModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XptSheetModel instance){
        super.copyTo(instance);
        
        instance.setAfterExpand(this.getAfterExpand());
        instance.setBeforeExpand(this.getBeforeExpand());
        instance.setBeginLoop(this.getBeginLoop());
        instance.setEndLoop(this.getEndLoop());
        instance.setLoopIndexName(this.getLoopIndexName());
        instance.setLoopItemsName(this.getLoopItemsName());
        instance.setLoopVarName(this.getLoopVarName());
        instance.setSheetNameExpr(this.getSheetNameExpr());
        instance.setSheetVarName(this.getSheetVarName());
        instance.setTestExpr(this.getTestExpr());
    }

    protected XptSheetModel newInstance(){
        return (XptSheetModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
