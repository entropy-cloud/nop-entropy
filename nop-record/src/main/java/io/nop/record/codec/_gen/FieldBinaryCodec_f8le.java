
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.input.IRecordBinaryInput;
import io.nop.record.output.IRecordBinaryOutput;

import java.nio.charset.Charset;

public class FieldBinaryCodec_f8le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_f8le INSTANCE = new FieldBinaryCodec_f8le();

    public Object decode(IRecordBinaryInput input, int length, Charset charset, IFieldCodecContext context){
	  return input.readF8le();
    }

    public void encode(IRecordBinaryOutput output, Object value, int length, Charset charset, IFieldCodecContext context){
	    if(value == null){
		    output.writeF8le(0.0);
		}else{
			output.writeF8le((Double)value);
		}	
    }
}
