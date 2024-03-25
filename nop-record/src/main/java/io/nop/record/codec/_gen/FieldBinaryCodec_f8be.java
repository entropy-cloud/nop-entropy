
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_f8be implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_f8be INSTANCE = new FieldBinaryCodec_f8be();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readF8be();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context){
	    if(value == null){
		    output.writeF8be(0.0);
		}else{
			output.writeF8be((Double)value);
		}	
    }
}
