//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlInsert;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlInsert extends io.nop.orm.eql.ast.SqlDmlStatement {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlColumnName> columns;
    
    protected io.nop.orm.eql.ast.SqlSelect select;
    
    protected io.nop.orm.eql.ast.SqlTableName tableName;
    
    protected io.nop.orm.eql.ast.SqlValues values;
    

    public _SqlInsert(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlColumnName> getColumns(){
        return columns;
    }

    public void setColumns(java.util.List<io.nop.orm.eql.ast.SqlColumnName> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.columns = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlColumnName> makeColumns(){
        java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = getColumns();
        if(list == null){
            list = new java.util.ArrayList<>();
            setColumns(list);
        }
        return list;
    }
    
    public io.nop.orm.eql.ast.SqlSelect getSelect(){
        return select;
    }

    public void setSelect(io.nop.orm.eql.ast.SqlSelect value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.select = value;
    }
    
    public io.nop.orm.eql.ast.SqlTableName getTableName(){
        return tableName;
    }

    public void setTableName(io.nop.orm.eql.ast.SqlTableName value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.tableName = value;
    }
    
    public io.nop.orm.eql.ast.SqlValues getValues(){
        return values;
    }

    public void setValues(io.nop.orm.eql.ast.SqlValues value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.values = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("columns",getColumns());
       
          checkMandatory("tableName",getTableName());
       
    }


    public SqlInsert newInstance(){
      return new SqlInsert();
    }

    @Override
    public SqlInsert deepClone(){
       SqlInsert ret = newInstance();
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
            
                if(tableName != null){
                  
                          ret.setTableName(tableName.deepClone());
                      
                }
            
                if(columns != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlColumnName> copy_columns = new java.util.ArrayList<>(columns.size());
                          for(io.nop.orm.eql.ast.SqlColumnName item: columns){
                              copy_columns.add(item.deepClone());
                          }
                          ret.setColumns(copy_columns);
                      
                }
            
                if(select != null){
                  
                          ret.setSelect(select.deepClone());
                      
                }
            
                if(values != null){
                  
                          ret.setValues(values.deepClone());
                      
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
            if(tableName != null)
                processor.accept(tableName);
        
            if(columns != null){
               for(io.nop.orm.eql.ast.SqlColumnName child: columns){
                    processor.accept(child);
                }
            }
            if(select != null)
                processor.accept(select);
        
            if(values != null)
                processor.accept(values);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(tableName != null && processor.apply(tableName) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(columns != null){
               for(io.nop.orm.eql.ast.SqlColumnName child: columns){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(select != null && processor.apply(select) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(values != null && processor.apply(values) == ProcessResult.STOP)
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
            if(this.tableName == oldChild){
               this.setTableName((io.nop.orm.eql.ast.SqlTableName)newChild);
               return true;
            }
        
            if(this.columns != null){
               int index = this.columns.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = this.replaceInList(this.columns,index,newChild);
                   this.setColumns(list);
                   return true;
               }
            }
            if(this.select == oldChild){
               this.setSelect((io.nop.orm.eql.ast.SqlSelect)newChild);
               return true;
            }
        
            if(this.values == oldChild){
               this.setValues((io.nop.orm.eql.ast.SqlValues)newChild);
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
            if(this.tableName == child){
                this.setTableName(null);
                return true;
            }
        
            if(this.columns != null){
               int index = this.columns.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = this.removeInList(this.columns,index);
                   this.setColumns(list);
                   return true;
               }
            }
            if(this.select == child){
                this.setSelect(null);
                return true;
            }
        
            if(this.values == child){
                this.setValues(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlInsert other = (SqlInsert)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(!isNodeEquivalent(this.tableName,other.getTableName())){
               return false;
            }
        
            if(isListEquivalent(this.columns,other.getColumns())){
               return false;
            }
            if(!isNodeEquivalent(this.select,other.getSelect())){
               return false;
            }
        
            if(!isNodeEquivalent(this.values,other.getValues())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlInsert;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(tableName != null){
                      
                              json.put("tableName", tableName);
                          
                    }
                
                    if(columns != null){
                      
                              if(!columns.isEmpty())
                                json.put("columns", columns);
                          
                    }
                
                    if(select != null){
                      
                              json.put("select", select);
                          
                    }
                
                    if(values != null){
                      
                              json.put("values", values);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                if(tableName != null)
                    tableName.freeze(cascade);
                columns = io.nop.api.core.util.FreezeHelper.freezeList(columns,cascade);         
                if(select != null)
                    select.freeze(cascade);
                if(values != null)
                    values.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
