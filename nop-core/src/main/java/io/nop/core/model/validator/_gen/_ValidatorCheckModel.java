package io.nop.core.model.validator._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [22:6:0:0]/nop/schema/validator.xdef <p>
 * check检查不通过会抛出异常
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ValidatorCheckModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bizFatal
     * 
     */
    private java.lang.Boolean _bizFatal ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.xml.XNode _condition ;
    
    /**
     *  
     * xml name: errorCode
     * 检查不通过时抛出的异常码。
     */
    private java.lang.String _errorCode ;
    
    /**
     *  
     * xml name: errorDescription
     * 
     */
    private java.lang.String _errorDescription ;
    
    /**
     *  
     * xml name: errorParams
     * 检查不通过时异常消息中的参数。格式为x=a.b.c, y, z=u这种形式，相当于执行代码{ x: a.b.c, y: y, z: u}。
     * 它表示将a.b.c的值赋给变量x, 将当前环境中y的值赋给变量y，将u的值赋给变量z
     */
    private java.util.Map<java.lang.String,java.lang.String> _errorParams ;
    
    /**
     *  
     * xml name: errorStatus
     * 
     */
    private java.lang.Integer _errorStatus ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: severity
     * 严重程度。值越大严重性越高。如果有多个检查失败，可以选择只返回最严重的错误信息（一个或者多个）
     */
    private int _severity  = 0;
    
    /**
     * 
     * xml name: bizFatal
     *  
     */
    
    public java.lang.Boolean getBizFatal(){
      return _bizFatal;
    }

    
    public void setBizFatal(java.lang.Boolean value){
        checkAllowChange();
        
        this._bizFatal = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.core.lang.xml.XNode getCondition(){
      return _condition;
    }

    
    public void setCondition(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._condition = value;
           
    }

    
    /**
     * 
     * xml name: errorCode
     *  检查不通过时抛出的异常码。
     */
    
    public java.lang.String getErrorCode(){
      return _errorCode;
    }

    
    public void setErrorCode(java.lang.String value){
        checkAllowChange();
        
        this._errorCode = value;
           
    }

    
    /**
     * 
     * xml name: errorDescription
     *  
     */
    
    public java.lang.String getErrorDescription(){
      return _errorDescription;
    }

    
    public void setErrorDescription(java.lang.String value){
        checkAllowChange();
        
        this._errorDescription = value;
           
    }

    
    /**
     * 
     * xml name: errorParams
     *  检查不通过时异常消息中的参数。格式为x=a.b.c, y, z=u这种形式，相当于执行代码{ x: a.b.c, y: y, z: u}。
     * 它表示将a.b.c的值赋给变量x, 将当前环境中y的值赋给变量y，将u的值赋给变量z
     */
    
    public java.util.Map<java.lang.String,java.lang.String> getErrorParams(){
      return _errorParams;
    }

    
    public void setErrorParams(java.util.Map<java.lang.String,java.lang.String> value){
        checkAllowChange();
        
        this._errorParams = value;
           
    }

    
    public boolean hasErrorParams(){
        return this._errorParams != null && !this._errorParams.isEmpty();
    }
    
    /**
     * 
     * xml name: errorStatus
     *  
     */
    
    public java.lang.Integer getErrorStatus(){
      return _errorStatus;
    }

    
    public void setErrorStatus(java.lang.Integer value){
        checkAllowChange();
        
        this._errorStatus = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: severity
     *  严重程度。值越大严重性越高。如果有多个检查失败，可以选择只返回最严重的错误信息（一个或者多个）
     */
    
    public int getSeverity(){
      return _severity;
    }

    
    public void setSeverity(int value){
        checkAllowChange();
        
        this._severity = value;
           
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
        
        out.put("bizFatal",this.getBizFatal());
        out.put("condition",this.getCondition());
        out.put("errorCode",this.getErrorCode());
        out.put("errorDescription",this.getErrorDescription());
        out.put("errorParams",this.getErrorParams());
        out.put("errorStatus",this.getErrorStatus());
        out.put("id",this.getId());
        out.put("severity",this.getSeverity());
    }
}
 // resume CPD analysis - CPD-ON
