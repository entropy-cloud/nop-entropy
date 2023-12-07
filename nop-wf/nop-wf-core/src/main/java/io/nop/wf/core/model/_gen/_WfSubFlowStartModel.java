package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [263:14:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _WfSubFlowStartModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfSubFlowArgModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: return
     * 将子工作流中的output变量返回到本工作流中作为变量var
     */
    private KeyedList<io.nop.wf.core.model.WfSubFlowReturnModel> _returns = KeyedList.emptyList();
    
    /**
     *  
     * xml name: wfName
     * 
     */
    private java.lang.String _wfName ;
    
    /**
     *  
     * xml name: wfVersion
     * 
     */
    private java.lang.Long _wfVersion ;
    
    /**
     * 
     * xml name: arg
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfSubFlowArgModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.wf.core.model.WfSubFlowArgModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.wf.core.model.WfSubFlowArgModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfSubFlowArgModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.wf.core.model.WfSubFlowArgModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfSubFlowArgModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfSubFlowArgModel::getName);
            setArgs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_args(){
        return this._args.keySet();
    }

    public boolean hasArgs(){
        return !this._args.isEmpty();
    }
    
    /**
     * 
     * xml name: return
     *  将子工作流中的output变量返回到本工作流中作为变量var
     */
    
    public java.util.List<io.nop.wf.core.model.WfSubFlowReturnModel> getReturns(){
      return _returns;
    }

    
    public void setReturns(java.util.List<io.nop.wf.core.model.WfSubFlowReturnModel> value){
        checkAllowChange();
        
        this._returns = KeyedList.fromList(value, io.nop.wf.core.model.WfSubFlowReturnModel::getVar);
           
    }

    
    public io.nop.wf.core.model.WfSubFlowReturnModel getReturn(String name){
        return this._returns.getByKey(name);
    }

    public boolean hasReturn(String name){
        return this._returns.containsKey(name);
    }

    public void addReturn(io.nop.wf.core.model.WfSubFlowReturnModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfSubFlowReturnModel> list = this.getReturns();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfSubFlowReturnModel::getVar);
            setReturns(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_returns(){
        return this._returns.keySet();
    }

    public boolean hasReturns(){
        return !this._returns.isEmpty();
    }
    
    /**
     * 
     * xml name: wfName
     *  
     */
    
    public java.lang.String getWfName(){
      return _wfName;
    }

    
    public void setWfName(java.lang.String value){
        checkAllowChange();
        
        this._wfName = value;
           
    }

    
    /**
     * 
     * xml name: wfVersion
     *  
     */
    
    public java.lang.Long getWfVersion(){
      return _wfVersion;
    }

    
    public void setWfVersion(java.lang.Long value){
        checkAllowChange();
        
        this._wfVersion = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
           this._returns = io.nop.api.core.util.FreezeHelper.deepFreeze(this._returns);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("args",this.getArgs());
        out.put("returns",this.getReturns());
        out.put("wfName",this.getWfName());
        out.put("wfVersion",this.getWfVersion());
    }
}
 // resume CPD analysis - CPD-ON
