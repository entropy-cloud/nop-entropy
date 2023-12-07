//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlProgram extends EqlASTNode {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlStatement> statements;
    

    public _SqlProgram(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlStatement> getStatements(){
        return statements;
    }

    public void setStatements(java.util.List<io.nop.orm.eql.ast.SqlStatement> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.statements = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlStatement> makeStatements(){
        java.util.List<io.nop.orm.eql.ast.SqlStatement> list = getStatements();
        if(list == null){
            list = new java.util.ArrayList<>();
            setStatements(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("statements",getStatements());
       
    }


    public SqlProgram newInstance(){
      return new SqlProgram();
    }

    @Override
    public SqlProgram deepClone(){
       SqlProgram ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(statements != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlStatement> copy_statements = new java.util.ArrayList<>(statements.size());
                          for(io.nop.orm.eql.ast.SqlStatement item: statements){
                              copy_statements.add(item.deepClone());
                          }
                          ret.setStatements(copy_statements);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(statements != null){
               for(io.nop.orm.eql.ast.SqlStatement child: statements){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(statements != null){
               for(io.nop.orm.eql.ast.SqlStatement child: statements){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.statements != null){
               int index = this.statements.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlStatement> list = this.replaceInList(this.statements,index,newChild);
                   this.setStatements(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.statements != null){
               int index = this.statements.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlStatement> list = this.removeInList(this.statements,index);
                   this.setStatements(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlProgram other = (SqlProgram)node;
    
            if(isListEquivalent(this.statements,other.getStatements())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlProgram;
    }

    protected void serializeFields(IJsonHandler json) {
        
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
