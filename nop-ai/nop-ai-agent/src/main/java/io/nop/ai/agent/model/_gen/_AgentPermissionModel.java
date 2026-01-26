package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentPermissionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPermissionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: action
     * 
     */
    private java.lang.String _action ;
    
    /**
     *  
     * xml name: resource
     * 
     */
    private java.lang.String _resource ;
    
    /**
     * 
     * xml name: action
     *  
     */
    
    public java.lang.String getAction(){
      return _action;
    }

    
    public void setAction(java.lang.String value){
        checkAllowChange();
        
        this._action = value;
           
    }

    
    /**
     * 
     * xml name: resource
     *  
     */
    
    public java.lang.String getResource(){
      return _resource;
    }

    
    public void setResource(java.lang.String value){
        checkAllowChange();
        
        this._resource = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("action",this.getAction());
        out.putNotNull("resource",this.getResource());
    }

    public AgentPermissionModel cloneInstance(){
        AgentPermissionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPermissionModel instance){
        super.copyTo(instance);
        
        instance.setAction(this.getAction());
        instance.setResource(this.getResource());
    }

    protected AgentPermissionModel newInstance(){
        return (AgentPermissionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
