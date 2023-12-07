//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlQuerySelect extends io.nop.orm.eql.ast.SqlSelect {
    
    protected boolean distinct;
    
    protected boolean forUpdate;
    
    protected io.nop.orm.eql.ast.SqlFrom from;
    
    protected io.nop.orm.eql.ast.SqlGroupBy groupBy;
    
    protected io.nop.orm.eql.ast.SqlHaving having;
    
    protected io.nop.orm.eql.ast.SqlLimit limit;
    
    protected boolean nowait;
    
    protected io.nop.orm.eql.ast.SqlOrderBy orderBy;
    
    protected java.util.List<io.nop.orm.eql.ast.SqlProjection> projections;
    
    protected boolean selectAll;
    
    protected io.nop.orm.eql.ast.SqlWhere where;
    

    public _SqlQuerySelect(){
    }

    
    public boolean getDistinct(){
        return distinct;
    }

    public void setDistinct(boolean value){
        checkAllowChange();
        
        this.distinct = value;
    }
    
    public boolean getForUpdate(){
        return forUpdate;
    }

    public void setForUpdate(boolean value){
        checkAllowChange();
        
        this.forUpdate = value;
    }
    
    public io.nop.orm.eql.ast.SqlFrom getFrom(){
        return from;
    }

    public void setFrom(io.nop.orm.eql.ast.SqlFrom value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.from = value;
    }
    
    public io.nop.orm.eql.ast.SqlGroupBy getGroupBy(){
        return groupBy;
    }

    public void setGroupBy(io.nop.orm.eql.ast.SqlGroupBy value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.groupBy = value;
    }
    
    public io.nop.orm.eql.ast.SqlHaving getHaving(){
        return having;
    }

    public void setHaving(io.nop.orm.eql.ast.SqlHaving value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.having = value;
    }
    
    public io.nop.orm.eql.ast.SqlLimit getLimit(){
        return limit;
    }

    public void setLimit(io.nop.orm.eql.ast.SqlLimit value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.limit = value;
    }
    
    public boolean getNowait(){
        return nowait;
    }

    public void setNowait(boolean value){
        checkAllowChange();
        
        this.nowait = value;
    }
    
    public io.nop.orm.eql.ast.SqlOrderBy getOrderBy(){
        return orderBy;
    }

    public void setOrderBy(io.nop.orm.eql.ast.SqlOrderBy value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.orderBy = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlProjection> getProjections(){
        return projections;
    }

    public void setProjections(java.util.List<io.nop.orm.eql.ast.SqlProjection> value){
        checkAllowChange();
        
                if(value != null){
                  value.forEach(node->node.setASTParent((EqlASTNode)this));
                }
            
        this.projections = value;
    }
    
    public java.util.List<io.nop.orm.eql.ast.SqlProjection> makeProjections(){
        java.util.List<io.nop.orm.eql.ast.SqlProjection> list = getProjections();
        if(list == null){
            list = new java.util.ArrayList<>();
            setProjections(list);
        }
        return list;
    }
    
    public boolean getSelectAll(){
        return selectAll;
    }

    public void setSelectAll(boolean value){
        checkAllowChange();
        
        this.selectAll = value;
    }
    
    public io.nop.orm.eql.ast.SqlWhere getWhere(){
        return where;
    }

    public void setWhere(io.nop.orm.eql.ast.SqlWhere value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.where = value;
    }
    

    public void validate(){
       super.validate();
     
    }


    public SqlQuerySelect newInstance(){
      return new SqlQuerySelect();
    }

    @Override
    public SqlQuerySelect deepClone(){
       SqlQuerySelect ret = newInstance();
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
            
                ret.setDistinct(distinct);
            
                ret.setSelectAll(selectAll);
            
                if(projections != null){
                  
                          java.util.List<io.nop.orm.eql.ast.SqlProjection> copy_projections = new java.util.ArrayList<>(projections.size());
                          for(io.nop.orm.eql.ast.SqlProjection item: projections){
                              copy_projections.add(item.deepClone());
                          }
                          ret.setProjections(copy_projections);
                      
                }
            
                if(from != null){
                  
                          ret.setFrom(from.deepClone());
                      
                }
            
                if(where != null){
                  
                          ret.setWhere(where.deepClone());
                      
                }
            
                if(groupBy != null){
                  
                          ret.setGroupBy(groupBy.deepClone());
                      
                }
            
                if(having != null){
                  
                          ret.setHaving(having.deepClone());
                      
                }
            
                if(orderBy != null){
                  
                          ret.setOrderBy(orderBy.deepClone());
                      
                }
            
                if(limit != null){
                  
                          ret.setLimit(limit.deepClone());
                      
                }
            
                ret.setForUpdate(forUpdate);
            
                ret.setNowait(nowait);
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    processor.accept(child);
                }
            }
            if(projections != null){
               for(io.nop.orm.eql.ast.SqlProjection child: projections){
                    processor.accept(child);
                }
            }
            if(from != null)
                processor.accept(from);
        
            if(where != null)
                processor.accept(where);
        
            if(groupBy != null)
                processor.accept(groupBy);
        
            if(having != null)
                processor.accept(having);
        
            if(orderBy != null)
                processor.accept(orderBy);
        
            if(limit != null)
                processor.accept(limit);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(decorators != null){
               for(io.nop.orm.eql.ast.SqlDecorator child: decorators){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(projections != null){
               for(io.nop.orm.eql.ast.SqlProjection child: projections){
                    if(processor.apply(child) == ProcessResult.STOP)
                        return ProcessResult.STOP;
               }
            }
            if(from != null && processor.apply(from) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(where != null && processor.apply(where) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(groupBy != null && processor.apply(groupBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(having != null && processor.apply(having) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(orderBy != null && processor.apply(orderBy) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(limit != null && processor.apply(limit) == ProcessResult.STOP)
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
            if(this.projections != null){
               int index = this.projections.indexOf(oldChild);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlProjection> list = this.replaceInList(this.projections,index,newChild);
                   this.setProjections(list);
                   return true;
               }
            }
            if(this.from == oldChild){
               this.setFrom((io.nop.orm.eql.ast.SqlFrom)newChild);
               return true;
            }
        
            if(this.where == oldChild){
               this.setWhere((io.nop.orm.eql.ast.SqlWhere)newChild);
               return true;
            }
        
            if(this.groupBy == oldChild){
               this.setGroupBy((io.nop.orm.eql.ast.SqlGroupBy)newChild);
               return true;
            }
        
            if(this.having == oldChild){
               this.setHaving((io.nop.orm.eql.ast.SqlHaving)newChild);
               return true;
            }
        
            if(this.orderBy == oldChild){
               this.setOrderBy((io.nop.orm.eql.ast.SqlOrderBy)newChild);
               return true;
            }
        
            if(this.limit == oldChild){
               this.setLimit((io.nop.orm.eql.ast.SqlLimit)newChild);
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
            if(this.projections != null){
               int index = this.projections.indexOf(child);
               if(index >= 0){
                   java.util.List<io.nop.orm.eql.ast.SqlProjection> list = this.removeInList(this.projections,index);
                   this.setProjections(list);
                   return true;
               }
            }
            if(this.from == child){
                this.setFrom(null);
                return true;
            }
        
            if(this.where == child){
                this.setWhere(null);
                return true;
            }
        
            if(this.groupBy == child){
                this.setGroupBy(null);
                return true;
            }
        
            if(this.having == child){
                this.setHaving(null);
                return true;
            }
        
            if(this.orderBy == child){
                this.setOrderBy(null);
                return true;
            }
        
            if(this.limit == child){
                this.setLimit(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlQuerySelect other = (SqlQuerySelect)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
                if(!isValueEquivalent(this.distinct,other.getDistinct())){
                   return false;
                }
            
                if(!isValueEquivalent(this.selectAll,other.getSelectAll())){
                   return false;
                }
            
            if(isListEquivalent(this.projections,other.getProjections())){
               return false;
            }
            if(!isNodeEquivalent(this.from,other.getFrom())){
               return false;
            }
        
            if(!isNodeEquivalent(this.where,other.getWhere())){
               return false;
            }
        
            if(!isNodeEquivalent(this.groupBy,other.getGroupBy())){
               return false;
            }
        
            if(!isNodeEquivalent(this.having,other.getHaving())){
               return false;
            }
        
            if(!isNodeEquivalent(this.orderBy,other.getOrderBy())){
               return false;
            }
        
            if(!isNodeEquivalent(this.limit,other.getLimit())){
               return false;
            }
        
                if(!isValueEquivalent(this.forUpdate,other.getForUpdate())){
                   return false;
                }
            
                if(!isValueEquivalent(this.nowait,other.getNowait())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlQuerySelect;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                   json.put("distinct", distinct);
                
                   json.put("selectAll", selectAll);
                
                    if(projections != null){
                      
                              if(!projections.isEmpty())
                                json.put("projections", projections);
                          
                    }
                
                    if(from != null){
                      
                              json.put("from", from);
                          
                    }
                
                    if(where != null){
                      
                              json.put("where", where);
                          
                    }
                
                    if(groupBy != null){
                      
                              json.put("groupBy", groupBy);
                          
                    }
                
                    if(having != null){
                      
                              json.put("having", having);
                          
                    }
                
                    if(orderBy != null){
                      
                              json.put("orderBy", orderBy);
                          
                    }
                
                    if(limit != null){
                      
                              json.put("limit", limit);
                          
                    }
                
                   json.put("forUpdate", forUpdate);
                
                   json.put("nowait", nowait);
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                projections = io.nop.api.core.util.FreezeHelper.freezeList(projections,cascade);         
                if(from != null)
                    from.freeze(cascade);
                if(where != null)
                    where.freeze(cascade);
                if(groupBy != null)
                    groupBy.freeze(cascade);
                if(having != null)
                    having.freeze(cascade);
                if(orderBy != null)
                    orderBy.freeze(cascade);
                if(limit != null)
                    limit.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
