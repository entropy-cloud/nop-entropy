//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlRegularFunction;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlRegularFunction extends io.nop.orm.eql.ast.SqlFunction {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlExpr> args;
    
    protected java.lang.String name;
    

    public _SqlRegularFunction(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlExpr> getArgs(){
        return args;
    }

    public void setArgs(java.util.List<io.nop.orm.eql.ast.SqlExpr> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.args = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlExpr> makeArgs(){
        java.util.List<io.nop.orm.eql.ast.SqlExpr> list = getArgs();
        if(list == null){
            list = new java.util.ArrayList<>();
            setArgs(list);
        }
        return list;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
    }


    public SqlRegularFunction newInstance(){
      return new SqlRegularFunction();
    }

    @Override
    public SqlRegularFunction deepClone(){
       SqlRegularFunction ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(args != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlExpr> copy_args = new java.util.ArrayList<>(args.size());
                          for(io.nop.orm.eql.ast.SqlExpr item: args){
                              copy_args.add(item.deepClone());
                          }
                          ret.setArgs(copy_args);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(args != null){
               for(io.nop.orm.eql.ast.SqlExpr child: args){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(args != null){
               for(io.nop.orm.eql.ast.SqlExpr child: args){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.args != null){
               int index = this.args.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlExpr> list = this.replaceInList(this.args,index,newChild);
                   this.setArgs(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.args != null){
               int index = this.args.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlExpr> list = this.removeInList(this.args,index);
                   this.setArgs(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlRegularFunction other = (SqlRegularFunction)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(isListEquivalent(this.args,other.getArgs())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlRegularFunction;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(args != null){
                      
                              if(!args.isEmpty())
                                json.put("args", args);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                args = io.nop.api.core.util.FreezeHelper.freezeList(args,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
