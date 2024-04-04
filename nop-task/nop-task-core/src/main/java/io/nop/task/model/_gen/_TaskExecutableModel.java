package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskExecutableModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [19:6:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskExecutableModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: catch
     * 捕获异常后如果不抛出异常，则认为是成功执行，会执行output返回
     */
    private io.nop.core.lang.eval.IEvalAction _catch ;
    
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
    private io.nop.core.lang.eval.IEvalAction _finally ;
    
    /**
     *  
     * xml name: flags
     * 可以根据动态设置的flag来决定是否执行本步骤
     */
    private io.nop.task.model.TaskFlagsModel _flags ;
    
    /**
     *  
     * xml name: input
     * 
     */
    private KeyedList<io.nop.task.model.TaskInputModel> _inputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: onReload
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _onReload ;
    
    /**
     *  
     * xml name: output
     * 
     */
    private KeyedList<io.nop.task.model.TaskOutputModel> _outputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: persisVars
     * 
     */
    private java.util.Set<java.lang.String> _persisVars ;
    
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
     * xml name: returnType
     * 
     */
    private io.nop.core.type.IGenericType _returnType ;
    
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
     * xml name: validator
     * 验证输入数据
     */
    private io.nop.core.model.validator.ValidatorModel _validator ;
    
    /**
     *  
     * xml name: when
     * 不满足条件的时候将会自动跳过本步骤
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: catch
     *  捕获异常后如果不抛出异常，则认为是成功执行，会执行output返回
     */
    
    public io.nop.core.lang.eval.IEvalAction getCatch(){
      return _catch;
    }

    
    public void setCatch(io.nop.core.lang.eval.IEvalAction value){
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
        
        this._decorators = KeyedList.fromList(value, io.nop.task.model.TaskDecoratorModel::getName);
           
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
            list = new KeyedList<>(io.nop.task.model.TaskDecoratorModel::getName);
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
    
    public io.nop.core.lang.eval.IEvalAction getFinally(){
      return _finally;
    }

    
    public void setFinally(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._finally = value;
           
    }

    
    /**
     * 
     * xml name: flags
     *  可以根据动态设置的flag来决定是否执行本步骤
     */
    
    public io.nop.task.model.TaskFlagsModel getFlags(){
      return _flags;
    }

    
    public void setFlags(io.nop.task.model.TaskFlagsModel value){
        checkAllowChange();
        
        this._flags = value;
           
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
     * xml name: onReload
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnReload(){
      return _onReload;
    }

    
    public void setOnReload(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onReload = value;
           
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
     * xml name: persisVars
     *  
     */
    
    public java.util.Set<java.lang.String> getPersisVars(){
      return _persisVars;
    }

    
    public void setPersisVars(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._persisVars = value;
           
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
     * xml name: returnType
     *  
     */
    
    public io.nop.core.type.IGenericType getReturnType(){
      return _returnType;
    }

    
    public void setReturnType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._returnType = value;
           
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
     * xml name: validator
     *  验证输入数据
     */
    
    public io.nop.core.model.validator.ValidatorModel getValidator(){
      return _validator;
    }

    
    public void setValidator(io.nop.core.model.validator.ValidatorModel value){
        checkAllowChange();
        
        this._validator = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  不满足条件的时候将会自动跳过本步骤
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
        
           this._decorators = io.nop.api.core.util.FreezeHelper.deepFreeze(this._decorators);
            
           this._flags = io.nop.api.core.util.FreezeHelper.deepFreeze(this._flags);
            
           this._inputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputs);
            
           this._outputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputs);
            
           this._rateLimit = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rateLimit);
            
           this._retry = io.nop.api.core.util.FreezeHelper.deepFreeze(this._retry);
            
           this._throttle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._throttle);
            
           this._validator = io.nop.api.core.util.FreezeHelper.deepFreeze(this._validator);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("catch",this.getCatch());
        out.putNotNull("decorators",this.getDecorators());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("executor",this.getExecutor());
        out.putNotNull("finally",this.getFinally());
        out.putNotNull("flags",this.getFlags());
        out.putNotNull("inputs",this.getInputs());
        out.putNotNull("onReload",this.getOnReload());
        out.putNotNull("outputs",this.getOutputs());
        out.putNotNull("persisVars",this.getPersisVars());
        out.putNotNull("rateLimit",this.getRateLimit());
        out.putNotNull("retry",this.getRetry());
        out.putNotNull("returnType",this.getReturnType());
        out.putNotNull("throttle",this.getThrottle());
        out.putNotNull("timeout",this.getTimeout());
        out.putNotNull("validator",this.getValidator());
        out.putNotNull("when",this.getWhen());
    }

    public TaskExecutableModel cloneInstance(){
        TaskExecutableModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskExecutableModel instance){
        super.copyTo(instance);
        
        instance.setCatch(this.getCatch());
        instance.setDecorators(this.getDecorators());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setExecutor(this.getExecutor());
        instance.setFinally(this.getFinally());
        instance.setFlags(this.getFlags());
        instance.setInputs(this.getInputs());
        instance.setOnReload(this.getOnReload());
        instance.setOutputs(this.getOutputs());
        instance.setPersisVars(this.getPersisVars());
        instance.setRateLimit(this.getRateLimit());
        instance.setRetry(this.getRetry());
        instance.setReturnType(this.getReturnType());
        instance.setThrottle(this.getThrottle());
        instance.setTimeout(this.getTimeout());
        instance.setValidator(this.getValidator());
        instance.setWhen(this.getWhen());
    }

    protected TaskExecutableModel newInstance(){
        return (TaskExecutableModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
