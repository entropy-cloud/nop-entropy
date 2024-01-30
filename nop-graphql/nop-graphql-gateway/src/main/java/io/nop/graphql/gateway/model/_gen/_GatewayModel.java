package io.nop.graphql.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.graphql.gateway.model.GatewayModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [1:2:0:0]/nop/schema/gateway.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: routes
     * 
     */
    private KeyedList<io.nop.graphql.gateway.model.GatewayRouteModel> _routes = KeyedList.emptyList();
    
    /**
     * 
     * xml name: routes
     *  
     */
    
    public java.util.List<io.nop.graphql.gateway.model.GatewayRouteModel> getRoutes(){
      return _routes;
    }

    
    public void setRoutes(java.util.List<io.nop.graphql.gateway.model.GatewayRouteModel> value){
        checkAllowChange();
        
        this._routes = KeyedList.fromList(value, io.nop.graphql.gateway.model.GatewayRouteModel::getId);
           
    }

    
    public io.nop.graphql.gateway.model.GatewayRouteModel getRoute(String name){
        return this._routes.getByKey(name);
    }

    public boolean hasRoute(String name){
        return this._routes.containsKey(name);
    }

    public void addRoute(io.nop.graphql.gateway.model.GatewayRouteModel item) {
        checkAllowChange();
        java.util.List<io.nop.graphql.gateway.model.GatewayRouteModel> list = this.getRoutes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.graphql.gateway.model.GatewayRouteModel::getId);
            setRoutes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_routes(){
        return this._routes.keySet();
    }

    public boolean hasRoutes(){
        return !this._routes.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._routes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._routes);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("routes",this.getRoutes());
    }

    public GatewayModel cloneInstance(){
        GatewayModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayModel instance){
        super.copyTo(instance);
        
        instance.setRoutes(this.getRoutes());
    }

    protected GatewayModel newInstance(){
        return (GatewayModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
