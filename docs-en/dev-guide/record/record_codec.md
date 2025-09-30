# Record Encoding/Decoding

The record model defines how to encode/decode message objects in binary or text formats:

## prop

When encoding and decoding a record, the attribute that specifies a field is named 'field'; in general it corresponds to the JavaBean property name, but you can also explicitly specify 'prop'.

Multiple fields can correspond to the same 'prop'.

1. Conditional logic: For example, under different conditions, multiple distinct fields can map to the same JavaBean property.
2. For nested objects, different fields in a record can correspond to different parts of a nested object. For example, if 'part1' and 'part2' are both object properties, and 'prop' is configured as 'refObj' for both, then the fields parsed from 'part1' and those parsed from 'part2' are merged into the object's 'refObj' property.
<!-- SOURCE_MD5:14f4acc2882faa37f72abff898c4d490-->
