package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanReadFileRecord;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanReadFileRecord extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: path
     * 
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: purpose
     * 
     */
    private java.lang.String _purpose ;
    
    /**
     *  
     * xml name: readAt
     * 
     */
    private java.time.LocalDateTime _readAt ;
    
    /**
     * 
     * xml name: path
     *  
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
    }

    
    /**
     * 
     * xml name: purpose
     *  
     */
    
    public java.lang.String getPurpose(){
      return _purpose;
    }

    
    public void setPurpose(java.lang.String value){
        checkAllowChange();
        
        this._purpose = value;
           
    }

    
    /**
     * 
     * xml name: readAt
     *  
     */
    
    public java.time.LocalDateTime getReadAt(){
      return _readAt;
    }

    
    public void setReadAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._readAt = value;
           
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
        
        out.putNotNull("path",this.getPath());
        out.putNotNull("purpose",this.getPurpose());
        out.putNotNull("readAt",this.getReadAt());
    }

    public AgentPlanReadFileRecord cloneInstance(){
        AgentPlanReadFileRecord instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanReadFileRecord instance){
        super.copyTo(instance);
        
        instance.setPath(this.getPath());
        instance.setPurpose(this.getPurpose());
        instance.setReadAt(this.getReadAt());
    }

    protected AgentPlanReadFileRecord newInstance(){
        return (AgentPlanReadFileRecord) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
