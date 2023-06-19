package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [144:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _InvokeTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.task.model.TaskInvokeArgModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: ignoreReturn
     * 
     */
    private boolean _ignoreReturn  = false;
    
    /**
     *  
     * xml name: method
     * 
     */
    private java.lang.String _method ;
    
    /**
     * 
     * xml name: arg
     *  
     */
    
    public java.util.List<io.nop.task.model.TaskInvokeArgModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.task.model.TaskInvokeArgModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.task.model.TaskInvokeArgModel::getIndex);
           
    }

    
    public io.nop.task.model.TaskInvokeArgModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.task.model.TaskInvokeArgModel item) {
        checkAllowChange();
        java.util.List<io.nop.task.model.TaskInvokeArgModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.task.model.TaskInvokeArgModel::getIndex);
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
     * xml name: bean
     *  
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: ignoreReturn
     *  
     */
    
    public boolean isIgnoreReturn(){
      return _ignoreReturn;
    }

    
    public void setIgnoreReturn(boolean value){
        checkAllowChange();
        
        this._ignoreReturn = value;
           
    }

    
    /**
     * 
     * xml name: method
     *  
     */
    
    public java.lang.String getMethod(){
      return _method;
    }

    
    public void setMethod(java.lang.String value){
        checkAllowChange();
        
        this._method = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("args",this.getArgs());
        out.put("bean",this.getBean());
        out.put("ignoreReturn",this.isIgnoreReturn());
        out.put("method",this.getMethod());
    }
}
 // resume CPD analysis - CPD-ON
