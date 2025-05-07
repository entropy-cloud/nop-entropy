package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptOutputParseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 没有指定format的情况下才会使用parseFromResponse配置
 * 如果指定了source，则执行代码来解析变量。如果没有指定source，但是指定了startMarker和endMarker，则从响应消息中截取相关信息。
 * 如果以上配置都没有，但是配置了contains，则只要响应消息中包含此字符串，就设置为true。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptOutputParseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: containsText
     * 
     */
    private java.lang.String _containsText ;
    
    /**
     *  
     * xml name: includeEndMarker
     * 
     */
    private boolean _includeEndMarker  = false;
    
    /**
     *  
     * xml name: includeStartMarker
     * 如果为true，则将startMarker包含在解析结果中
     */
    private boolean _includeStartMarker  = false;
    
    /**
     *  
     * xml name: parseFunction
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parseFunction ;
    
    /**
     *  
     * xml name: startMarkerOptional
     * 如果为true，则允许响应消息中没有startMarker，此时认为startMarker在消息的最前方
     */
    private boolean _startMarkerOptional  = false;
    
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
     * xml name: containsText
     *  
     */
    
    public java.lang.String getContainsText(){
      return _containsText;
    }

    
    public void setContainsText(java.lang.String value){
        checkAllowChange();
        
        this._containsText = value;
           
    }

    
    /**
     * 
     * xml name: includeEndMarker
     *  
     */
    
    public boolean isIncludeEndMarker(){
      return _includeEndMarker;
    }

    
    public void setIncludeEndMarker(boolean value){
        checkAllowChange();
        
        this._includeEndMarker = value;
           
    }

    
    /**
     * 
     * xml name: includeStartMarker
     *  如果为true，则将startMarker包含在解析结果中
     */
    
    public boolean isIncludeStartMarker(){
      return _includeStartMarker;
    }

    
    public void setIncludeStartMarker(boolean value){
        checkAllowChange();
        
        this._includeStartMarker = value;
           
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

    
    /**
     * 
     * xml name: startMarkerOptional
     *  如果为true，则允许响应消息中没有startMarker，此时认为startMarker在消息的最前方
     */
    
    public boolean isStartMarkerOptional(){
      return _startMarkerOptional;
    }

    
    public void setStartMarkerOptional(boolean value){
        checkAllowChange();
        
        this._startMarkerOptional = value;
           
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
        out.putNotNull("containsText",this.getContainsText());
        out.putNotNull("includeEndMarker",this.isIncludeEndMarker());
        out.putNotNull("includeStartMarker",this.isIncludeStartMarker());
        out.putNotNull("parseFunction",this.getParseFunction());
        out.putNotNull("startMarkerOptional",this.isStartMarkerOptional());
    }

    public PromptOutputParseModel cloneInstance(){
        PromptOutputParseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptOutputParseModel instance){
        super.copyTo(instance);
        
        instance.setBlockEndMarker(this.getBlockEndMarker());
        instance.setBlockStartMarker(this.getBlockStartMarker());
        instance.setContainsText(this.getContainsText());
        instance.setIncludeEndMarker(this.isIncludeEndMarker());
        instance.setIncludeStartMarker(this.isIncludeStartMarker());
        instance.setParseFunction(this.getParseFunction());
        instance.setStartMarkerOptional(this.isStartMarkerOptional());
    }

    protected PromptOutputParseModel newInstance(){
        return (PromptOutputParseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
