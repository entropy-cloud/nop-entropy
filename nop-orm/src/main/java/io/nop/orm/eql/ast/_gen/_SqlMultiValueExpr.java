//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlMultiValueExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlMultiValueExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlExpr> values;
    

    public _SqlMultiValueExpr(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlExpr> getValues(){
        return values;
    }

    public void setValues(java.util.List<io.nop.orm.eql.ast.SqlExpr> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.values = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlExpr> makeValues(){
        java.util.List<io.nop.orm.eql.ast.SqlExpr> list = getValues();
        if(list == null){
            list = new java.util.ArrayList<>();
            setValues(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("values",getValues());
       
    }


    public SqlMultiValueExpr newInstance(){
      return new SqlMultiValueExpr();
    }

    @Override
    public SqlMultiValueExpr deepClone(){
       SqlMultiValueExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(values != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlExpr> copy_values = new java.util.ArrayList<>(values.size());
                          for(io.nop.orm.eql.ast.SqlExpr item: values){
                              copy_values.add(item.deepClone());
                          }
                          ret.setValues(copy_values);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(values != null){
               for(io.nop.orm.eql.ast.SqlExpr child: values){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(values != null){
               for(io.nop.orm.eql.ast.SqlExpr child: values){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.values != null){
               int index = this.values.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlExpr> list = this.replaceInList(this.values,index,newChild);
                   this.setValues(list);
                   return true;
               }
            }
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.values != null){
               int index = this.values.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlExpr> list = this.removeInList(this.values,index);
                   this.setValues(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlMultiValueExpr other = (SqlMultiValueExpr)node;
    
            if(isListEquivalent(this.values,other.getValues())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlMultiValueExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(values != null){
                      
                              if(!values.isEmpty())
                                json.put("values", values);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                values = io.nop.api.core.util.FreezeHelper.freezeList(values,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
