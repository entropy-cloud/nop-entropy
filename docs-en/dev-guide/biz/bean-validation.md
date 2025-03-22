# Similar to JSR-303 Validation Mechanism

The Nop platform does not directly support annotations like `@NotEmpty` from JSR-303. Instead, it uses a more robust Schema model internally.

1. All entity objects have corresponding ObjMeta, and the Nop platform leverages the Schema metadata within ObjMeta to perform validation. This mechanism is significantly more powerful than JSR-303's approach. For example, it identifies `dictName` configurations and validates dictionary tables automatically.
2. For JavaBeans that are not entities, you can introduce metadata using the `@PropMeta` annotation. This will be automatically converted into an ISchema metadata object.

```markdown
@DataBean
public class MyBean {
    @PropMeta(domain="email")
    public String getEmail() {
        return ...
    }
}
```
