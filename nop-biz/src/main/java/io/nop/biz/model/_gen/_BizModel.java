package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [9:2:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 每个业务模型(bizModel)必须关联一个对象模型(objMeta)。BizModel作为后端模型，只返回json, 不允许输出文本， 不包含具体界面实现
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actions
     * 
     */
    private KeyedList<io.nop.biz.model.BizActionModel> _actions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: disabledActions
     * 可以强制禁用BizModel(包括Java中定义的方法)中的某些Action。缺省情况下平台会提供大量的操作，有可能从安全角度考虑需要缩小范围
     */
    private java.util.Set<java.lang.String> _disabledActions ;
    
    /**
     *  
     * xml name: inheritActions
     * 如果非空，则只有明确允许的action才对外暴露
     */
    private java.util.Set<java.lang.String> _inheritActions ;
    
    /**
     *  
     * xml name: interceptors
     * 
     */
    private KeyedList<io.nop.biz.model.BizInterceptorModel> _interceptors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: loaders
     * 
     */
    private KeyedList<io.nop.biz.model.BizLoaderModel> _loaders = KeyedList.emptyList();
    
    /**
     *  
     * xml name: metaDir
     * 根据传入的bizType参数，可以在metaDir目录下动态查找关联的objMeta模型。
     */
    private java.lang.String _metaDir ;
    
    /**
     *  
     * xml name: observes
     * 
     */
    private KeyedList<io.nop.biz.model.BizObserveModel> _observes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: state-machine
     * 与XState库的概念基本保持一致。为了简化设计，只支持单一状态表示，不支持并行状态和历史状态。这样状态信息可以作为一个字段存放到数据库中。
     */
    private io.nop.fsm.model.StateMachineModel _stateMachine ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: wfName
     * BizModel可以选择关联一个工作流定义(Workflow)
     */
    private java.lang.String _wfName ;
    
    /**
     * 
     * xml name: actions
     *  
     */
    
    public java.util.List<io.nop.biz.model.BizActionModel> getActions(){
      return _actions;
    }

    
    public void setActions(java.util.List<io.nop.biz.model.BizActionModel> value){
        checkAllowChange();
        
        this._actions = KeyedList.fromList(value, io.nop.biz.model.BizActionModel::getName);
           
    }

    
    public io.nop.biz.model.BizActionModel getAction(String name){
        return this._actions.getByKey(name);
    }

    public boolean hasAction(String name){
        return this._actions.containsKey(name);
    }

    public void addAction(io.nop.biz.model.BizActionModel item) {
        checkAllowChange();
        java.util.List<io.nop.biz.model.BizActionModel> list = this.getActions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.biz.model.BizActionModel::getName);
            setActions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_actions(){
        return this._actions.keySet();
    }

    public boolean hasActions(){
        return !this._actions.isEmpty();
    }
    
    /**
     * 
     * xml name: disabledActions
     *  可以强制禁用BizModel(包括Java中定义的方法)中的某些Action。缺省情况下平台会提供大量的操作，有可能从安全角度考虑需要缩小范围
     */
    
    public java.util.Set<java.lang.String> getDisabledActions(){
      return _disabledActions;
    }

    
    public void setDisabledActions(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._disabledActions = value;
           
    }

    
    /**
     * 
     * xml name: inheritActions
     *  如果非空，则只有明确允许的action才对外暴露
     */
    
    public java.util.Set<java.lang.String> getInheritActions(){
      return _inheritActions;
    }

    
    public void setInheritActions(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._inheritActions = value;
           
    }

    
    /**
     * 
     * xml name: interceptors
     *  
     */
    
    public java.util.List<io.nop.biz.model.BizInterceptorModel> getInterceptors(){
      return _interceptors;
    }

    
    public void setInterceptors(java.util.List<io.nop.biz.model.BizInterceptorModel> value){
        checkAllowChange();
        
        this._interceptors = KeyedList.fromList(value, io.nop.biz.model.BizInterceptorModel::getName);
           
    }

    
    public io.nop.biz.model.BizInterceptorModel getInterceptor(String name){
        return this._interceptors.getByKey(name);
    }

    public boolean hasInterceptor(String name){
        return this._interceptors.containsKey(name);
    }

    public void addInterceptor(io.nop.biz.model.BizInterceptorModel item) {
        checkAllowChange();
        java.util.List<io.nop.biz.model.BizInterceptorModel> list = this.getInterceptors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.biz.model.BizInterceptorModel::getName);
            setInterceptors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_interceptors(){
        return this._interceptors.keySet();
    }

    public boolean hasInterceptors(){
        return !this._interceptors.isEmpty();
    }
    
    /**
     * 
     * xml name: loaders
     *  
     */
    
    public java.util.List<io.nop.biz.model.BizLoaderModel> getLoaders(){
      return _loaders;
    }

    
    public void setLoaders(java.util.List<io.nop.biz.model.BizLoaderModel> value){
        checkAllowChange();
        
        this._loaders = KeyedList.fromList(value, io.nop.biz.model.BizLoaderModel::getName);
           
    }

    
    public io.nop.biz.model.BizLoaderModel getLoader(String name){
        return this._loaders.getByKey(name);
    }

    public boolean hasLoader(String name){
        return this._loaders.containsKey(name);
    }

    public void addLoader(io.nop.biz.model.BizLoaderModel item) {
        checkAllowChange();
        java.util.List<io.nop.biz.model.BizLoaderModel> list = this.getLoaders();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.biz.model.BizLoaderModel::getName);
            setLoaders(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_loaders(){
        return this._loaders.keySet();
    }

    public boolean hasLoaders(){
        return !this._loaders.isEmpty();
    }
    
    /**
     * 
     * xml name: metaDir
     *  根据传入的bizType参数，可以在metaDir目录下动态查找关联的objMeta模型。
     */
    
    public java.lang.String getMetaDir(){
      return _metaDir;
    }

    
    public void setMetaDir(java.lang.String value){
        checkAllowChange();
        
        this._metaDir = value;
           
    }

    
    /**
     * 
     * xml name: observes
     *  
     */
    
    public java.util.List<io.nop.biz.model.BizObserveModel> getObserves(){
      return _observes;
    }

    
    public void setObserves(java.util.List<io.nop.biz.model.BizObserveModel> value){
        checkAllowChange();
        
        this._observes = KeyedList.fromList(value, io.nop.biz.model.BizObserveModel::getId);
           
    }

    
    public io.nop.biz.model.BizObserveModel getObserve(String name){
        return this._observes.getByKey(name);
    }

    public boolean hasObserve(String name){
        return this._observes.containsKey(name);
    }

    public void addObserve(io.nop.biz.model.BizObserveModel item) {
        checkAllowChange();
        java.util.List<io.nop.biz.model.BizObserveModel> list = this.getObserves();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.biz.model.BizObserveModel::getId);
            setObserves(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_observes(){
        return this._observes.keySet();
    }

    public boolean hasObserves(){
        return !this._observes.isEmpty();
    }
    
    /**
     * 
     * xml name: state-machine
     *  与XState库的概念基本保持一致。为了简化设计，只支持单一状态表示，不支持并行状态和历史状态。这样状态信息可以作为一个字段存放到数据库中。
     */
    
    public io.nop.fsm.model.StateMachineModel getStateMachine(){
      return _stateMachine;
    }

    
    public void setStateMachine(io.nop.fsm.model.StateMachineModel value){
        checkAllowChange();
        
        this._stateMachine = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: wfName
     *  BizModel可以选择关联一个工作流定义(Workflow)
     */
    
    public java.lang.String getWfName(){
      return _wfName;
    }

    
    public void setWfName(java.lang.String value){
        checkAllowChange();
        
        this._wfName = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._actions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actions);
            
           this._interceptors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._interceptors);
            
           this._loaders = io.nop.api.core.util.FreezeHelper.deepFreeze(this._loaders);
            
           this._observes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._observes);
            
           this._stateMachine = io.nop.api.core.util.FreezeHelper.deepFreeze(this._stateMachine);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("actions",this.getActions());
        out.putNotNull("disabledActions",this.getDisabledActions());
        out.putNotNull("inheritActions",this.getInheritActions());
        out.putNotNull("interceptors",this.getInterceptors());
        out.putNotNull("loaders",this.getLoaders());
        out.putNotNull("metaDir",this.getMetaDir());
        out.putNotNull("observes",this.getObserves());
        out.putNotNull("stateMachine",this.getStateMachine());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("wfName",this.getWfName());
    }

    public BizModel cloneInstance(){
        BizModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizModel instance){
        super.copyTo(instance);
        
        instance.setActions(this.getActions());
        instance.setDisabledActions(this.getDisabledActions());
        instance.setInheritActions(this.getInheritActions());
        instance.setInterceptors(this.getInterceptors());
        instance.setLoaders(this.getLoaders());
        instance.setMetaDir(this.getMetaDir());
        instance.setObserves(this.getObserves());
        instance.setStateMachine(this.getStateMachine());
        instance.setTagSet(this.getTagSet());
        instance.setWfName(this.getWfName());
    }

    protected BizModel newInstance(){
        return (BizModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
