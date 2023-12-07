//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SequenceExpression extends io.nop.xlang.ast.Expression {
    
    protected java.util.List<io.nop.xlang.ast.Expression> expressions;
    

    public _SequenceExpression(){
    }

    
    public java.util.List<io.nop.xlang.ast.Expression> getExpressions(){
        return expressions;
    }

    public void setExpressions(java.util.List<io.nop.xlang.ast.Expression> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.expressions = value;
    }
    
    public java.util.List<io.nop.xlang.ast.Expression> makeExpressions(){
        java.util.List<io.nop.xlang.ast.Expression> list = getExpressions();
        if(list == null){
            list = new java.util.ArrayList<>();
            setExpressions(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expressions",getExpressions());
       
    }


    public SequenceExpression newInstance(){
      return new SequenceExpression();
    }

    @Override
    public SequenceExpression deepClone(){
       SequenceExpression ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expressions != null){
                  
                          java.util.List<io.nop.xlang.ast.Expression> copy_expressions = new java.util.ArrayList<>(expressions.size());
                          for(io.nop.xlang.ast.Expression item: expressions){
                              copy_expressions.add(item.deepClone());
                          }
                          ret.setExpressions(copy_expressions);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(expressions != null){
               for(io.nop.xlang.ast.Expression child: expressions){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(expressions != null){
               for(io.nop.xlang.ast.Expression child: expressions){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.expressions != null){
               int index = this.expressions.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Expression> list = this.replaceInList(this.expressions,index,newChild);
                   this.setExpressions(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.expressions != null){
               int index = this.expressions.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Expression> list = this.removeInList(this.expressions,index);
                   this.setExpressions(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SequenceExpression other = (SequenceExpression)node;
    
            if(isListEquivalent(this.expressions,other.getExpressions())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.SequenceExpression;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expressions != null){
                      
                              if(!expressions.isEmpty())
                                json.put("expressions", expressions);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                expressions = io.nop.api.core.util.FreezeHelper.freezeList(expressions,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
