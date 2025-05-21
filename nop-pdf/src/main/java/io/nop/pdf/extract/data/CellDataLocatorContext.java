package io.nop.pdf.extract.data;

import java.util.HashMap;
import java.util.Map;

public class CellDataLocatorContext {

    private Map<String,Object> data = new HashMap<String,Object>();
    
    public void set( String name, Object value ) {
        
        this.data.put( name, value );
    }
    
    public Object get( String name ) {
        
        return this.data.get( name );
    }
}
