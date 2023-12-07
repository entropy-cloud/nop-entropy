//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlInValuesExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    
    protected boolean not;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlExpr> values;
    

    public _SqlInValuesExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public boolean getNot(){
        return not;
    }

    public void setNot(boolean value){
        checkAllowChange();
        
        this.not = value;
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
     
          checkMandatory("expr",getExpr());
       
          checkMandatory("values",getValues());
       
    }


    public SqlInValuesExpr newInstance(){
      return new SqlInValuesExpr();
    }

    @Override
    public SqlInValuesExpr deepClone(){
       SqlInValuesExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                ret.setNot(not);
            
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
    
            if(expr != null)
                processor.accept(expr);
        
            if(values != null){
               for(io.nop.orm.eql.ast.SqlExpr child: values){
                    processor.accept(child);
                }
            }
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
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
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
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
    
            if(this.expr == child){
                this.setExpr(null);
                return true;
            }
        
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
    SqlInValuesExpr other = (SqlInValuesExpr)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.not,other.getNot())){
                   return false;
                }
            
            if(isListEquivalent(this.values,other.getValues())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlInValuesExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                   json.put("not", not);
                
                    if(values != null){
                      
                              if(!values.isEmpty())
                                json.put("values", values);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
                values = io.nop.api.core.util.FreezeHelper.freezeList(values,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
