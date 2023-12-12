package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [116:6:0:0]/nop/schema/beans.xdef <p>
 * 指定parent属性时，从parent对应的bean继承配置。但是class/primary/abstract/autowire-candidate/lazy-init/depends-on等属性不会被继承
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
public abstract class _BeanValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: autowire
     * 
     */
    private io.nop.ioc.model.AutowireType _autowire ;
    
    /**
     *  
     * xml name: autowire-candidate
     * 设置为false时，这个bean不被纳入按照类型进行自动编配的候选集合。
     */
    private boolean _autowireCandidate  = true;
    
    /**
     *  
     * xml name: class
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: constructor-arg
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanConstructorArgModel> _constructorArgs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: depends-on
     * 
     */
    private java.util.Set<java.lang.String> _dependsOn ;
    
    /**
     *  
     * xml name: destroy-method
     * 
     */
    private java.lang.String _destroyMethod ;
    
    /**
     *  
     * xml name: factory-bean
     * 
     */
    private java.lang.String _factoryBean ;
    
    /**
     *  
     * xml name: factory-method
     * 在spring中factory-method为class上的静态方法，取代普通的构造函数。如果定义了factory-method,
     * 则constructor-arg就是factory-method的参数.
     */
    private java.lang.String _factoryMethod ;
    
    /**
     *  
     * xml name: init-method
     * 
     */
    private java.lang.String _initMethod ;
    
    /**
     *  
     * xml name: ioc:after
     * 在这些bean创建之后立刻创建当前的bean
     */
    private java.util.Set<java.lang.String> _iocAfter ;
    
    /**
     *  
     * xml name: ioc:aop
     * 指定了ioc:aop=true的bean才会成为AOP pointcut的目标。
     */
    private boolean _iocAop  = true;
    
    /**
     *  
     * xml name: ioc:auto-refresh
     * 
     */
    private boolean _iocAutoRefresh  = false;
    
    /**
     *  
     * xml name: ioc:bean-method
     * 
     */
    private java.lang.String _iocBeanMethod ;
    
    /**
     *  
     * xml name: ioc:before
     * 在创建这些bean之前需要先创建当前的bean。类似于depends-on设置，只是它在被依赖方设置
     */
    private java.util.Set<java.lang.String> _iocBefore ;
    
    /**
     *  
     * xml name: ioc:build
     * 将xml属性直接映射到bean属性，支持嵌套结构
     */
    private io.nop.ioc.model.BeanBuildModel _iocBuild ;
    
    /**
     *  
     * xml name: ioc:condition
     * 满足条件时bean才允许实例化
     */
    private io.nop.ioc.model.BeanConditionModel _iocCondition ;
    
    /**
     *  
     * xml name: ioc:config-prefix
     * 
     */
    private java.lang.String _iocConfigPrefix ;
    
    /**
     *  
     * xml name: ioc:delay-method
     * 
     */
    private java.lang.String _iocDelayMethod ;
    
    /**
     *  
     * xml name: ioc:delay-start
     * 
     */
    private io.nop.xlang.api.EvalCode _iocDelayStart ;
    
    /**
     *  
     * xml name: ioc:destroy
     * 
     */
    private io.nop.xlang.api.EvalCode _iocDestroy ;
    
    /**
     *  
     * xml name: ioc:force-init
     * 在START_LAZY启动模式下即使设置了lazy-init=false，缺省也不会自动启动。但是如果设置了ioc:force-init，则强制新建。
     */
    private boolean _iocForceInit  = false;
    
    /**
     *  
     * xml name: ioc:init
     * 
     */
    private io.nop.xlang.api.EvalCode _iocInit ;
    
    /**
     *  
     * xml name: ioc:init-order
     * 指定初始化顺序。容器初始化时对于bean按照order顺序初始化，销毁的时候按照反向顺序进行销毁。同样order的bean按照依赖顺序进行初始化
     */
    private int _iocInitOrder  = 100;
    
    /**
     *  
     * xml name: ioc:interceptor
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanInterceptorModel> _iocInterceptors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: ioc:on-config-refresh
     * 
     */
    private io.nop.ioc.model.BeanOnConfigRefresh _iocOnConfigRefresh ;
    
    /**
     *  
     * xml name: ioc:proxy
     * 如果为true，则要求ioc:type不为空。会利用Java的DynamicProxy机制将当前beanInstance包装成指定接口类型。
     * 当前的beanInstance要求是InvocationHandler类型。
     */
    private boolean _iocProxy  = false;
    
    /**
     *  
     * xml name: ioc:refresh-config
     * 
     */
    private io.nop.xlang.api.EvalCode _iocRefreshConfig ;
    
    /**
     *  
     * xml name: ioc:refresh-config-method
     * 
     */
    private java.lang.String _iocRefreshConfigMethod ;
    
    /**
     *  
     * xml name: ioc:restart
     * 
     */
    private io.nop.xlang.api.EvalCode _iocRestart ;
    
    /**
     *  
     * xml name: ioc:restart-method
     * 
     */
    private java.lang.String _iocRestartMethod ;
    
    /**
     *  
     * xml name: ioc:security-domain
     * 格式为逗号分隔的字符串，设置bean的安全域。如果xlib具有安全域设置，则安全域必须匹配才能调用bean。
     */
    private java.util.Set<java.lang.String> _iocSecurityDomain ;
    
    /**
     *  
     * xml name: ioc:type
     * 指定返回bean的类型，按类型autowire时将使用这里的类型设置。如果不指定，则根据factory-method和class等属性推导
     */
    private java.util.Set<java.lang.String> _iocType ;
    
    /**
     *  
     * xml name: lazy-init
     * 
     */
    private java.lang.Boolean _lazyInit ;
    
    /**
     *  
     * xml name: parent
     * 
     */
    private java.lang.String _parent ;
    
    /**
     *  
     * xml name: property
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanPropertyModel> _properties = KeyedList.emptyList();
    
    /**
     *  
     * xml name: scope
     * 
     */
    private java.lang.String _scope ;
    
    /**
     * 
     * xml name: autowire
     *  
     */
    
    public io.nop.ioc.model.AutowireType getAutowire(){
      return _autowire;
    }

    
    public void setAutowire(io.nop.ioc.model.AutowireType value){
        checkAllowChange();
        
        this._autowire = value;
           
    }

    
    /**
     * 
     * xml name: autowire-candidate
     *  设置为false时，这个bean不被纳入按照类型进行自动编配的候选集合。
     */
    
    public boolean isAutowireCandidate(){
      return _autowireCandidate;
    }

    
    public void setAutowireCandidate(boolean value){
        checkAllowChange();
        
        this._autowireCandidate = value;
           
    }

    
    /**
     * 
     * xml name: class
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: constructor-arg
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanConstructorArgModel> getConstructorArgs(){
      return _constructorArgs;
    }

    
    public void setConstructorArgs(java.util.List<io.nop.ioc.model.BeanConstructorArgModel> value){
        checkAllowChange();
        
        this._constructorArgs = KeyedList.fromList(value, io.nop.ioc.model.BeanConstructorArgModel::getIndex);
           
    }

    
    public io.nop.ioc.model.BeanConstructorArgModel getConstructorArg(String name){
        return this._constructorArgs.getByKey(name);
    }

    public boolean hasConstructorArg(String name){
        return this._constructorArgs.containsKey(name);
    }

    public void addConstructorArg(io.nop.ioc.model.BeanConstructorArgModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanConstructorArgModel> list = this.getConstructorArgs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanConstructorArgModel::getIndex);
            setConstructorArgs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_constructorArgs(){
        return this._constructorArgs.keySet();
    }

    public boolean hasConstructorArgs(){
        return !this._constructorArgs.isEmpty();
    }
    
    /**
     * 
     * xml name: depends-on
     *  
     */
    
    public java.util.Set<java.lang.String> getDependsOn(){
      return _dependsOn;
    }

    
    public void setDependsOn(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._dependsOn = value;
           
    }

    
    /**
     * 
     * xml name: destroy-method
     *  
     */
    
    public java.lang.String getDestroyMethod(){
      return _destroyMethod;
    }

    
    public void setDestroyMethod(java.lang.String value){
        checkAllowChange();
        
        this._destroyMethod = value;
           
    }

    
    /**
     * 
     * xml name: factory-bean
     *  
     */
    
    public java.lang.String getFactoryBean(){
      return _factoryBean;
    }

    
    public void setFactoryBean(java.lang.String value){
        checkAllowChange();
        
        this._factoryBean = value;
           
    }

    
    /**
     * 
     * xml name: factory-method
     *  在spring中factory-method为class上的静态方法，取代普通的构造函数。如果定义了factory-method,
     * 则constructor-arg就是factory-method的参数.
     */
    
    public java.lang.String getFactoryMethod(){
      return _factoryMethod;
    }

    
    public void setFactoryMethod(java.lang.String value){
        checkAllowChange();
        
        this._factoryMethod = value;
           
    }

    
    /**
     * 
     * xml name: init-method
     *  
     */
    
    public java.lang.String getInitMethod(){
      return _initMethod;
    }

    
    public void setInitMethod(java.lang.String value){
        checkAllowChange();
        
        this._initMethod = value;
           
    }

    
    /**
     * 
     * xml name: ioc:after
     *  在这些bean创建之后立刻创建当前的bean
     */
    
    public java.util.Set<java.lang.String> getIocAfter(){
      return _iocAfter;
    }

    
    public void setIocAfter(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._iocAfter = value;
           
    }

    
    /**
     * 
     * xml name: ioc:aop
     *  指定了ioc:aop=true的bean才会成为AOP pointcut的目标。
     */
    
    public boolean isIocAop(){
      return _iocAop;
    }

    
    public void setIocAop(boolean value){
        checkAllowChange();
        
        this._iocAop = value;
           
    }

    
    /**
     * 
     * xml name: ioc:auto-refresh
     *  
     */
    
    public boolean isIocAutoRefresh(){
      return _iocAutoRefresh;
    }

    
    public void setIocAutoRefresh(boolean value){
        checkAllowChange();
        
        this._iocAutoRefresh = value;
           
    }

    
    /**
     * 
     * xml name: ioc:bean-method
     *  
     */
    
    public java.lang.String getIocBeanMethod(){
      return _iocBeanMethod;
    }

    
    public void setIocBeanMethod(java.lang.String value){
        checkAllowChange();
        
        this._iocBeanMethod = value;
           
    }

    
    /**
     * 
     * xml name: ioc:before
     *  在创建这些bean之前需要先创建当前的bean。类似于depends-on设置，只是它在被依赖方设置
     */
    
    public java.util.Set<java.lang.String> getIocBefore(){
      return _iocBefore;
    }

    
    public void setIocBefore(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._iocBefore = value;
           
    }

    
    /**
     * 
     * xml name: ioc:build
     *  将xml属性直接映射到bean属性，支持嵌套结构
     */
    
    public io.nop.ioc.model.BeanBuildModel getIocBuild(){
      return _iocBuild;
    }

    
    public void setIocBuild(io.nop.ioc.model.BeanBuildModel value){
        checkAllowChange();
        
        this._iocBuild = value;
           
    }

    
    /**
     * 
     * xml name: ioc:condition
     *  满足条件时bean才允许实例化
     */
    
    public io.nop.ioc.model.BeanConditionModel getIocCondition(){
      return _iocCondition;
    }

    
    public void setIocCondition(io.nop.ioc.model.BeanConditionModel value){
        checkAllowChange();
        
        this._iocCondition = value;
           
    }

    
    /**
     * 
     * xml name: ioc:config-prefix
     *  
     */
    
    public java.lang.String getIocConfigPrefix(){
      return _iocConfigPrefix;
    }

    
    public void setIocConfigPrefix(java.lang.String value){
        checkAllowChange();
        
        this._iocConfigPrefix = value;
           
    }

    
    /**
     * 
     * xml name: ioc:delay-method
     *  
     */
    
    public java.lang.String getIocDelayMethod(){
      return _iocDelayMethod;
    }

    
    public void setIocDelayMethod(java.lang.String value){
        checkAllowChange();
        
        this._iocDelayMethod = value;
           
    }

    
    /**
     * 
     * xml name: ioc:delay-start
     *  
     */
    
    public io.nop.xlang.api.EvalCode getIocDelayStart(){
      return _iocDelayStart;
    }

    
    public void setIocDelayStart(io.nop.xlang.api.EvalCode value){
        checkAllowChange();
        
        this._iocDelayStart = value;
           
    }

    
    /**
     * 
     * xml name: ioc:destroy
     *  
     */
    
    public io.nop.xlang.api.EvalCode getIocDestroy(){
      return _iocDestroy;
    }

    
    public void setIocDestroy(io.nop.xlang.api.EvalCode value){
        checkAllowChange();
        
        this._iocDestroy = value;
           
    }

    
    /**
     * 
     * xml name: ioc:force-init
     *  在START_LAZY启动模式下即使设置了lazy-init=false，缺省也不会自动启动。但是如果设置了ioc:force-init，则强制新建。
     */
    
    public boolean isIocForceInit(){
      return _iocForceInit;
    }

    
    public void setIocForceInit(boolean value){
        checkAllowChange();
        
        this._iocForceInit = value;
           
    }

    
    /**
     * 
     * xml name: ioc:init
     *  
     */
    
    public io.nop.xlang.api.EvalCode getIocInit(){
      return _iocInit;
    }

    
    public void setIocInit(io.nop.xlang.api.EvalCode value){
        checkAllowChange();
        
        this._iocInit = value;
           
    }

    
    /**
     * 
     * xml name: ioc:init-order
     *  指定初始化顺序。容器初始化时对于bean按照order顺序初始化，销毁的时候按照反向顺序进行销毁。同样order的bean按照依赖顺序进行初始化
     */
    
    public int getIocInitOrder(){
      return _iocInitOrder;
    }

    
    public void setIocInitOrder(int value){
        checkAllowChange();
        
        this._iocInitOrder = value;
           
    }

    
    /**
     * 
     * xml name: ioc:interceptor
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanInterceptorModel> getIocInterceptors(){
      return _iocInterceptors;
    }

    
    public void setIocInterceptors(java.util.List<io.nop.ioc.model.BeanInterceptorModel> value){
        checkAllowChange();
        
        this._iocInterceptors = KeyedList.fromList(value, io.nop.ioc.model.BeanInterceptorModel::getBean);
           
    }

    
    public io.nop.ioc.model.BeanInterceptorModel getIocInterceptor(String name){
        return this._iocInterceptors.getByKey(name);
    }

    public boolean hasIocInterceptor(String name){
        return this._iocInterceptors.containsKey(name);
    }

    public void addIocInterceptor(io.nop.ioc.model.BeanInterceptorModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanInterceptorModel> list = this.getIocInterceptors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanInterceptorModel::getBean);
            setIocInterceptors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_iocInterceptors(){
        return this._iocInterceptors.keySet();
    }

    public boolean hasIocInterceptors(){
        return !this._iocInterceptors.isEmpty();
    }
    
    /**
     * 
     * xml name: ioc:on-config-refresh
     *  
     */
    
    public io.nop.ioc.model.BeanOnConfigRefresh getIocOnConfigRefresh(){
      return _iocOnConfigRefresh;
    }

    
    public void setIocOnConfigRefresh(io.nop.ioc.model.BeanOnConfigRefresh value){
        checkAllowChange();
        
        this._iocOnConfigRefresh = value;
           
    }

    
    /**
     * 
     * xml name: ioc:proxy
     *  如果为true，则要求ioc:type不为空。会利用Java的DynamicProxy机制将当前beanInstance包装成指定接口类型。
     * 当前的beanInstance要求是InvocationHandler类型。
     */
    
    public boolean isIocProxy(){
      return _iocProxy;
    }

    
    public void setIocProxy(boolean value){
        checkAllowChange();
        
        this._iocProxy = value;
           
    }

    
    /**
     * 
     * xml name: ioc:refresh-config
     *  
     */
    
    public io.nop.xlang.api.EvalCode getIocRefreshConfig(){
      return _iocRefreshConfig;
    }

    
    public void setIocRefreshConfig(io.nop.xlang.api.EvalCode value){
        checkAllowChange();
        
        this._iocRefreshConfig = value;
           
    }

    
    /**
     * 
     * xml name: ioc:refresh-config-method
     *  
     */
    
    public java.lang.String getIocRefreshConfigMethod(){
      return _iocRefreshConfigMethod;
    }

    
    public void setIocRefreshConfigMethod(java.lang.String value){
        checkAllowChange();
        
        this._iocRefreshConfigMethod = value;
           
    }

    
    /**
     * 
     * xml name: ioc:restart
     *  
     */
    
    public io.nop.xlang.api.EvalCode getIocRestart(){
      return _iocRestart;
    }

    
    public void setIocRestart(io.nop.xlang.api.EvalCode value){
        checkAllowChange();
        
        this._iocRestart = value;
           
    }

    
    /**
     * 
     * xml name: ioc:restart-method
     *  
     */
    
    public java.lang.String getIocRestartMethod(){
      return _iocRestartMethod;
    }

    
    public void setIocRestartMethod(java.lang.String value){
        checkAllowChange();
        
        this._iocRestartMethod = value;
           
    }

    
    /**
     * 
     * xml name: ioc:security-domain
     *  格式为逗号分隔的字符串，设置bean的安全域。如果xlib具有安全域设置，则安全域必须匹配才能调用bean。
     */
    
    public java.util.Set<java.lang.String> getIocSecurityDomain(){
      return _iocSecurityDomain;
    }

    
    public void setIocSecurityDomain(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._iocSecurityDomain = value;
           
    }

    
    /**
     * 
     * xml name: ioc:type
     *  指定返回bean的类型，按类型autowire时将使用这里的类型设置。如果不指定，则根据factory-method和class等属性推导
     */
    
    public java.util.Set<java.lang.String> getIocType(){
      return _iocType;
    }

    
    public void setIocType(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._iocType = value;
           
    }

    
    /**
     * 
     * xml name: lazy-init
     *  
     */
    
    public java.lang.Boolean getLazyInit(){
      return _lazyInit;
    }

    
    public void setLazyInit(java.lang.Boolean value){
        checkAllowChange();
        
        this._lazyInit = value;
           
    }

    
    /**
     * 
     * xml name: parent
     *  
     */
    
    public java.lang.String getParent(){
      return _parent;
    }

    
    public void setParent(java.lang.String value){
        checkAllowChange();
        
        this._parent = value;
           
    }

    
    /**
     * 
     * xml name: property
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanPropertyModel> getProperties(){
      return _properties;
    }

    
    public void setProperties(java.util.List<io.nop.ioc.model.BeanPropertyModel> value){
        checkAllowChange();
        
        this._properties = KeyedList.fromList(value, io.nop.ioc.model.BeanPropertyModel::getName);
           
    }

    
    public io.nop.ioc.model.BeanPropertyModel getProperty(String name){
        return this._properties.getByKey(name);
    }

    public boolean hasProperty(String name){
        return this._properties.containsKey(name);
    }

    public void addProperty(io.nop.ioc.model.BeanPropertyModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanPropertyModel> list = this.getProperties();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanPropertyModel::getName);
            setProperties(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_properties(){
        return this._properties.keySet();
    }

    public boolean hasProperties(){
        return !this._properties.isEmpty();
    }
    
    /**
     * 
     * xml name: scope
     *  
     */
    
    public java.lang.String getScope(){
      return _scope;
    }

    
    public void setScope(java.lang.String value){
        checkAllowChange();
        
        this._scope = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._constructorArgs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._constructorArgs);
            
           this._iocBuild = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocBuild);
            
           this._iocCondition = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocCondition);
            
           this._iocInterceptors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocInterceptors);
            
           this._iocOnConfigRefresh = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocOnConfigRefresh);
            
           this._properties = io.nop.api.core.util.FreezeHelper.deepFreeze(this._properties);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("autowire",this.getAutowire());
        out.put("autowireCandidate",this.isAutowireCandidate());
        out.put("className",this.getClassName());
        out.put("constructorArgs",this.getConstructorArgs());
        out.put("dependsOn",this.getDependsOn());
        out.put("destroyMethod",this.getDestroyMethod());
        out.put("factoryBean",this.getFactoryBean());
        out.put("factoryMethod",this.getFactoryMethod());
        out.put("initMethod",this.getInitMethod());
        out.put("iocAfter",this.getIocAfter());
        out.put("iocAop",this.isIocAop());
        out.put("iocAutoRefresh",this.isIocAutoRefresh());
        out.put("iocBeanMethod",this.getIocBeanMethod());
        out.put("iocBefore",this.getIocBefore());
        out.put("iocBuild",this.getIocBuild());
        out.put("iocCondition",this.getIocCondition());
        out.put("iocConfigPrefix",this.getIocConfigPrefix());
        out.put("iocDelayMethod",this.getIocDelayMethod());
        out.put("iocDelayStart",this.getIocDelayStart());
        out.put("iocDestroy",this.getIocDestroy());
        out.put("iocForceInit",this.isIocForceInit());
        out.put("iocInit",this.getIocInit());
        out.put("iocInitOrder",this.getIocInitOrder());
        out.put("iocInterceptors",this.getIocInterceptors());
        out.put("iocOnConfigRefresh",this.getIocOnConfigRefresh());
        out.put("iocProxy",this.isIocProxy());
        out.put("iocRefreshConfig",this.getIocRefreshConfig());
        out.put("iocRefreshConfigMethod",this.getIocRefreshConfigMethod());
        out.put("iocRestart",this.getIocRestart());
        out.put("iocRestartMethod",this.getIocRestartMethod());
        out.put("iocSecurityDomain",this.getIocSecurityDomain());
        out.put("iocType",this.getIocType());
        out.put("lazyInit",this.getLazyInit());
        out.put("parent",this.getParent());
        out.put("properties",this.getProperties());
        out.put("scope",this.getScope());
    }
}
 // resume CPD analysis - CPD-ON
