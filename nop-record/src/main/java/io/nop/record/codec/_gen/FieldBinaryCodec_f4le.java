
package io.nop.record.codec._gen;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldBinaryEncoder;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IRecordBinaryReader;
import io.nop.record.writer.IRecordBinaryWriter;

import java.nio.charset.Charset;

public class FieldBinaryCodec_f4le implements IFieldBinaryCodec{
    public static final FieldBinaryCodec_f4le INSTANCE = new FieldBinaryCodec_f4le();

    public Object decode(IRecordBinaryReader input, int length, Charset charset, IFieldCodecContext context){
	  return input.readF4le();
    }

    public void encode(IRecordBinaryWriter output, Object value, int length, Charset charset,
					   IFieldCodecContext context, IFieldBinaryEncoder bodyEncoder){
	    if(value == null){
		    output.writeF4le(0.0f);
		}else{
			output.writeF4le((Float)value);
		}	
    }
}
