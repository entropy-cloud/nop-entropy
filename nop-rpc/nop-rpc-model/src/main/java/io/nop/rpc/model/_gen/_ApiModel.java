package io.nop.rpc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rpc.model.ApiModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: apiPackageName
     * 
     */
    private java.lang.String _apiPackageName ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: dicts
     * 
     */
    private KeyedList<io.nop.api.core.beans.DictBean> _dicts = KeyedList.emptyList();
    
    /**
     *  
     * xml name: domains
     * 
     */
    private KeyedList<io.nop.rpc.model.ApiDomainModel> _domains = KeyedList.emptyList();
    
    /**
     *  
     * xml name: import
     * 
     */
    private KeyedList<io.nop.rpc.model.ApiImportModel> _imports = KeyedList.emptyList();
    
    /**
     *  
     * xml name: messages
     * 
     */
    private KeyedList<io.nop.rpc.model.ApiMessageModel> _messages = KeyedList.emptyList();
    
    /**
     *  
     * xml name: option
     * 
     */
    private KeyedList<io.nop.rpc.model.ApiOptionModel> _options = KeyedList.emptyList();
    
    /**
     *  
     * xml name: services
     * 服务对象
     */
    private KeyedList<io.nop.rpc.model.ApiServiceModel> _services = KeyedList.emptyList();
    
    /**
     * 
     * xml name: apiPackageName
     *  
     */
    
    public java.lang.String getApiPackageName(){
      return _apiPackageName;
    }

    
    public void setApiPackageName(java.lang.String value){
        checkAllowChange();
        
        this._apiPackageName = value;
           
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
     * xml name: dicts
     *  
     */
    
    public java.util.List<io.nop.api.core.beans.DictBean> getDicts(){
      return _dicts;
    }

    
    public void setDicts(java.util.List<io.nop.api.core.beans.DictBean> value){
        checkAllowChange();
        
        this._dicts = KeyedList.fromList(value, io.nop.api.core.beans.DictBean::getName);
           
    }

    
    public io.nop.api.core.beans.DictBean getDict(String name){
        return this._dicts.getByKey(name);
    }

    public boolean hasDict(String name){
        return this._dicts.containsKey(name);
    }

    public void addDict(io.nop.api.core.beans.DictBean item) {
        checkAllowChange();
        java.util.List<io.nop.api.core.beans.DictBean> list = this.getDicts();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.api.core.beans.DictBean::getName);
            setDicts(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_dicts(){
        return this._dicts.keySet();
    }

    public boolean hasDicts(){
        return !this._dicts.isEmpty();
    }
    
    /**
     * 
     * xml name: domains
     *  
     */
    
    public java.util.List<io.nop.rpc.model.ApiDomainModel> getDomains(){
      return _domains;
    }

    
    public void setDomains(java.util.List<io.nop.rpc.model.ApiDomainModel> value){
        checkAllowChange();
        
        this._domains = KeyedList.fromList(value, io.nop.rpc.model.ApiDomainModel::getName);
           
    }

    
    public io.nop.rpc.model.ApiDomainModel getDomain(String name){
        return this._domains.getByKey(name);
    }

    public boolean hasDomain(String name){
        return this._domains.containsKey(name);
    }

    public void addDomain(io.nop.rpc.model.ApiDomainModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiDomainModel> list = this.getDomains();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiDomainModel::getName);
            setDomains(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_domains(){
        return this._domains.keySet();
    }

    public boolean hasDomains(){
        return !this._domains.isEmpty();
    }
    
    /**
     * 
     * xml name: import
     *  
     */
    
    public java.util.List<io.nop.rpc.model.ApiImportModel> getImports(){
      return _imports;
    }

    
    public void setImports(java.util.List<io.nop.rpc.model.ApiImportModel> value){
        checkAllowChange();
        
        this._imports = KeyedList.fromList(value, io.nop.rpc.model.ApiImportModel::getFrom);
           
    }

    
    public io.nop.rpc.model.ApiImportModel getImport(String name){
        return this._imports.getByKey(name);
    }

    public boolean hasImport(String name){
        return this._imports.containsKey(name);
    }

    public void addImport(io.nop.rpc.model.ApiImportModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiImportModel> list = this.getImports();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiImportModel::getFrom);
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
     * xml name: messages
     *  
     */
    
    public java.util.List<io.nop.rpc.model.ApiMessageModel> getMessages(){
      return _messages;
    }

    
    public void setMessages(java.util.List<io.nop.rpc.model.ApiMessageModel> value){
        checkAllowChange();
        
        this._messages = KeyedList.fromList(value, io.nop.rpc.model.ApiMessageModel::getName);
           
    }

    
    public io.nop.rpc.model.ApiMessageModel getMessage(String name){
        return this._messages.getByKey(name);
    }

    public boolean hasMessage(String name){
        return this._messages.containsKey(name);
    }

    public void addMessage(io.nop.rpc.model.ApiMessageModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiMessageModel> list = this.getMessages();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiMessageModel::getName);
            setMessages(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_messages(){
        return this._messages.keySet();
    }

    public boolean hasMessages(){
        return !this._messages.isEmpty();
    }
    
    /**
     * 
     * xml name: option
     *  
     */
    
    public java.util.List<io.nop.rpc.model.ApiOptionModel> getOptions(){
      return _options;
    }

    
    public void setOptions(java.util.List<io.nop.rpc.model.ApiOptionModel> value){
        checkAllowChange();
        
        this._options = KeyedList.fromList(value, io.nop.rpc.model.ApiOptionModel::getName);
           
    }

    
    public io.nop.rpc.model.ApiOptionModel getOption(String name){
        return this._options.getByKey(name);
    }

    public boolean hasOption(String name){
        return this._options.containsKey(name);
    }

    public void addOption(io.nop.rpc.model.ApiOptionModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiOptionModel> list = this.getOptions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiOptionModel::getName);
            setOptions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_options(){
        return this._options.keySet();
    }

    public boolean hasOptions(){
        return !this._options.isEmpty();
    }
    
    /**
     * 
     * xml name: services
     *  服务对象
     */
    
    public java.util.List<io.nop.rpc.model.ApiServiceModel> getServices(){
      return _services;
    }

    
    public void setServices(java.util.List<io.nop.rpc.model.ApiServiceModel> value){
        checkAllowChange();
        
        this._services = KeyedList.fromList(value, io.nop.rpc.model.ApiServiceModel::getName);
           
    }

    
    public io.nop.rpc.model.ApiServiceModel getService(String name){
        return this._services.getByKey(name);
    }

    public boolean hasService(String name){
        return this._services.containsKey(name);
    }

    public void addService(io.nop.rpc.model.ApiServiceModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiServiceModel> list = this.getServices();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiServiceModel::getName);
            setServices(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_services(){
        return this._services.keySet();
    }

    public boolean hasServices(){
        return !this._services.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._dicts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dicts);
            
           this._domains = io.nop.api.core.util.FreezeHelper.deepFreeze(this._domains);
            
           this._imports = io.nop.api.core.util.FreezeHelper.deepFreeze(this._imports);
            
           this._messages = io.nop.api.core.util.FreezeHelper.deepFreeze(this._messages);
            
           this._options = io.nop.api.core.util.FreezeHelper.deepFreeze(this._options);
            
           this._services = io.nop.api.core.util.FreezeHelper.deepFreeze(this._services);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("apiPackageName",this.getApiPackageName());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("dicts",this.getDicts());
        out.putNotNull("domains",this.getDomains());
        out.putNotNull("imports",this.getImports());
        out.putNotNull("messages",this.getMessages());
        out.putNotNull("options",this.getOptions());
        out.putNotNull("services",this.getServices());
    }

    public ApiModel cloneInstance(){
        ApiModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiModel instance){
        super.copyTo(instance);
        
        instance.setApiPackageName(this.getApiPackageName());
        instance.setDescription(this.getDescription());
        instance.setDicts(this.getDicts());
        instance.setDomains(this.getDomains());
        instance.setImports(this.getImports());
        instance.setMessages(this.getMessages());
        instance.setOptions(this.getOptions());
        instance.setServices(this.getServices());
    }

    protected ApiModel newInstance(){
        return (ApiModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
