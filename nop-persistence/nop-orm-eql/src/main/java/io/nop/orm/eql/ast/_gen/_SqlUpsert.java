//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlUpsert;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlUpsert extends io.nop.orm.eql.ast.SqlDmlStatement {
    
    protected java.util.List<io.nop.orm.eql.ast.SqlAssignment> assignments;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlColumnName> columns;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlColumnName> conflictColumns;
    
    protected io.nop.orm.eql.ast.SqlTableName tableName;
    
    protected io.nop.orm.eql.ast.SqlValues values;
    

    public _SqlUpsert(){
    }

    
    public java.util.List<io.nop.orm.eql.ast.SqlAssignment> getAssignments(){
        return assignments;
    }

    public void setAssignments(java.util.List<io.nop.orm.eql.ast.SqlAssignment> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.assignments = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlAssignment> makeAssignments(){
        java.util.List<io.nop.orm.eql.ast.SqlAssignment> list = getAssignments();
        if(list == null){
            list = new java.util.ArrayList<>();
            setAssignments(list);
        }
        return list;
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
    
    public java.util.List<io.nop.orm.eql.ast.SqlColumnName> getConflictColumns(){
        return conflictColumns;
    }

    public void setConflictColumns(java.util.List<io.nop.orm.eql.ast.SqlColumnName> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.conflictColumns = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlColumnName> makeConflictColumns(){
        java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = getConflictColumns();
        if(list == null){
            list = new java.util.ArrayList<>();
            setConflictColumns(list);
        }
        return list;
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
     
          checkMandatory("assignments",getAssignments());
       
          checkMandatory("columns",getColumns());
       
          checkMandatory("conflictColumns",getConflictColumns());
       
          checkMandatory("tableName",getTableName());
       
          checkMandatory("values",getValues());
       
    }


    public SqlUpsert newInstance(){
      return new SqlUpsert();
    }

    @Override
    public SqlUpsert deepClone(){
       SqlUpsert ret = newInstance();
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
            
                if(values != null){
                  
                          ret.setValues(values.deepClone());
                      
                }
            
                if(conflictColumns != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlColumnName> copy_conflictColumns = new java.util.ArrayList<>(conflictColumns.size());
                          for(io.nop.orm.eql.ast.SqlColumnName item: conflictColumns){
                              copy_conflictColumns.add(item.deepClone());
                          }
                          ret.setConflictColumns(copy_conflictColumns);
                      
                }
            
                if(assignments != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlAssignment> copy_assignments = new java.util.ArrayList<>(assignments.size());
                          for(io.nop.orm.eql.ast.SqlAssignment item: assignments){
                              copy_assignments.add(item.deepClone());
                          }
                          ret.setAssignments(copy_assignments);
                      
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
            if(values != null)
                processor.accept(values);
        
            if(conflictColumns != null){
               for(io.nop.orm.eql.ast.SqlColumnName child: conflictColumns){
                    processor.accept(child);
                }
            }
            if(assignments != null){
               for(io.nop.orm.eql.ast.SqlAssignment child: assignments){
                    processor.accept(child);
                }
            }
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
            if(values != null && processor.apply(values) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(conflictColumns != null){
               for(io.nop.orm.eql.ast.SqlColumnName child: conflictColumns){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(assignments != null){
               for(io.nop.orm.eql.ast.SqlAssignment child: assignments){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
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
            if(this.values == oldChild){
               this.setValues((io.nop.orm.eql.ast.SqlValues)newChild);
               return true;
            }
        
            if(this.conflictColumns != null){
               int index = this.conflictColumns.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = this.replaceInList(this.conflictColumns,index,newChild);
                   this.setConflictColumns(list);
                   return true;
               }
            }
            if(this.assignments != null){
               int index = this.assignments.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlAssignment> list = this.replaceInList(this.assignments,index,newChild);
                   this.setAssignments(list);
                   return true;
               }
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
            if(this.values == child){
                this.setValues(null);
                return true;
            }
        
            if(this.conflictColumns != null){
               int index = this.conflictColumns.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlColumnName> list = this.removeInList(this.conflictColumns,index);
                   this.setConflictColumns(list);
                   return true;
               }
            }
            if(this.assignments != null){
               int index = this.assignments.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlAssignment> list = this.removeInList(this.assignments,index);
                   this.setAssignments(list);
                   return true;
               }
            }
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlUpsert other = (SqlUpsert)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
            if(!isNodeEquivalent(this.tableName,other.getTableName())){
               return false;
            }
        
            if(isListEquivalent(this.columns,other.getColumns())){
               return false;
            }
            if(!isNodeEquivalent(this.values,other.getValues())){
               return false;
            }
        
            if(isListEquivalent(this.conflictColumns,other.getConflictColumns())){
               return false;
            }
            if(isListEquivalent(this.assignments,other.getAssignments())){
               return false;
            }
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlUpsert;
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
                
                    if(values != null){
                      
                              json.put("values", values);
                          
                    }
                
                    if(conflictColumns != null){
                      
                              if(!conflictColumns.isEmpty())
                                json.put("conflictColumns", conflictColumns);
                          
                    }
                
                    if(assignments != null){
                      
                              if(!assignments.isEmpty())
                                json.put("assignments", assignments);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                if(tableName != null)
                    tableName.freeze(cascade);
                columns = io.nop.api.core.util.FreezeHelper.freezeList(columns,cascade);         
                if(values != null)
                    values.freeze(cascade);
                conflictColumns = io.nop.api.core.util.FreezeHelper.freezeList(conflictColumns,cascade);         
                assignments = io.nop.api.core.util.FreezeHelper.freezeList(assignments,cascade);         
    }

}
 // resume CPD analysis - CPD-ON
