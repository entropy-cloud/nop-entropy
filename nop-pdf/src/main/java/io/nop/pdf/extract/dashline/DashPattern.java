package io.nop.pdf.extract.dashline;

public class DashPattern {

    private int mod;
    private int start;
    private int length;
    
    public DashPattern() {
        
    }
    
    public DashPattern( int mod, int start, int length ) {
        
        this.mod = mod;
        this.start = start;
        this.length = length;
    }
    
    public int getMod() {
        return mod;
    }
    public void setMod( int mod ) {
        this.mod = mod;
    }
    public int getStart() {
        return start;
    }
    public void setStart( int start ) {
        this.start = start;
    }
    public int getLength() {
        return length;
    }
    public void setLength( int length ) {
        this.length = length;
    }

     
}
