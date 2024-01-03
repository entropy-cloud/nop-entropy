package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [19:6:0:0]/nop/schema/wf/wf.xdef <p>
 * start只对应唯一启动步骤， 避免多个地方都写判断。可以很方便的实现回退到初始节点
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfStartModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: arg
     * 
     */
    private KeyedList<io.nop.wf.core.model.WfArgVarModel> _args = KeyedList.emptyList();
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     *  
     * xml name: startStepName
     * 
     */
    private java.lang.String _startStepName ;
    
    /**
     *  
     * xml name: when
     * when返回false时调用start会抛出异常
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: arg
     *  
     */
    
    public java.util.List<io.nop.wf.core.model.WfArgVarModel> getArgs(){
      return _args;
    }

    
    public void setArgs(java.util.List<io.nop.wf.core.model.WfArgVarModel> value){
        checkAllowChange();
        
        this._args = KeyedList.fromList(value, io.nop.wf.core.model.WfArgVarModel::getName);
           
    }

    
    public io.nop.wf.core.model.WfArgVarModel getArg(String name){
        return this._args.getByKey(name);
    }

    public boolean hasArg(String name){
        return this._args.containsKey(name);
    }

    public void addArg(io.nop.wf.core.model.WfArgVarModel item) {
        checkAllowChange();
        java.util.List<io.nop.wf.core.model.WfArgVarModel> list = this.getArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.wf.core.model.WfArgVarModel::getName);
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
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: startStepName
     *  
     */
    
    public java.lang.String getStartStepName(){
      return _startStepName;
    }

    
    public void setStartStepName(java.lang.String value){
        checkAllowChange();
        
        this._startStepName = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  when返回false时调用start会抛出异常
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._args = io.nop.api.core.util.FreezeHelper.deepFreeze(this._args);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("args",this.getArgs());
        out.put("source",this.getSource());
        out.put("startStepName",this.getStartStepName());
        out.put("when",this.getWhen());
    }
}
 // resume CPD analysis - CPD-ON
