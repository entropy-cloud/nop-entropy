//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlValuesTableSource;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlValuesTableSource extends io.nop.orm.eql.ast.SqlTableSource {
    
    protected io.nop.orm.eql.ast.SqlAlias alias;
    
    protected java.util.List<java.lang.String> columns;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> rows;
    

    public _SqlValuesTableSource(){
    }

    
    public io.nop.orm.eql.ast.SqlAlias getAlias(){
        return alias;
    }

    public void setAlias(io.nop.orm.eql.ast.SqlAlias value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.alias = value;
    }
    
    public java.util.List<java.lang.String> getColumns(){
        return columns;
    }

    public void setColumns(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this.columns = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> getRows(){
        return rows;
    }

    public void setRows(java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.rows = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> makeRows(){
        java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> list = getRows();
        if(list == null){
            list = new java.util.ArrayList<>();
            setRows(list);
        }
        return list;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("alias",getAlias());
       
          checkMandatory("rows",getRows());
       
    }


    public SqlValuesTableSource newInstance(){
      return new SqlValuesTableSource();
    }

    @Override
    public SqlValuesTableSource deepClone(){
       SqlValuesTableSource ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(decorators != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlDecorator> copy_decorators = new java.util.ArrayList<>(decorators.size());
                          for(io.nop.orm.eql.ast.SqlDecorator item: decorators){
                              copy_decorators.add(item.deepClone());
                          }
                          ret.setDecorators(copy_decorators);
                      
                }
            
                if(rows != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> copy_rows = new java.util.ArrayList<>(rows.size());
                          for(io.nop.orm.eql.ast.SqlMultiValueExpr item: rows){
                              copy_rows.add(item.deepClone());
                          }
                          ret.setRows(copy_rows);
                      
                }
            
                if(alias != null){
                  
                          ret.setAlias(alias.deepClone());
                      
                }
            
                if(columns != null){
                  
                          ret.setColumns(columns);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    processor.accept(child);
                }
            }
            if(rows != null){
               for(io.nop.orm.eql.ast.SqlMultiValueExpr child: rows){
                    processor.accept(child);
                }
            }
            if(alias != null)
                processor.accept(alias);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(rows != null){
               for(io.nop.orm.eql.ast.SqlMultiValueExpr child: rows){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(alias != null && processor.apply(alias) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.decorators != null){
               int index = this.decorators.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = this.replaceInList(this.decorators,index,newChild);
                   this.setDecorators(list);
                   return true;
               }
            }
            if(this.rows != null){
               int index = this.rows.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> list = this.replaceInList(this.rows,index,newChild);
                   this.setRows(list);
                   return true;
               }
            }
            if(this.alias == oldChild){
               this.setAlias((io.nop.orm.eql.ast.SqlAlias)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.decorators != null){
               int index = this.decorators.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlDecorator> list = this.removeInList(this.decorators,index);
                   this.setDecorators(list);
                   return true;
               }
            }
            if(this.rows != null){
               int index = this.rows.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlMultiValueExpr> list = this.removeInList(this.rows,index);
                   this.setRows(list);
                   return true;
               }
            }
            if(this.alias == child){
                this.setAlias(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlValuesTableSource other = (SqlValuesTableSource)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(isListEquivalent(this.rows,other.getRows())){
               return false;
            }
            if(!isNodeEquivalent(this.alias,other.getAlias())){
               return false;
            }
        
                if(!isValueEquivalent(this.columns,other.getColumns())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlValuesTableSource;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(rows != null){
                      
                              if(!rows.isEmpty())
                                json.put("rows", rows);
                          
                    }
                
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
                    if(columns != null){
                      
                              json.put("columns", columns);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                rows = io.nop.api.core.util.FreezeHelper.freezeList(rows,cascade);         
                if(alias != null)
                    alias.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
