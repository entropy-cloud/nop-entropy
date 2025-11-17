# Kustomize from the Perspective of Reversible Computation

kustomize is a declarative configuration management mechanism heavily promoted in Kubernetes 1.14. It explicitly articulates the idea “Customization is reuse.” Its core idea borrows from Docker’s design principles, using union-filesystem-style layering for configuration files. The lowest layer is a base file; different patch files are overlaid on top of the base to produce different configuration variants, thereby achieving reuse of the base configuration.

The directory structure used by kustomize is as follows:

```
someapp/
    ├── base/
    │   ├── kustomization.yaml
    │   ├── deployment.yaml
    │   ├── configMap.yaml
    │   └── service.yaml
    └── overlays/
     ├── production/
     │   └── kustomization.yaml
     │   ├── replica_count.yaml
     └── staging/
         ├── kustomization.yaml
         └── cpu_count.yaml
```

The base directory contains shared common configurations. The someapp/base/kustomization.yaml file is responsible for merging multiple YAML files into a single configuration file—essentially an include-like mechanism for decomposing and recombining files.

```yaml
commonLabels:
  app: hello

resources:
- deployment.yaml
- service.yaml
- configMap.yaml
```

The overlays directory centrally manages all customization configurations. For example, in someapp/overlays/production/kustomization.yaml you can use patches to modify bases:

```yaml
commonLabels:
  env: production
bases:
  - ../../base
patches:
  - replica_count.yaml
```

The format of a patch file is similar to a regular Kubernetes manifest:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: the-deployment
spec:
  replicas: 100
```

According to Zhang Lei at Alibaba, “The idea of the PATCH above is very similar to Docker images,” and “PATCH is the essence of declarative APIs.” Some have observed, “Once you truly understand the pattern of using overlays and patches, it starts to feel like a pattern you want to use everywhere.”

Before kustomize, a popular way to manage complex configurations was to use a template technique to dynamically generate configurations, such as Helm. This approach attempts to abstract variable parts of the configuration as parameters; the same template produces different results when given different parameters. As a reuse technique, this primitive parameter abstraction faces a fundamental difficulty: user needs are hard to determine in advance, so it is difficult to abstract a stable set of parameters. If you try to satisfy all needs, any part of the configuration file might be abstracted into a parameter, which defeats the purpose of abstraction.

The crux of kustomize is that it treats the patch (Delta) as an independently existing, independently understandable, independently operable object. The structure of a patch is basically consistent with ordinary configuration file formats (in fact, you can regard an ordinary configuration file as a special case of a patch). Patches and the base are merged according to precisely defined rules; the base file does not need to know anything about patches in advance nor prepare for patches that may exist in the future. When our mental focus shifts from the base to the patch, many complexities are reduced. For example, base files and patch files can be maintained by different people; when the base file is updated, the patch files we manage directly generally do not need adjustment (the Delta does not change with changes to the base). This is analogous to Docker’s revolution relative to virtual machines: we only need to care about the application layers (image layers) and no longer face the complexity of the operating system foundation layer directly.

Clearly, the Delta merge mechanism in kustomize conforms to the principles of Reversible Computation. In fact, as early as 2007 I proposed a delta customization mechanism similar to kustomize; see my blog post “Multi-version support.”

If you look around, you’ll notice that in recent years a large number of ad hoc configuration merge processes similar to kustomize have appeared across various fields. For example, the POM file in Maven’s package management tool also has similar inheritance and merge mechanisms.

```xml
<pom>
      <parent>
           <groupId>entropy</groupId>
           <artifactId>entropy-root</artifactId>
           <version>1.0-SNAPSHOT</version>
           <relativePath>../../entropy-platform/entropy-root</relativePath>
      </parent>
      ...
      <!-- The configuration nodes here will be merged with the inherited configuration,
           and different child nodes have different merge rules -->
      <build>...</build>
      <dependencies>...</dependencies>
   </pom>
```

Compared with Maven’s POM, kustomize advances by defining operations such as delete, thereby forming a complete set of patch operations. At the same time, kustomize allows custom resource definitions to be introduced and merged using the same mechanism, effectively providing a relatively general mechanism rather than a hardcoded algorithm targeting a specific structure. However, analyzed through the theory of Reversible Computation, kustomize still lacks some key conceptual designs.

The differences between Delta customization and kustomize are:
- Delta customization is a general mechanism that applies not only to configuration file merging but also broadly to the dynamic organization and merging of various structures. By contrast, kustomize remains an ad hoc, partial solution targeting Kubernetes configurations.
- Delta customization includes a built-in metaprogramming generation mechanism, which makes it easy to support higher-order abstraction, so end users are not limited by the expressive power of the base model. Kubernetes configurations amount to a domain-specific language for cloud deployment, but in specific business scenarios there is still a great deal of domain knowledge that can be abstracted and reused—knowledge that cannot be encapsulated within kustomize’s current mechanisms.
- Delta customization places strong emphasis on reversibility and actively leverages it. If you think carefully, you’ll realize that if we can implement precise customization, it necessarily means we can precisely locate anywhere in the domain structure and thus retrieve information from any point in it. In other words, the information expressed through the domain structure can be extracted in reverse. If such reversibility can be systematized and leveraged, it may unleash unimaginable productivity. For example, in a system that satisfies reversibility, it is easy to realize multiple representations of the same information; visual design is merely a simple application of reversible semantics.

Visual Interface = Interface Generator( DSL )
DSL = Data Extractor( Visual Interface )

Domain models are described using a Domain-Specific Language (DSL). A UI generator can understand the DSL, extract information from it, and convert it into a visual interface. At the same time, the UI generator provides a corresponding reverse information extraction mechanism, enabling the DSL model information to be extracted back from the generated visual interface. So-called visual design is merely a reversible transformation between two representations of the model.

Reversible Computation builds a complete software construction theory based on the concept of Delta. Its core ideas are inspired by physics and mathematics, giving it broad applicability. For specific theoretical analysis, see my earlier article “Reversible Computation: The Next-Generation Software Construction Theory.” In fact, Kubernetes’ architectural principles can also be explained within the framework of Reversible Computation:

[Reversible Computation: A Next-Generation Theory for Software Construction](https://dev.to/canonical/reversible-computation-a-next-generation-theory-for-software-construction-27fk)

<!-- SOURCE_MD5:a2550be0cdf36f2bfa92c90b5ca40d69-->
