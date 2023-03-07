//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _NewExpression extends io.nop.xlang.ast.Expression {
    
    protected java.util.List<io.nop.xlang.ast.Expression> arguments;
    
    protected io.nop.xlang.ast.NamedTypeNode callee;
    

    public _NewExpression(){
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
    
    public io.nop.xlang.ast.NamedTypeNode getCallee(){
        return callee;
    }

    public void setCallee(io.nop.xlang.ast.NamedTypeNode value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.callee = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("callee",getCallee());
       
    }


    public NewExpression newInstance(){
      return new NewExpression();
    }

    @Override
    public NewExpression deepClone(){
       NewExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
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
               this.setCallee((io.nop.xlang.ast.NamedTypeNode)newChild);
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
    NewExpression other = (NewExpression)node;
    
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
       return XLangASTKind.NewExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
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
