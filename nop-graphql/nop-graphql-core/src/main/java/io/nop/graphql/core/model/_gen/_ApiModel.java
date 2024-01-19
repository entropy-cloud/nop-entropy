package io.nop.graphql.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.graphql.core.model.ApiModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [3:2:0:0]/nop/schema/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
    private KeyedList<io.nop.graphql.core.model.ApiDomainModel> _domains = KeyedList.emptyList();
    
    /**
     *  
     * xml name: messages
     * 
     */
    private KeyedList<io.nop.graphql.core.model.ApiMessageModel> _messages = KeyedList.emptyList();
    
    /**
     *  
     * xml name: services
     * 服务对象
     */
    private KeyedList<io.nop.graphql.core.model.ApiServiceModel> _services = KeyedList.emptyList();
    
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
    
    public java.util.List<io.nop.graphql.core.model.ApiDomainModel> getDomains(){
      return _domains;
    }

    
    public void setDomains(java.util.List<io.nop.graphql.core.model.ApiDomainModel> value){
        checkAllowChange();
        
        this._domains = KeyedList.fromList(value, io.nop.graphql.core.model.ApiDomainModel::getName);
           
    }

    
    public io.nop.graphql.core.model.ApiDomainModel getDomain(String name){
        return this._domains.getByKey(name);
    }

    public boolean hasDomain(String name){
        return this._domains.containsKey(name);
    }

    public void addDomain(io.nop.graphql.core.model.ApiDomainModel item) {
        checkAllowChange();
        java.util.List<io.nop.graphql.core.model.ApiDomainModel> list = this.getDomains();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.graphql.core.model.ApiDomainModel::getName);
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
     * xml name: messages
     *  
     */
    
    public java.util.List<io.nop.graphql.core.model.ApiMessageModel> getMessages(){
      return _messages;
    }

    
    public void setMessages(java.util.List<io.nop.graphql.core.model.ApiMessageModel> value){
        checkAllowChange();
        
        this._messages = KeyedList.fromList(value, io.nop.graphql.core.model.ApiMessageModel::getName);
           
    }

    
    public io.nop.graphql.core.model.ApiMessageModel getMessage(String name){
        return this._messages.getByKey(name);
    }

    public boolean hasMessage(String name){
        return this._messages.containsKey(name);
    }

    public void addMessage(io.nop.graphql.core.model.ApiMessageModel item) {
        checkAllowChange();
        java.util.List<io.nop.graphql.core.model.ApiMessageModel> list = this.getMessages();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.graphql.core.model.ApiMessageModel::getName);
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
     * xml name: services
     *  服务对象
     */
    
    public java.util.List<io.nop.graphql.core.model.ApiServiceModel> getServices(){
      return _services;
    }

    
    public void setServices(java.util.List<io.nop.graphql.core.model.ApiServiceModel> value){
        checkAllowChange();
        
        this._services = KeyedList.fromList(value, io.nop.graphql.core.model.ApiServiceModel::getName);
           
    }

    
    public io.nop.graphql.core.model.ApiServiceModel getService(String name){
        return this._services.getByKey(name);
    }

    public boolean hasService(String name){
        return this._services.containsKey(name);
    }

    public void addService(io.nop.graphql.core.model.ApiServiceModel item) {
        checkAllowChange();
        java.util.List<io.nop.graphql.core.model.ApiServiceModel> list = this.getServices();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.graphql.core.model.ApiServiceModel::getName);
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
            
           this._messages = io.nop.api.core.util.FreezeHelper.deepFreeze(this._messages);
            
           this._services = io.nop.api.core.util.FreezeHelper.deepFreeze(this._services);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("dicts",this.getDicts());
        out.put("domains",this.getDomains());
        out.put("messages",this.getMessages());
        out.put("services",this.getServices());
    }

    public ApiModel cloneInstance(){
        ApiModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiModel instance){
        super.copyTo(instance);
        
        instance.setDicts(this.getDicts());
        instance.setDomains(this.getDomains());
        instance.setMessages(this.getMessages());
        instance.setServices(this.getServices());
    }

    protected ApiModel newInstance(){
        return (ApiModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
