//__XGEN_FORCE_OVERRIDE__
package io.nop.xlang.ast._gen;

import io.nop.xlang.ast.CompilationUnit;
import io.nop.xlang.ast.XLangASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.xlang.ast.XLangASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _CompilationUnit extends XLangASTNode {
    
    protected java.lang.String packageName;
    
    protected java.util.List<io.nop.xlang.ast.Statement> statements;
    

    public _CompilationUnit(){
    }

    
    public java.lang.String getPackageName(){
        return packageName;
    }

    public void setPackageName(java.lang.String value){
        checkAllowChange();
        
        this.packageName = value;
    }
    
    public java.util.List<io.nop.xlang.ast.Statement> getStatements(){
        return statements;
    }

    public void setStatements(java.util.List<io.nop.xlang.ast.Statement> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((XLangASTNode)this));
                }
            
        this.statements = value;
    }
    
    public java.util.List<io.nop.xlang.ast.Statement> makeStatements(){
        java.util.List<io.nop.xlang.ast.Statement> list = getStatements();
        if(list == null){
            list = new java.util.ArrayList<>();
            setStatements(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("packageName",getPackageName());
       
    }


    public CompilationUnit newInstance(){
      return new CompilationUnit();
    }

    @Override
    public CompilationUnit deepClone(){
       CompilationUnit ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(packageName != null){
                  
                          ret.setPackageName(packageName);
                      
                }
            
                if(statements != null){
                  
                          java.util.List<io.nop.xlang.ast.Statement> copy_statements = new java.util.ArrayList<>(statements.size());
                          for(io.nop.xlang.ast.Statement item: statements){
                              copy_statements.add(item.deepClone());
                          }
                          ret.setStatements(copy_statements);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<XLangASTNode> processor){
    
            if(statements != null){
               for(io.nop.xlang.ast.Statement child: statements){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<XLangASTNode,ProcessResult> processor){
    
            if(statements != null){
               for(io.nop.xlang.ast.Statement child: statements){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(XLangASTNode oldChild, XLangASTNode newChild){
    
            if(this.statements != null){
               int index = this.statements.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Statement> list = this.replaceInList(this.statements,index,newChild);
                   this.setStatements(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(XLangASTNode child){
    
            if(this.statements != null){
               int index = this.statements.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.xlang.ast.Statement> list = this.removeInList(this.statements,index);
                   this.setStatements(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(XLangASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    CompilationUnit other = (CompilationUnit)node;
    
                if(!isValueEquivalent(this.packageName,other.getPackageName())){
                   return false;
                }
            
            if(isListEquivalent(this.statements,other.getStatements())){
               return false;
            }
        return true;
    }

    @Override
    public XLangASTKind getASTKind(){
       return XLangASTKind.CompilationUnit;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(packageName != null){
                      
                              json.put("packageName", packageName);
                          
                    }
                
                    if(statements != null){
                      
                              if(!statements.isEmpty())
                                json.put("statements", statements);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                statements = io.nop.api.core.util.FreezeHelper.freezeList(statements,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
