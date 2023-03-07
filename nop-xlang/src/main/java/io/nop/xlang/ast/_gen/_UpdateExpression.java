//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _UpdateExpression extends io.nop.xlang.ast.Expression {
    
    protected io.nop.xlang.ast.Expression argument;
    
    protected io.nop.xlang.ast.XLangOperator operator;
    
    protected boolean prefix;
    

    public _UpdateExpression(){
    }

    
    public io.nop.xlang.ast.Expression getArgument(){
        return argument;
    }

    public void setArgument(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.argument = value;
    }
    
    public io.nop.xlang.ast.XLangOperator getOperator(){
        return operator;
    }

    public void setOperator(io.nop.xlang.ast.XLangOperator value){
        checkAllowChange();
        
        this.operator = value;
    }
    
    public boolean getPrefix(){
        return prefix;
    }

    public void setPrefix(boolean value){
        checkAllowChange();
        
        this.prefix = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("argument",getArgument());
       
          checkMandatory("operator",getOperator());
       
    }


    public UpdateExpression newInstance(){
      return new UpdateExpression();
    }

    @Override
    public UpdateExpression deepClone(){
       UpdateExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(operator != null){
                  
                          ret.setOperator(operator);
                      
                }
            
                if(argument != null){
                  
                          ret.setArgument(argument.deepClone());
                      
                }
            
                ret.setPrefix(prefix);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(argument != null)
                processor.accept(argument);
        
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(argument != null && processor.apply(argument) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.argument == oldChild){
               this.setArgument((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.argument == child){
                this.setArgument(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    UpdateExpression other = (UpdateExpression)node;
    
                if(!isValueEquivalent(this.operator,other.getOperator())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.argument,other.getArgument())){
               return false;
            }
        
                if(!isValueEquivalent(this.prefix,other.getPrefix())){
                   return false;
                }
            
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.UpdateExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(operator != null){
                      
                              json.put("operator", operator);
                          
                    }
                
                    if(argument != null){
                      
                              json.put("argument", argument);
                          
                    }
                
                   json.put("prefix", prefix);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(argument != null)
                    argument.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
