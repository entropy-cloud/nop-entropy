
package io.nop.record.codec._gen;

import io.nop.record.codec.FieldCodecRegistry;



public class FieldBinaryCodecRegistrar{
    public static void registerWordType(FieldCodecRegistry registry){
	    
		registry.registerBinaryCodec("s1",FieldBinaryCodec_s1.INSTANCE);
		
		registry.registerBinaryCodec("u1",FieldBinaryCodec_u1.INSTANCE);
		
		registry.registerBinaryCodec("s2le",FieldBinaryCodec_s2le.INSTANCE);
		
		registry.registerBinaryCodec("s2be",FieldBinaryCodec_s2be.INSTANCE);
		
		registry.registerBinaryCodec("u2le",FieldBinaryCodec_u2le.INSTANCE);
		
		registry.registerBinaryCodec("u2be",FieldBinaryCodec_u2be.INSTANCE);
		
		registry.registerBinaryCodec("s4le",FieldBinaryCodec_s4le.INSTANCE);
		
		registry.registerBinaryCodec("s4be",FieldBinaryCodec_s4be.INSTANCE);
		
		registry.registerBinaryCodec("u4le",FieldBinaryCodec_u4le.INSTANCE);
		
		registry.registerBinaryCodec("u4be",FieldBinaryCodec_u4be.INSTANCE);
		
		registry.registerBinaryCodec("s8le",FieldBinaryCodec_s8le.INSTANCE);
		
		registry.registerBinaryCodec("s8be",FieldBinaryCodec_s8be.INSTANCE);
		
		registry.registerBinaryCodec("u8le",FieldBinaryCodec_u8le.INSTANCE);
		
		registry.registerBinaryCodec("u8be",FieldBinaryCodec_u8be.INSTANCE);
		
		registry.registerBinaryCodec("f4le",FieldBinaryCodec_f4le.INSTANCE);
		
		registry.registerBinaryCodec("f4be",FieldBinaryCodec_f4be.INSTANCE);
		
		registry.registerBinaryCodec("f8le",FieldBinaryCodec_f8le.INSTANCE);
		
		registry.registerBinaryCodec("f8be",FieldBinaryCodec_f8be.INSTANCE);
		
	}
}
