package io.nop.ai.agent.tool.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.tool.model.ToolCallOutputFile;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/call-tools-response.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ToolCallOutputFile extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: path
     * 
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: totalLines
     * 
     */
    private java.lang.Integer _totalLines ;
    
    /**
     * 
     * xml name: description
     *  
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
     * xml name: totalLines
     *  
     */
    
    public java.lang.Integer getTotalLines(){
      return _totalLines;
    }

    
    public void setTotalLines(java.lang.Integer value){
        checkAllowChange();
        
        this._totalLines = value;
           
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
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("path",this.getPath());
        out.putNotNull("totalLines",this.getTotalLines());
    }

    public ToolCallOutputFile cloneInstance(){
        ToolCallOutputFile instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ToolCallOutputFile instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setPath(this.getPath());
        instance.setTotalLines(this.getTotalLines());
    }

    protected ToolCallOutputFile newInstance(){
        return (ToolCallOutputFile) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
