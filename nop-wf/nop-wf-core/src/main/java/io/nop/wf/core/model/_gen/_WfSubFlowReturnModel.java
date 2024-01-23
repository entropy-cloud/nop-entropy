package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfSubFlowReturnModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [272:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 将子工作流中的output变量返回到本工作流中作为变量var
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfSubFlowReturnModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: output
     * 
     */
    private java.lang.String _output ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: var
     * 
     */
    private java.lang.String _var ;
    
    /**
     * 
     * xml name: output
     *  
     */
    
    public java.lang.String getOutput(){
      return _output;
    }

    
    public void setOutput(java.lang.String value){
        checkAllowChange();
        
        this._output = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: var
     *  
     */
    
    public java.lang.String getVar(){
      return _var;
    }

    
    public void setVar(java.lang.String value){
        checkAllowChange();
        
        this._var = value;
           
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
        
        out.putNotNull("output",this.getOutput());
        out.putNotNull("source",this.getSource());
        out.putNotNull("type",this.getType());
        out.putNotNull("var",this.getVar());
    }

    public WfSubFlowReturnModel cloneInstance(){
        WfSubFlowReturnModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfSubFlowReturnModel instance){
        super.copyTo(instance);
        
        instance.setOutput(this.getOutput());
        instance.setSource(this.getSource());
        instance.setType(this.getType());
        instance.setVar(this.getVar());
    }

    protected WfSubFlowReturnModel newInstance(){
        return (WfSubFlowReturnModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
