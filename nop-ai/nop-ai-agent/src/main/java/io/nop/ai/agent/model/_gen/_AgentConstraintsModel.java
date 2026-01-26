package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentConstraintsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentConstraintsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: maxIterations
     * 
     */
    private java.lang.Integer _maxIterations ;
    
    /**
     *  
     * xml name: maxParallelTools
     * 
     */
    private java.lang.Integer _maxParallelTools ;
    
    /**
     *  
     * xml name: tokenCompressionThreashold
     * 
     */
    private java.lang.Double _tokenCompressionThreashold ;
    
    /**
     *  
     * xml name: toolTimeoutSeconds
     * 
     */
    private java.lang.Integer _toolTimeoutSeconds ;
    
    /**
     * 
     * xml name: maxIterations
     *  
     */
    
    public java.lang.Integer getMaxIterations(){
      return _maxIterations;
    }

    
    public void setMaxIterations(java.lang.Integer value){
        checkAllowChange();
        
        this._maxIterations = value;
           
    }

    
    /**
     * 
     * xml name: maxParallelTools
     *  
     */
    
    public java.lang.Integer getMaxParallelTools(){
      return _maxParallelTools;
    }

    
    public void setMaxParallelTools(java.lang.Integer value){
        checkAllowChange();
        
        this._maxParallelTools = value;
           
    }

    
    /**
     * 
     * xml name: tokenCompressionThreashold
     *  
     */
    
    public java.lang.Double getTokenCompressionThreashold(){
      return _tokenCompressionThreashold;
    }

    
    public void setTokenCompressionThreashold(java.lang.Double value){
        checkAllowChange();
        
        this._tokenCompressionThreashold = value;
           
    }

    
    /**
     * 
     * xml name: toolTimeoutSeconds
     *  
     */
    
    public java.lang.Integer getToolTimeoutSeconds(){
      return _toolTimeoutSeconds;
    }

    
    public void setToolTimeoutSeconds(java.lang.Integer value){
        checkAllowChange();
        
        this._toolTimeoutSeconds = value;
           
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
        
        out.putNotNull("maxIterations",this.getMaxIterations());
        out.putNotNull("maxParallelTools",this.getMaxParallelTools());
        out.putNotNull("tokenCompressionThreashold",this.getTokenCompressionThreashold());
        out.putNotNull("toolTimeoutSeconds",this.getToolTimeoutSeconds());
    }

    public AgentConstraintsModel cloneInstance(){
        AgentConstraintsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentConstraintsModel instance){
        super.copyTo(instance);
        
        instance.setMaxIterations(this.getMaxIterations());
        instance.setMaxParallelTools(this.getMaxParallelTools());
        instance.setTokenCompressionThreashold(this.getTokenCompressionThreashold());
        instance.setToolTimeoutSeconds(this.getToolTimeoutSeconds());
    }

    protected AgentConstraintsModel newInstance(){
        return (AgentConstraintsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
