package io.nop.ai.agent.tool.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.tool.model.AgentToolModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/tool.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentToolModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * description：工具的详细说明，支持纯文本或CDATA
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: examples
     * example：工具的使用示例，可包含一个或多个具体调用实例
     */
    private java.util.List<io.nop.ai.agent.tool.model.AgentToolExample> _examples = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: schema
     * schema：描述具体工具的调用格式，内容为任意XML（如patch-text-file、manage-todo-list等）
     */
    private io.nop.core.lang.xml.XNode _schema ;
    
    /**
     * 
     * xml name: description
     *  description：工具的详细说明，支持纯文本或CDATA
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
     * xml name: examples
     *  example：工具的使用示例，可包含一个或多个具体调用实例
     */
    
    public java.util.List<io.nop.ai.agent.tool.model.AgentToolExample> getExamples(){
      return _examples;
    }

    
    public void setExamples(java.util.List<io.nop.ai.agent.tool.model.AgentToolExample> value){
        checkAllowChange();
        
        this._examples = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema：描述具体工具的调用格式，内容为任意XML（如patch-text-file、manage-todo-list等）
     */
    
    public io.nop.core.lang.xml.XNode getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.core.lang.xml.XNode value){
        checkAllowChange();
        
        this._schema = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._examples = io.nop.api.core.util.FreezeHelper.deepFreeze(this._examples);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("examples",this.getExamples());
        out.putNotNull("name",this.getName());
        out.putNotNull("schema",this.getSchema());
    }

    public AgentToolModel cloneInstance(){
        AgentToolModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentToolModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setExamples(this.getExamples());
        instance.setName(this.getName());
        instance.setSchema(this.getSchema());
    }

    protected AgentToolModel newInstance(){
        return (AgentToolModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
