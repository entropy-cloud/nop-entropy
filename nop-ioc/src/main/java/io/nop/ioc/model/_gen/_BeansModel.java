package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeansModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [8:2:0:0]/nop/schema/beans.xdef <p>
 * 带ioc前缀的属性和子节点是相对于spring配置格式增加的扩展
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeansModel extends io.nop.xlang.xdsl.AbstractDslModel {
    
    /**
     *  
     * xml name: alias
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanAliasModel> _aliases = KeyedList.emptyList();
    
    /**
     *  
     * xml name: bean
     * 指定parent属性时，从parent对应的bean继承配置。但是class/primary/abstract/autowire-candidate/lazy-init/depends-on等属性不会被继承
     */
    private KeyedList<io.nop.ioc.model.BeanModel> _beans = KeyedList.emptyList();
    
    /**
     *  
     * xml name: default-lazy-init
     * 
     */
    private boolean _defaultLazyInit  = false;
    
    /**
     *  
     * xml name: import
     * 多次import同一资源只会实际执行一次。所有的bean不允许重名，从而避免出现import顺序不同导致结果不同。
     */
    private KeyedList<io.nop.ioc.model.BeanImportModel> _imports = KeyedList.emptyList();
    
    /**
     *  
     * xml name: ioc:config
     * 指定parent属性时，从parent对应的bean继承配置。但是class/primary/abstract/autowire-candidate/lazy-init/depends-on等属性不会被继承
     */
    private KeyedList<io.nop.ioc.model.BeanConfigModel> _iocConfigs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: ioc:listener
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanListenerModel> _iocListeners = KeyedList.emptyList();
    
    /**
     *  
     * xml name: ioc:security-domain
     * 
     */
    private java.util.Set<java.lang.String> _iocSecurityDomain ;
    
    /**
     *  
     * xml name: util:constant
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanConstantModel> _utilConstants = KeyedList.emptyList();
    
    /**
     *  
     * xml name: util:list
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanListModel> _utilLists = KeyedList.emptyList();
    
    /**
     *  
     * xml name: util:map
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanMapModel> _utilMaps = KeyedList.emptyList();
    
    /**
     *  
     * xml name: util:set
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanSetModel> _utilSets = KeyedList.emptyList();
    
    /**
     * 
     * xml name: alias
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanAliasModel> getAliases(){
      return _aliases;
    }

    
    public void setAliases(java.util.List<io.nop.ioc.model.BeanAliasModel> value){
        checkAllowChange();
        
        this._aliases = KeyedList.fromList(value, io.nop.ioc.model.BeanAliasModel::getName);
           
    }

    
    public io.nop.ioc.model.BeanAliasModel getAlias(String name){
        return this._aliases.getByKey(name);
    }

    public boolean hasAlias(String name){
        return this._aliases.containsKey(name);
    }

    public void addAlias(io.nop.ioc.model.BeanAliasModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanAliasModel> list = this.getAliases();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanAliasModel::getName);
            setAliases(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_aliases(){
        return this._aliases.keySet();
    }

    public boolean hasAliases(){
        return !this._aliases.isEmpty();
    }
    
    /**
     * 
     * xml name: bean
     *  指定parent属性时，从parent对应的bean继承配置。但是class/primary/abstract/autowire-candidate/lazy-init/depends-on等属性不会被继承
     */
    
    public java.util.List<io.nop.ioc.model.BeanModel> getBeans(){
      return _beans;
    }

    
    public void setBeans(java.util.List<io.nop.ioc.model.BeanModel> value){
        checkAllowChange();
        
        this._beans = KeyedList.fromList(value, io.nop.ioc.model.BeanModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanModel getBean(String name){
        return this._beans.getByKey(name);
    }

    public boolean hasBean(String name){
        return this._beans.containsKey(name);
    }

    public void addBean(io.nop.ioc.model.BeanModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanModel> list = this.getBeans();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanModel::getId);
            setBeans(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_beans(){
        return this._beans.keySet();
    }

    public boolean hasBeans(){
        return !this._beans.isEmpty();
    }
    
    /**
     * 
     * xml name: default-lazy-init
     *  
     */
    
    public boolean isDefaultLazyInit(){
      return _defaultLazyInit;
    }

    
    public void setDefaultLazyInit(boolean value){
        checkAllowChange();
        
        this._defaultLazyInit = value;
           
    }

    
    /**
     * 
     * xml name: import
     *  多次import同一资源只会实际执行一次。所有的bean不允许重名，从而避免出现import顺序不同导致结果不同。
     */
    
    public java.util.List<io.nop.ioc.model.BeanImportModel> getImports(){
      return _imports;
    }

    
    public void setImports(java.util.List<io.nop.ioc.model.BeanImportModel> value){
        checkAllowChange();
        
        this._imports = KeyedList.fromList(value, io.nop.ioc.model.BeanImportModel::getResource);
           
    }

    
    public io.nop.ioc.model.BeanImportModel getImport(String name){
        return this._imports.getByKey(name);
    }

    public boolean hasImport(String name){
        return this._imports.containsKey(name);
    }

    public void addImport(io.nop.ioc.model.BeanImportModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanImportModel> list = this.getImports();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanImportModel::getResource);
            setImports(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_imports(){
        return this._imports.keySet();
    }

    public boolean hasImports(){
        return !this._imports.isEmpty();
    }
    
    /**
     * 
     * xml name: ioc:config
     *  指定parent属性时，从parent对应的bean继承配置。但是class/primary/abstract/autowire-candidate/lazy-init/depends-on等属性不会被继承
     */
    
    public java.util.List<io.nop.ioc.model.BeanConfigModel> getIocConfigs(){
      return _iocConfigs;
    }

    
    public void setIocConfigs(java.util.List<io.nop.ioc.model.BeanConfigModel> value){
        checkAllowChange();
        
        this._iocConfigs = KeyedList.fromList(value, io.nop.ioc.model.BeanConfigModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanConfigModel getIocConfig(String name){
        return this._iocConfigs.getByKey(name);
    }

    public boolean hasIocConfig(String name){
        return this._iocConfigs.containsKey(name);
    }

    public void addIocConfig(io.nop.ioc.model.BeanConfigModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanConfigModel> list = this.getIocConfigs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanConfigModel::getId);
            setIocConfigs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_iocConfigs(){
        return this._iocConfigs.keySet();
    }

    public boolean hasIocConfigs(){
        return !this._iocConfigs.isEmpty();
    }
    
    /**
     * 
     * xml name: ioc:listener
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanListenerModel> getIocListeners(){
      return _iocListeners;
    }

    
    public void setIocListeners(java.util.List<io.nop.ioc.model.BeanListenerModel> value){
        checkAllowChange();
        
        this._iocListeners = KeyedList.fromList(value, io.nop.ioc.model.BeanListenerModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanListenerModel getIocListener(String name){
        return this._iocListeners.getByKey(name);
    }

    public boolean hasIocListener(String name){
        return this._iocListeners.containsKey(name);
    }

    public void addIocListener(io.nop.ioc.model.BeanListenerModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanListenerModel> list = this.getIocListeners();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanListenerModel::getId);
            setIocListeners(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_iocListeners(){
        return this._iocListeners.keySet();
    }

    public boolean hasIocListeners(){
        return !this._iocListeners.isEmpty();
    }
    
    /**
     * 
     * xml name: ioc:security-domain
     *  
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
     * xml name: util:constant
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanConstantModel> getUtilConstants(){
      return _utilConstants;
    }

    
    public void setUtilConstants(java.util.List<io.nop.ioc.model.BeanConstantModel> value){
        checkAllowChange();
        
        this._utilConstants = KeyedList.fromList(value, io.nop.ioc.model.BeanConstantModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanConstantModel getUtilConstant(String name){
        return this._utilConstants.getByKey(name);
    }

    public boolean hasUtilConstant(String name){
        return this._utilConstants.containsKey(name);
    }

    public void addUtilConstant(io.nop.ioc.model.BeanConstantModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanConstantModel> list = this.getUtilConstants();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanConstantModel::getId);
            setUtilConstants(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_utilConstants(){
        return this._utilConstants.keySet();
    }

    public boolean hasUtilConstants(){
        return !this._utilConstants.isEmpty();
    }
    
    /**
     * 
     * xml name: util:list
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanListModel> getUtilLists(){
      return _utilLists;
    }

    
    public void setUtilLists(java.util.List<io.nop.ioc.model.BeanListModel> value){
        checkAllowChange();
        
        this._utilLists = KeyedList.fromList(value, io.nop.ioc.model.BeanListModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanListModel getUtilList(String name){
        return this._utilLists.getByKey(name);
    }

    public boolean hasUtilList(String name){
        return this._utilLists.containsKey(name);
    }

    public void addUtilList(io.nop.ioc.model.BeanListModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanListModel> list = this.getUtilLists();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanListModel::getId);
            setUtilLists(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_utilLists(){
        return this._utilLists.keySet();
    }

    public boolean hasUtilLists(){
        return !this._utilLists.isEmpty();
    }
    
    /**
     * 
     * xml name: util:map
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanMapModel> getUtilMaps(){
      return _utilMaps;
    }

    
    public void setUtilMaps(java.util.List<io.nop.ioc.model.BeanMapModel> value){
        checkAllowChange();
        
        this._utilMaps = KeyedList.fromList(value, io.nop.ioc.model.BeanMapModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanMapModel getEntry(String name){
        return this._utilMaps.getByKey(name);
    }

    public boolean hasEntry(String name){
        return this._utilMaps.containsKey(name);
    }

    public void addEntry(io.nop.ioc.model.BeanMapModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanMapModel> list = this.getUtilMaps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanMapModel::getId);
            setUtilMaps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_utilMaps(){
        return this._utilMaps.keySet();
    }

    public boolean hasUtilMaps(){
        return !this._utilMaps.isEmpty();
    }
    
    /**
     * 
     * xml name: util:set
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanSetModel> getUtilSets(){
      return _utilSets;
    }

    
    public void setUtilSets(java.util.List<io.nop.ioc.model.BeanSetModel> value){
        checkAllowChange();
        
        this._utilSets = KeyedList.fromList(value, io.nop.ioc.model.BeanSetModel::getId);
           
    }

    
    public io.nop.ioc.model.BeanSetModel getUtilSet(String name){
        return this._utilSets.getByKey(name);
    }

    public boolean hasUtilSet(String name){
        return this._utilSets.containsKey(name);
    }

    public void addUtilSet(io.nop.ioc.model.BeanSetModel item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanSetModel> list = this.getUtilSets();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanSetModel::getId);
            setUtilSets(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_utilSets(){
        return this._utilSets.keySet();
    }

    public boolean hasUtilSets(){
        return !this._utilSets.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._aliases = io.nop.api.core.util.FreezeHelper.deepFreeze(this._aliases);
            
           this._beans = io.nop.api.core.util.FreezeHelper.deepFreeze(this._beans);
            
           this._imports = io.nop.api.core.util.FreezeHelper.deepFreeze(this._imports);
            
           this._iocConfigs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocConfigs);
            
           this._iocListeners = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocListeners);
            
           this._utilConstants = io.nop.api.core.util.FreezeHelper.deepFreeze(this._utilConstants);
            
           this._utilLists = io.nop.api.core.util.FreezeHelper.deepFreeze(this._utilLists);
            
           this._utilMaps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._utilMaps);
            
           this._utilSets = io.nop.api.core.util.FreezeHelper.deepFreeze(this._utilSets);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("aliases",this.getAliases());
        out.put("beans",this.getBeans());
        out.put("defaultLazyInit",this.isDefaultLazyInit());
        out.put("imports",this.getImports());
        out.put("iocConfigs",this.getIocConfigs());
        out.put("iocListeners",this.getIocListeners());
        out.put("iocSecurityDomain",this.getIocSecurityDomain());
        out.put("utilConstants",this.getUtilConstants());
        out.put("utilLists",this.getUtilLists());
        out.put("utilMaps",this.getUtilMaps());
        out.put("utilSets",this.getUtilSets());
    }

    public BeansModel cloneInstance(){
        BeansModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeansModel instance){
        super.copyTo(instance);
        
        instance.setAliases(this.getAliases());
        instance.setBeans(this.getBeans());
        instance.setDefaultLazyInit(this.isDefaultLazyInit());
        instance.setImports(this.getImports());
        instance.setIocConfigs(this.getIocConfigs());
        instance.setIocListeners(this.getIocListeners());
        instance.setIocSecurityDomain(this.getIocSecurityDomain());
        instance.setUtilConstants(this.getUtilConstants());
        instance.setUtilLists(this.getUtilLists());
        instance.setUtilMaps(this.getUtilMaps());
        instance.setUtilSets(this.getUtilSets());
    }

    protected BeansModel newInstance(){
        return (BeansModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
