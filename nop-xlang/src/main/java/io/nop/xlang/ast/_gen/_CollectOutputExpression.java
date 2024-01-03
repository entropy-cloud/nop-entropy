//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.CollectOutputExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _CollectOutputExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.Expression body;
    
    protected io.nop.xlang.ast.XLangOutputMode outputMode;
    
    protected boolean singleNode;
    

    public _CollectOutputExpression(){
    }

    
    public io.nop.xlang.ast.Expression getBody(){
        return body;
    }

    public void setBody(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.body = value;
    }
    
    public io.nop.xlang.ast.XLangOutputMode getOutputMode(){
        return outputMode;
    }

    public void setOutputMode(io.nop.xlang.ast.XLangOutputMode value){
        checkAllowChange();
        
        this.outputMode = value;
    }
    
    public boolean getSingleNode(){
        return singleNode;
    }

    public void setSingleNode(boolean value){
        checkAllowChange();
        
        this.singleNode = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("body",getBody());
       
          checkMandatory("outputMode",getOutputMode());
       
    }


    public CollectOutputExpression newInstance(){
      return new CollectOutputExpression();
    }

    @Override
    public CollectOutputExpression deepClone(){
       CollectOutputExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setSingleNode(singleNode);
            
                if(outputMode != null){
                  
                          ret.setOutputMode(outputMode);
                      
                }
            
                if(body != null){
                  
                          ret.setBody(body.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(body != null)
                processor.accept(body);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(body != null && processor.apply(body) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.body == oldChild){
               this.setBody((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.body == child){
                this.setBody(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    CollectOutputExpression other = (CollectOutputExpression)node;
    
                if(!isValueEquivalent(this.singleNode,other.getSingleNode())){
                   return false;
                }
            
                if(!isValueEquivalent(this.outputMode,other.getOutputMode())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.body,other.getBody())){
               return false;
            }
        
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.CollectOutputExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("singleNode", singleNode);
                
                    if(outputMode != null){
                      
                              json.put("outputMode", outputMode);
                          
                    }
                
                    if(body != null){
                      
                              json.put("body", body);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(body != null)
                    body.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
