package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefAbstractCheck;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * 所有XDef约束规则的公共部分
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefAbstractCheck extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: errorCode
     * 
     */
    private java.lang.String _errorCode ;
    
    /**
     *  
     * xml name: id
     * 规则自身的标识
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: message
     * 
     */
    private java.lang.String _message ;
    
    /**
     *  
     * xml name: select
     * 选择参与校验的节点集合（xpath/xselector 字符串）
     */
    private java.lang.String _select ;
    
    /**
     * 
     * xml name: errorCode
     *  
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
     * xml name: id
     *  规则自身的标识
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
     * xml name: message
     *  
     */
    
    public java.lang.String getMessage(){
      return _message;
    }

    
    public void setMessage(java.lang.String value){
        checkAllowChange();
        
        this._message = value;
           
    }

    
    /**
     * 
     * xml name: select
     *  选择参与校验的节点集合（xpath/xselector 字符串）
     */
    
    public java.lang.String getSelect(){
      return _select;
    }

    
    public void setSelect(java.lang.String value){
        checkAllowChange();
        
        this._select = value;
           
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
        
        out.putNotNull("errorCode",this.getErrorCode());
        out.putNotNull("id",this.getId());
        out.putNotNull("message",this.getMessage());
        out.putNotNull("select",this.getSelect());
    }

    public XDefAbstractCheck cloneInstance(){
        XDefAbstractCheck instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefAbstractCheck instance){
        super.copyTo(instance);
        
        instance.setErrorCode(this.getErrorCode());
        instance.setId(this.getId());
        instance.setMessage(this.getMessage());
        instance.setSelect(this.getSelect());
    }

    protected XDefAbstractCheck newInstance(){
        return (XDefAbstractCheck) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
