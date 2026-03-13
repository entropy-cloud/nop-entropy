package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanWriteFileRecord;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanWriteFileRecord extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: lastModified
     * 
     */
    private java.time.LocalDateTime _lastModified ;
    
    /**
     *  
     * xml name: path
     * 
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: summary
     * 
     */
    private java.lang.String _summary ;
    
    /**
     * 
     * xml name: lastModified
     *  
     */
    
    public java.time.LocalDateTime getLastModified(){
      return _lastModified;
    }

    
    public void setLastModified(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._lastModified = value;
           
    }

    
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
     * xml name: summary
     *  
     */
    
    public java.lang.String getSummary(){
      return _summary;
    }

    
    public void setSummary(java.lang.String value){
        checkAllowChange();
        
        this._summary = value;
           
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
        
        out.putNotNull("lastModified",this.getLastModified());
        out.putNotNull("path",this.getPath());
        out.putNotNull("summary",this.getSummary());
    }

    public AgentPlanWriteFileRecord cloneInstance(){
        AgentPlanWriteFileRecord instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanWriteFileRecord instance){
        super.copyTo(instance);
        
        instance.setLastModified(this.getLastModified());
        instance.setPath(this.getPath());
        instance.setSummary(this.getSummary());
    }

    protected AgentPlanWriteFileRecord newInstance(){
        return (AgentPlanWriteFileRecord) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
