package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptInputParseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptInputParseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: blockEndMarker
     * 
     */
    private java.lang.String _blockEndMarker ;
    
    /**
     *  
     * xml name: blockStartMarker
     * 
     */
    private java.lang.String _blockStartMarker ;
    
    /**
     *  
     * xml name: parseFunction
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parseFunction ;
    
    /**
     * 
     * xml name: blockEndMarker
     *  
     */
    
    public java.lang.String getBlockEndMarker(){
      return _blockEndMarker;
    }

    
    public void setBlockEndMarker(java.lang.String value){
        checkAllowChange();
        
        this._blockEndMarker = value;
           
    }

    
    /**
     * 
     * xml name: blockStartMarker
     *  
     */
    
    public java.lang.String getBlockStartMarker(){
      return _blockStartMarker;
    }

    
    public void setBlockStartMarker(java.lang.String value){
        checkAllowChange();
        
        this._blockStartMarker = value;
           
    }

    
    /**
     * 
     * xml name: parseFunction
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getParseFunction(){
      return _parseFunction;
    }

    
    public void setParseFunction(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._parseFunction = value;
           
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
        
        out.putNotNull("blockEndMarker",this.getBlockEndMarker());
        out.putNotNull("blockStartMarker",this.getBlockStartMarker());
        out.putNotNull("parseFunction",this.getParseFunction());
    }

    public PromptInputParseModel cloneInstance(){
        PromptInputParseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptInputParseModel instance){
        super.copyTo(instance);
        
        instance.setBlockEndMarker(this.getBlockEndMarker());
        instance.setBlockStartMarker(this.getBlockStartMarker());
        instance.setParseFunction(this.getParseFunction());
    }

    protected PromptInputParseModel newInstance(){
        return (PromptInputParseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
