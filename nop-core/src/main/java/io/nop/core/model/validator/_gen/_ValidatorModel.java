package io.nop.core.model.validator._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [1:2:0:0]/nop/schema/validator.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ValidatorModel extends io.nop.core.resource.component.AbstractSimpleComponentModel {
    
    /**
     *  
     * xml name: check
     * check检查不通过会抛出异常
     */
    private KeyedList<io.nop.core.model.validator.ValidatorCheckModel> _checks = KeyedList.emptyList();
    
    /**
     *  
     * xml name: condition
     * 
     */
    private io.nop.core.lang.xml.XNode _condition ;
    
    /**
     *  
     * xml name: errorCode
     * 
     */
    private java.lang.String _errorCode ;
    
    /**
     *  
     * xml name: errorParams
     * 
     */
    private java.util.Map<java.lang.String,java.lang.String> _errorParams ;
    
    /**
     *  
     * xml name: severity
     * 
     */
    private int _severity  = 0;
    
    /**
     * 
     * xml name: check
     *  check检查不通过会抛出异常
     */
    
    public java.util.List<io.nop.core.model.validator.ValidatorCheckModel> getChecks(){
      return _checks;
    }

    
    public void setChecks(java.util.List<io.nop.core.model.validator.ValidatorCheckModel> value){
        checkAllowChange();
        
        this._checks = KeyedList.fromList(value, io.nop.core.model.validator.ValidatorCheckModel::getId);
           
    }

    
    public io.nop.core.model.validator.ValidatorCheckModel getCheck(String name){
        return this._checks.getByKey(name);
    }

    public boolean hasCheck(String name){
        return this._checks.containsKey(name);
    }

    public void addCheck(io.nop.core.model.validator.ValidatorCheckModel item) {
        checkAllowChange();
        java.util.List<io.nop.core.model.validator.ValidatorCheckModel> list = this.getChecks();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.core.model.validator.ValidatorCheckModel::getId);
            setChecks(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_checks(){
        return this._checks.keySet();
    }

    public boolean hasChecks(){
        return !this._checks.isEmpty();
    }
    
    /**
     * 
     * xml name: condition
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
     * xml name: errorParams
     *  
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
     * xml name: severity
     *  
     */
    
    public int getSeverity(){
      return _severity;
    }

    
    public void setSeverity(int value){
        checkAllowChange();
        
        this._severity = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._checks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._checks);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("checks",this.getChecks());
        out.put("condition",this.getCondition());
        out.put("errorCode",this.getErrorCode());
        out.put("errorParams",this.getErrorParams());
        out.put("severity",this.getSeverity());
    }
}
 // resume CPD analysis - CPD-ON
