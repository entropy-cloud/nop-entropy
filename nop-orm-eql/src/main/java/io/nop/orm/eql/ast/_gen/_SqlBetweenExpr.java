//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlBetweenExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected io.nop.orm.eql.ast.SqlExpr begin;
    
    protected io.nop.orm.eql.ast.SqlExpr end;
    
    protected boolean not;
    
    protected io.nop.orm.eql.ast.SqlExpr test;
    

    public _SqlBetweenExpr(){
    }

    
    public io.nop.orm.eql.ast.SqlExpr getBegin(){
        return begin;
    }

    public void setBegin(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.begin = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getEnd(){
        return end;
    }

    public void setEnd(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.end = value;
    }
    
    public boolean getNot(){
        return not;
    }

    public void setNot(boolean value){
        checkAllowChange();
        
        this.not = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getTest(){
        return test;
    }

    public void setTest(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.test = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("begin",getBegin());
       
          checkMandatory("end",getEnd());
       
          checkMandatory("test",getTest());
       
    }


    public SqlBetweenExpr newInstance(){
      return new SqlBetweenExpr();
    }

    @Override
    public SqlBetweenExpr deepClone(){
       SqlBetweenExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(test != null){
                  
                          ret.setTest(test.deepClone());
                      
                }
            
                ret.setNot(not);
            
                if(begin != null){
                  
                          ret.setBegin(begin.deepClone());
                      
                }
            
                if(end != null){
                  
                          ret.setEnd(end.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(test != null)
                processor.accept(test);
        
            if(begin != null)
                processor.accept(begin);
        
            if(end != null)
                processor.accept(end);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(test != null && processor.apply(test) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(begin != null && processor.apply(begin) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
            if(end != null && processor.apply(end) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.test == oldChild){
               this.setTest((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.begin == oldChild){
               this.setBegin((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
            if(this.end == oldChild){
               this.setEnd((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.test == child){
                this.setTest(null);
                return true;
            }
        
            if(this.begin == child){
                this.setBegin(null);
                return true;
            }
        
            if(this.end == child){
                this.setEnd(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlBetweenExpr other = (SqlBetweenExpr)node;
    
            if(!isNodeEquivalent(this.test,other.getTest())){
               return false;
            }
        
                if(!isValueEquivalent(this.not,other.getNot())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.begin,other.getBegin())){
               return false;
            }
        
            if(!isNodeEquivalent(this.end,other.getEnd())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlBetweenExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(test != null){
                      
                              json.put("test", test);
                          
                    }
                
                   json.put("not", not);
                
                    if(begin != null){
                      
                              json.put("begin", begin);
                          
                    }
                
                    if(end != null){
                      
                              json.put("end", end);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(test != null)
                    test.freeze(cascade);
                if(begin != null)
                    begin.freeze(cascade);
                if(end != null)
                    end.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
