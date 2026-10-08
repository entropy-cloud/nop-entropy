//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlHopTableSource;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlHopTableSource extends io.nop.orm.eql.ast.SqlTableSource {
    
    protected io.nop.orm.eql.ast.SqlAlias alias;
    
    protected io.nop.orm.eql.ast.SqlIntervalExpr sizeInterval;
    
    protected io.nop.orm.eql.ast.SqlIntervalExpr slideInterval;
    
    protected java.lang.String tableName;
    
    protected java.lang.String timeColumn;
    

    public _SqlHopTableSource(){
    }

    
    public io.nop.orm.eql.ast.SqlAlias getAlias(){
        return alias;
    }

    public void setAlias(io.nop.orm.eql.ast.SqlAlias value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.alias = value;
    }
    
    public io.nop.orm.eql.ast.SqlIntervalExpr getSizeInterval(){
        return sizeInterval;
    }

    public void setSizeInterval(io.nop.orm.eql.ast.SqlIntervalExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.sizeInterval = value;
    }
    
    public io.nop.orm.eql.ast.SqlIntervalExpr getSlideInterval(){
        return slideInterval;
    }

    public void setSlideInterval(io.nop.orm.eql.ast.SqlIntervalExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.slideInterval = value;
    }
    
    public java.lang.String getTableName(){
        return tableName;
    }

    public void setTableName(java.lang.String value){
        checkAllowChange();
        
        this.tableName = value;
    }
    
    public java.lang.String getTimeColumn(){
        return timeColumn;
    }

    public void setTimeColumn(java.lang.String value){
        checkAllowChange();
        
        this.timeColumn = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("sizeInterval",getSizeInterval());
       
          checkMandatory("slideInterval",getSlideInterval());
       
          checkMandatory("tableName",getTableName());
       
          checkMandatory("timeColumn",getTimeColumn());
       
    }


    public SqlHopTableSource newInstance(){
      return new SqlHopTableSource();
    }

    @Override
    public SqlHopTableSource deepClone(){
       SqlHopTableSource ret = newInstance();
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
                  
                          ret.setTableName(tableName);
                      
                }
            
                if(timeColumn != null){
                  
                          ret.setTimeColumn(timeColumn);
                      
                }
            
                if(slideInterval != null){
                  
                          ret.setSlideInterval(slideInterval.deepClone());
                      
                }
            
                if(sizeInterval != null){
                  
                          ret.setSizeInterval(sizeInterval.deepClone());
                      
                }
            
                if(alias != null){
                  
                          ret.setAlias(alias.deepClone());
                      
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
            if(slideInterval != null)
                processor.accept(slideInterval);
        
            if(sizeInterval != null)
                processor.accept(sizeInterval);
        
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
            if(slideInterval != null && processor.apply(slideInterval) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(sizeInterval != null && processor.apply(sizeInterval) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
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
            if(this.slideInterval == oldChild){
               this.setSlideInterval((io.nop.orm.eql.ast.SqlIntervalExpr)newChild);
               return true;
            }
        
            if(this.sizeInterval == oldChild){
               this.setSizeInterval((io.nop.orm.eql.ast.SqlIntervalExpr)newChild);
               return true;
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
            if(this.slideInterval == child){
                this.setSlideInterval(null);
                return true;
            }
        
            if(this.sizeInterval == child){
                this.setSizeInterval(null);
                return true;
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
    SqlHopTableSource other = (SqlHopTableSource)node;
    
            if(isListEquivalent(this.decorators,other.getDecorators())){
               return false;
            }
                if(!isValueEquivalent(this.tableName,other.getTableName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.timeColumn,other.getTimeColumn())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.slideInterval,other.getSlideInterval())){
               return false;
            }
        
            if(!isNodeEquivalent(this.sizeInterval,other.getSizeInterval())){
               return false;
            }
        
            if(!isNodeEquivalent(this.alias,other.getAlias())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlHopTableSource;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(decorators != null){
                      
                              if(!decorators.isEmpty())
                                json.put("decorators", decorators);
                          
                    }
                
                    if(tableName != null){
                      
                              json.put("tableName", tableName);
                          
                    }
                
                    if(timeColumn != null){
                      
                              json.put("timeColumn", timeColumn);
                          
                    }
                
                    if(slideInterval != null){
                      
                              json.put("slideInterval", slideInterval);
                          
                    }
                
                    if(sizeInterval != null){
                      
                              json.put("sizeInterval", sizeInterval);
                          
                    }
                
                    if(alias != null){
                      
                              json.put("alias", alias);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                decorators = io.nop.api.core.util.FreezeHelper.freezeList(decorators,cascade);         
                if(slideInterval != null)
                    slideInterval.freeze(cascade);
                if(sizeInterval != null)
                    sizeInterval.freeze(cascade);
                if(alias != null)
                    alias.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
