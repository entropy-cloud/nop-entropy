# JSR303-like Validation Mechanism

The Nop platform does not directly support annotations such as `@NotEmpty` from JSR303. Internally, it uses a more powerful Schema model.

1. Every entity object has a corresponding ObjMeta. The Nop platform leverages the Schema metadata in ObjMeta to perform validation, which is a far more powerful mechanism than JSR303. For example, it recognizes the dictName configuration and will automatically validate against dictionary tables.
2. For JavaBeans that are not entities, you can introduce metadata via the `@PropMeta` annotation; it will be automatically converted into an ISchema metadata object.

```
@DataBean
public class MyBean{
   @PropMeta(domain="email")
   public String getEmail(){
     return ...
   }
}
```
<!-- SOURCE_MD5:773500a046e413c28cb3d25f72a4c293-->
