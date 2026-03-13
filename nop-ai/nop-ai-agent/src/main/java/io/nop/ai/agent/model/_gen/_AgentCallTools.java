package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentCallTools;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/call-tools.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentCallTools extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.ai.agent.tool.model.AgentToolCall> _body = KeyedList.emptyList();
    
    /**
     *  
     * xml name: maxConcurrency
     * 
     */
    private java.lang.Integer _maxConcurrency ;
    
    /**
     *  
     * xml name: paralllel
     * 
     */
    private java.lang.Boolean _paralllel  = true;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.ai.agent.tool.model.AgentToolCall> getBody(){
      return _body;
    }

    
    public void setBody(java.util.List<io.nop.ai.agent.tool.model.AgentToolCall> value){
        checkAllowChange();
        
        this._body = KeyedList.fromList(value, io.nop.ai.agent.tool.model.AgentToolCall::getId);
           
    }

    
    public java.util.Set<String> keySet_body(){
        return this._body.keySet();
    }

    public boolean hasBody(){
        return !this._body.isEmpty();
    }
    
    /**
     * 
     * xml name: maxConcurrency
     *  
     */
    
    public java.lang.Integer getMaxConcurrency(){
      return _maxConcurrency;
    }

    
    public void setMaxConcurrency(java.lang.Integer value){
        checkAllowChange();
        
        this._maxConcurrency = value;
           
    }

    
    /**
     * 
     * xml name: paralllel
     *  
     */
    
    public java.lang.Boolean getParalllel(){
      return _paralllel;
    }

    
    public void setParalllel(java.lang.Boolean value){
        checkAllowChange();
        
        this._paralllel = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("maxConcurrency",this.getMaxConcurrency());
        out.putNotNull("paralllel",this.getParalllel());
    }

    public AgentCallTools cloneInstance(){
        AgentCallTools instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentCallTools instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setMaxConcurrency(this.getMaxConcurrency());
        instance.setParalllel(this.getParalllel());
    }

    protected AgentCallTools newInstance(){
        return (AgentCallTools) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
