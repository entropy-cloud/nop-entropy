package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayMessageMappingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * request是ApiRequest类型，response是
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayMessageMappingModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allowHeaders
     * 
     */
    private java.util.Set<java.lang.String> _allowHeaders ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.gateway.model.GatewayMessageHeaderModel> _body = KeyedList.emptyList();
    
    /**
     *  
     * xml name: bodyMapping
     * 对应于IRecordMappingManager管理的mapping模型
     */
    private java.lang.String _bodyMapping ;
    
    /**
     *  
     * xml name: disallowHeaders
     * 
     */
    private java.util.Set<java.lang.String> _disallowHeaders ;
    
    /**
     * 
     * xml name: allowHeaders
     *  
     */
    
    public java.util.Set<java.lang.String> getAllowHeaders(){
      return _allowHeaders;
    }

    
    public void setAllowHeaders(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._allowHeaders = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.gateway.model.GatewayMessageHeaderModel> getBody(){
      return _body;
    }

    
    public void setBody(java.util.List<io.nop.gateway.model.GatewayMessageHeaderModel> value){
        checkAllowChange();
        
        this._body = KeyedList.fromList(value, io.nop.gateway.model.GatewayMessageHeaderModel::getName);
           
    }

    
    public io.nop.gateway.model.GatewayMessageHeaderModel getHeader(String name){
        return this._body.getByKey(name);
    }

    public boolean hasHeader(String name){
        return this._body.containsKey(name);
    }

    public void addHeader(io.nop.gateway.model.GatewayMessageHeaderModel item) {
        checkAllowChange();
        java.util.List<io.nop.gateway.model.GatewayMessageHeaderModel> list = this.getBody();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.gateway.model.GatewayMessageHeaderModel::getName);
            setBody(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_body(){
        return this._body.keySet();
    }

    public boolean hasBody(){
        return !this._body.isEmpty();
    }
    
    /**
     * 
     * xml name: bodyMapping
     *  对应于IRecordMappingManager管理的mapping模型
     */
    
    public java.lang.String getBodyMapping(){
      return _bodyMapping;
    }

    
    public void setBodyMapping(java.lang.String value){
        checkAllowChange();
        
        this._bodyMapping = value;
           
    }

    
    /**
     * 
     * xml name: disallowHeaders
     *  
     */
    
    public java.util.Set<java.lang.String> getDisallowHeaders(){
      return _disallowHeaders;
    }

    
    public void setDisallowHeaders(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._disallowHeaders = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._body = io.nop.api.core.util.FreezeHelper.deepFreeze(this._body);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("allowHeaders",this.getAllowHeaders());
        out.putNotNull("body",this.getBody());
        out.putNotNull("bodyMapping",this.getBodyMapping());
        out.putNotNull("disallowHeaders",this.getDisallowHeaders());
    }

    public GatewayMessageMappingModel cloneInstance(){
        GatewayMessageMappingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayMessageMappingModel instance){
        super.copyTo(instance);
        
        instance.setAllowHeaders(this.getAllowHeaders());
        instance.setBody(this.getBody());
        instance.setBodyMapping(this.getBodyMapping());
        instance.setDisallowHeaders(this.getDisallowHeaders());
    }

    protected GatewayMessageMappingModel newInstance(){
        return (GatewayMessageMappingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
