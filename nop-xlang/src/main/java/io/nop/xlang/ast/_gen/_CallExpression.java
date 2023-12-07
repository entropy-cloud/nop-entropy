//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _CallExpression extends io.nop.xlang.ast.OptionalExpression {
    
    protected java.util.List<io.nop.xlang.ast.Expression> arguments;
    
    protected io.nop.xlang.ast.Expression callee;
    
    protected java.lang.String xplLibPath;
    

    public _CallExpression(){
    }

    
    public java.util.List<io.nop.xlang.ast.Expression> getArguments(){
        return arguments;
    }

    public void setArguments(java.util.List<io.nop.xlang.ast.Expression> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.arguments = value;
    }
    
    public java.util.List<io.nop.xlang.ast.Expression> makeArguments(){
        java.util.List<io.nop.xlang.ast.Expression> list = getArguments();
        if(list == null){
            list = new java.util.ArrayList<>();
            setArguments(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.Expression getCallee(){
        return callee;
    }

    public void setCallee(io.nop.xlang.ast.Expression value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.callee = value;
    }
    
    public java.lang.String getXplLibPath(){
        return xplLibPath;
    }

    public void setXplLibPath(java.lang.String value){
        checkAllowChange();
        
        this.xplLibPath = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("arguments",getArguments());
       
          checkMandatory("callee",getCallee());
       
    }


    public CallExpression newInstance(){
      return new CallExpression();
    }

    @Override
    public CallExpression deepClone(){
       CallExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                ret.setOptional(optional);
            
                if(xplLibPath != null){
                  
                          ret.setXplLibPath(xplLibPath);
                      
                }
            
                if(callee != null){
                  
                          ret.setCallee(callee.deepClone());
                      
                }
            
                if(arguments != null){
                  
                          java.util.List<io.nop.xlang.ast.Expression> copy_arguments = new java.util.ArrayList<>(arguments.size());
                          for(io.nop.xlang.ast.Expression item: arguments){
                              copy_arguments.add(item.deepClone());
                          }
                          ret.setArguments(copy_arguments);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(callee != null)
                processor.accept(callee);
        
            if(arguments != null){
               for(io.nop.xlang.ast.Expression child: arguments){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(callee != null && processor.apply(callee) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(arguments != null){
               for(io.nop.xlang.ast.Expression child: arguments){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.callee == oldChild){
               this.setCallee((io.nop.xlang.ast.Expression)newChild);
               return true;
            }
        
            if(this.arguments != null){
               int index = this.arguments.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Expression> list = this.replaceInList(this.arguments,index,newChild);
                   this.setArguments(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.callee == child){
                this.setCallee(null);
                return true;
            }
        
            if(this.arguments != null){
               int index = this.arguments.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Expression> list = this.removeInList(this.arguments,index);
                   this.setArguments(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    CallExpression other = (CallExpression)node;
    
                if(!isValueEquivalent(this.optional,other.getOptional())){
                   return false;
                }
            
                if(!isValueEquivalent(this.xplLibPath,other.getXplLibPath())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.callee,other.getCallee())){
               return false;
            }
        
            if(isListEquivalent(this.arguments,other.getArguments())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.CallExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                   json.put("optional", optional);
                
                    if(xplLibPath != null){
                      
                              json.put("xplLibPath", xplLibPath);
                          
                    }
                
                    if(callee != null){
                      
                              json.put("callee", callee);
                          
                    }
                
                    if(arguments != null){
                      
                              if(!arguments.isEmpty())
                                json.put("arguments", arguments);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(callee != null)
                    callee.freeze(cascade);
                arguments = io.nop.api.core.util.FreezeHelper.freezeList(arguments,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
