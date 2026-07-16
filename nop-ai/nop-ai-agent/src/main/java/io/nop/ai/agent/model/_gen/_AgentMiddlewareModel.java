package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentMiddlewareModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentMiddlewareModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: impl
     * 
     */
    private java.lang.String _impl ;
    
    /**
     *  
     * xml name: point
     * 
     */
    private java.lang.String _point ;
    
    /**
     * 
     * xml name: impl
     *  
     */
    
    public java.lang.String getImpl(){
      return _impl;
    }

    
    public void setImpl(java.lang.String value){
        checkAllowChange();
        
        this._impl = value;
           
    }

    
    /**
     * 
     * xml name: point
     *  
     */
    
    public java.lang.String getPoint(){
      return _point;
    }

    
    public void setPoint(java.lang.String value){
        checkAllowChange();
        
        this._point = value;
           
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
        
        out.putNotNull("impl",this.getImpl());
        out.putNotNull("point",this.getPoint());
    }

    public AgentMiddlewareModel cloneInstance(){
        AgentMiddlewareModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentMiddlewareModel instance){
        super.copyTo(instance);
        
        instance.setImpl(this.getImpl());
        instance.setPoint(this.getPoint());
    }

    protected AgentMiddlewareModel newInstance(){
        return (AgentMiddlewareModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
