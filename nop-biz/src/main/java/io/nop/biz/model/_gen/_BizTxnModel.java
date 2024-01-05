package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizTxnModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [37:14:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizTxnModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: propagation
     * 
     */
    private io.nop.api.core.annotations.txn.TransactionPropagation _propagation ;
    
    /**
     *  
     * xml name: transactional
     * 是否自动打开事务
     */
    private java.lang.Boolean _transactional ;
    
    /**
     *  
     * xml name: txnGroup
     * 
     */
    private java.lang.String _txnGroup ;
    
    /**
     * 
     * xml name: propagation
     *  
     */
    
    public io.nop.api.core.annotations.txn.TransactionPropagation getPropagation(){
      return _propagation;
    }

    
    public void setPropagation(io.nop.api.core.annotations.txn.TransactionPropagation value){
        checkAllowChange();
        
        this._propagation = value;
           
    }

    
    /**
     * 
     * xml name: transactional
     *  是否自动打开事务
     */
    
    public java.lang.Boolean getTransactional(){
      return _transactional;
    }

    
    public void setTransactional(java.lang.Boolean value){
        checkAllowChange();
        
        this._transactional = value;
           
    }

    
    /**
     * 
     * xml name: txnGroup
     *  
     */
    
    public java.lang.String getTxnGroup(){
      return _txnGroup;
    }

    
    public void setTxnGroup(java.lang.String value){
        checkAllowChange();
        
        this._txnGroup = value;
           
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
        
        out.put("propagation",this.getPropagation());
        out.put("transactional",this.getTransactional());
        out.put("txnGroup",this.getTxnGroup());
    }

    public BizTxnModel cloneInstance(){
        BizTxnModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizTxnModel instance){
        super.copyTo(instance);
        
        instance.setPropagation(this.getPropagation());
        instance.setTransactional(this.getTransactional());
        instance.setTxnGroup(this.getTxnGroup());
    }

    protected BizTxnModel newInstance(){
        return (BizTxnModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
