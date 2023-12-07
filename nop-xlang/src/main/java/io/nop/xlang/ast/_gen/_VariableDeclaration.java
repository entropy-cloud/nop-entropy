//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _VariableDeclaration extends io.nop.xlang.ast.Declaration {
    
    protected java.util.List<io.nop.xlang.ast.VariableDeclarator> declarators;
    
    protected io.nop.xlang.ast.VariableKind kind;
    

    public _VariableDeclaration(){
    }

    
    public java.util.List<io.nop.xlang.ast.VariableDeclarator> getDeclarators(){
        return declarators;
    }

    public void setDeclarators(java.util.List<io.nop.xlang.ast.VariableDeclarator> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.declarators = value;
    }
    
    public java.util.List<io.nop.xlang.ast.VariableDeclarator> makeDeclarators(){
        java.util.List<io.nop.xlang.ast.VariableDeclarator> list = getDeclarators();
        if(list == null){
            list = new java.util.ArrayList<>();
            setDeclarators(list);
        }
        return list;
    }
    
    public io.nop.xlang.ast.VariableKind getKind(){
        return kind;
    }

    public void setKind(io.nop.xlang.ast.VariableKind value){
        checkAllowChange();
        
        this.kind = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("declarators",getDeclarators());
       
          checkMandatory("kind",getKind());
       
    }


    public VariableDeclaration newInstance(){
      return new VariableDeclaration();
    }

    @Override
    public VariableDeclaration deepClone(){
       VariableDeclaration ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(kind != null){
                  
                          ret.setKind(kind);
                      
                }
            
                if(declarators != null){
                  
                          java.util.List<io.nop.xlang.ast.VariableDeclarator> copy_declarators = new java.util.ArrayList<>(declarators.size());
                          for(io.nop.xlang.ast.VariableDeclarator item: declarators){
                              copy_declarators.add(item.deepClone());
                          }
                          ret.setDeclarators(copy_declarators);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(declarators != null){
               for(io.nop.xlang.ast.VariableDeclarator child: declarators){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(declarators != null){
               for(io.nop.xlang.ast.VariableDeclarator child: declarators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.declarators != null){
               int index = this.declarators.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.VariableDeclarator> list = this.replaceInList(this.declarators,index,newChild);
                   this.setDeclarators(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.declarators != null){
               int index = this.declarators.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.VariableDeclarator> list = this.removeInList(this.declarators,index);
                   this.setDeclarators(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    VariableDeclaration other = (VariableDeclaration)node;
    
                if(!isValueEquivalent(this.kind,other.getKind())){
                   return false;
                }
            
            if(isListEquivalent(this.declarators,other.getDeclarators())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.VariableDeclaration;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(kind != null){
                      
                              json.put("kind", kind);
                          
                    }
                
                    if(declarators != null){
                      
                              if(!declarators.isEmpty())
                                json.put("declarators", declarators);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                declarators = io.nop.api.core.util.FreezeHelper.freezeList(declarators,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
