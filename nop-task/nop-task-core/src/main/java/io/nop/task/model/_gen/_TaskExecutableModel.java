package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [16:6:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _TaskExecutableModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: catch
     * 
     */
    private io.nop.task.model.TaskStepsModel _catch ;
    
    /**
     *  
     * xml name: decorator
     * 对taskStep进行增强，返回新的step
     */
    private KeyedList<io.nop.task.model.TaskDecoratorModel> _decorators = KeyedList.emptyList();
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: executor
     * 
     */
    private java.lang.String _executor ;
    
    /**
     *  
     * xml name: finally
     * 
     */
    private io.nop.task.model.TaskStepsModel _finally ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: input
     * 
     */
    private KeyedList<io.nop.task.model.TaskInputModel> _inputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: output
     * 
     */
    private KeyedList<io.nop.task.model.TaskOutputModel> _outputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: rate-limit
     * 限制对同一个key的调用速率不能超过指定值
     */
    private io.nop.task.model.TaskRateLimitModel _rateLimit ;
    
    /**
     *  
     * xml name: retry
     * 如果发生异常，则重试整个task
     */
    private io.nop.task.model.TaskRetryModel _retry ;
    
    /**
     *  
     * xml name: shareScope
     * 
     */
    private boolean _shareScope  = false;
    
    /**
     *  
     * xml name: throttle
     * 限制对同一个key的调用并发数不能超过指定值
     */
    private io.nop.task.model.TaskThrottleModel _throttle ;
    
    /**
     *  
     * xml name: timeout
     * 
     */
    private long _timeout  = 0L;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: catch
     *  
     */
    
    public io.nop.task.model.TaskStepsModel getCatch(){
      return _catch;
    }

    
    public void setCatch(io.nop.task.model.TaskStepsModel value){
        checkAllowChange();
        
        this._catch = value;
           
    }

    
    /**
     * 
     * xml name: decorator
     *  对taskStep进行增强，返回新的step
     */
    
    public java.util.List<io.nop.task.model.TaskDecoratorModel> getDecorators(){
      return _decorators;
    }

    
    public void setDecorators(java.util.List<io.nop.task.model.TaskDecoratorModel> value){
        checkAllowChange();
        
        this._decorators = KeyedList.fromList(value, io.nop.task.model.TaskDecoratorModel::getOrder);
           
    }

    
    public io.nop.task.model.TaskDecoratorModel getDecorator(String name){
        return this._decorators.getByKey(name);
    }

    public boolean hasDecorator(String name){
        return this._decorators.containsKey(name);
    }

    public void addDecorator(io.nop.task.model.TaskDecoratorModel item) {
        checkAllowChange();
        java.util.List<io.nop.task.model.TaskDecoratorModel> list = this.getDecorators();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.task.model.TaskDecoratorModel::getOrder);
            setDecorators(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_decorators(){
        return this._decorators.keySet();
    }

    public boolean hasDecorators(){
        return !this._decorators.isEmpty();
    }
    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: executor
     *  
     */
    
    public java.lang.String getExecutor(){
      return _executor;
    }

    
    public void setExecutor(java.lang.String value){
        checkAllowChange();
        
        this._executor = value;
           
    }

    
    /**
     * 
     * xml name: finally
     *  
     */
    
    public io.nop.task.model.TaskStepsModel getFinally(){
      return _finally;
    }

    
    public void setFinally(io.nop.task.model.TaskStepsModel value){
        checkAllowChange();
        
        this._finally = value;
           
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
     * xml name: input
     *  
     */
    
    public java.util.List<io.nop.task.model.TaskInputModel> getInputs(){
      return _inputs;
    }

    
    public void setInputs(java.util.List<io.nop.task.model.TaskInputModel> value){
        checkAllowChange();
        
        this._inputs = KeyedList.fromList(value, io.nop.task.model.TaskInputModel::getName);
           
    }

    
    public io.nop.task.model.TaskInputModel getInput(String name){
        return this._inputs.getByKey(name);
    }

    public boolean hasInput(String name){
        return this._inputs.containsKey(name);
    }

    public void addInput(io.nop.task.model.TaskInputModel item) {
        checkAllowChange();
        java.util.List<io.nop.task.model.TaskInputModel> list = this.getInputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.task.model.TaskInputModel::getName);
            setInputs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_inputs(){
        return this._inputs.keySet();
    }

    public boolean hasInputs(){
        return !this._inputs.isEmpty();
    }
    
    /**
     * 
     * xml name: output
     *  
     */
    
    public java.util.List<io.nop.task.model.TaskOutputModel> getOutputs(){
      return _outputs;
    }

    
    public void setOutputs(java.util.List<io.nop.task.model.TaskOutputModel> value){
        checkAllowChange();
        
        this._outputs = KeyedList.fromList(value, io.nop.task.model.TaskOutputModel::getName);
           
    }

    
    public io.nop.task.model.TaskOutputModel getOutput(String name){
        return this._outputs.getByKey(name);
    }

    public boolean hasOutput(String name){
        return this._outputs.containsKey(name);
    }

    public void addOutput(io.nop.task.model.TaskOutputModel item) {
        checkAllowChange();
        java.util.List<io.nop.task.model.TaskOutputModel> list = this.getOutputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.task.model.TaskOutputModel::getName);
            setOutputs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_outputs(){
        return this._outputs.keySet();
    }

    public boolean hasOutputs(){
        return !this._outputs.isEmpty();
    }
    
    /**
     * 
     * xml name: rate-limit
     *  限制对同一个key的调用速率不能超过指定值
     */
    
    public io.nop.task.model.TaskRateLimitModel getRateLimit(){
      return _rateLimit;
    }

    
    public void setRateLimit(io.nop.task.model.TaskRateLimitModel value){
        checkAllowChange();
        
        this._rateLimit = value;
           
    }

    
    /**
     * 
     * xml name: retry
     *  如果发生异常，则重试整个task
     */
    
    public io.nop.task.model.TaskRetryModel getRetry(){
      return _retry;
    }

    
    public void setRetry(io.nop.task.model.TaskRetryModel value){
        checkAllowChange();
        
        this._retry = value;
           
    }

    
    /**
     * 
     * xml name: shareScope
     *  
     */
    
    public boolean isShareScope(){
      return _shareScope;
    }

    
    public void setShareScope(boolean value){
        checkAllowChange();
        
        this._shareScope = value;
           
    }

    
    /**
     * 
     * xml name: throttle
     *  限制对同一个key的调用并发数不能超过指定值
     */
    
    public io.nop.task.model.TaskThrottleModel getThrottle(){
      return _throttle;
    }

    
    public void setThrottle(io.nop.task.model.TaskThrottleModel value){
        checkAllowChange();
        
        this._throttle = value;
           
    }

    
    /**
     * 
     * xml name: timeout
     *  
     */
    
    public long getTimeout(){
      return _timeout;
    }

    
    public void setTimeout(long value){
        checkAllowChange();
        
        this._timeout = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._catch = io.nop.api.core.util.FreezeHelper.deepFreeze(this._catch);
            
           this._decorators = io.nop.api.core.util.FreezeHelper.deepFreeze(this._decorators);
            
           this._finally = io.nop.api.core.util.FreezeHelper.deepFreeze(this._finally);
            
           this._inputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputs);
            
           this._outputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputs);
            
           this._rateLimit = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rateLimit);
            
           this._retry = io.nop.api.core.util.FreezeHelper.deepFreeze(this._retry);
            
           this._throttle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._throttle);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("catch",this.getCatch());
        out.put("decorators",this.getDecorators());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("executor",this.getExecutor());
        out.put("finally",this.getFinally());
        out.put("id",this.getId());
        out.put("inputs",this.getInputs());
        out.put("outputs",this.getOutputs());
        out.put("rateLimit",this.getRateLimit());
        out.put("retry",this.getRetry());
        out.put("shareScope",this.isShareScope());
        out.put("throttle",this.getThrottle());
        out.put("timeout",this.getTimeout());
        out.put("when",this.getWhen());
    }
}
 // resume CPD analysis - CPD-ON
