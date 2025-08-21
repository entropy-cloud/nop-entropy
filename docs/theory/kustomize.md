# 从可逆计算看kustomize

kustomize是kubernetes最新版（1.14）中力推的声明式配置管理机制。它明确提出了“Customization is reuse”的概念。其核心思想是仿照Docker的设计原理，将配置文件也类似文件系统一样进行分层管理。最底层是一个作为基础的base文件，然后将不同的patch文件覆盖（overlay）到base文件之上就可以得到不同的配置变体（variants），从而实现对基础配置文件的复用。

kustomize所使用的目录结构如下所示：

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
		 
base目录下包含可以被共享的公共配置，someapp/base/kustomization.yaml文件负责将多个yaml文件合并成一个单一配置文件, 相当于是一种类似include的文件分解合并机制。

```yaml
commonLabels:
  app: hello

resources:
- deployment.yaml
- service.yaml
- configMap.yaml
overlays目录下统一管理所有的定制配置。 例如，可以在someapp/overlays/production/kustomization.yaml中通过patches来对bases进行修正：

commonLabels:
       env: production
      bases:
      - ../../base
      patches:
      - replica_count.yaml
patch文件的格式与普通的k8s配置类似：

apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: the-deployment
      spec:
        replicas: 100
```		
		
按照阿里公司张磊的说法，“上述PATCH的思想跟Docker镜像是非常相似的“，”PATCH才是声明式API的精髓“。还有人指出“一旦你真正了解了使用叠加层和补丁的模式，它就会开始感觉就像一个你想要在任何地方使用的模式”。

在kustomize出现之前，比较流行的复杂配置管理方式是采用一种模板（template）技术来动态生成配置，例如helm, 这种技术试图将可变的配置部分抽象为参数（Parameter），同样的模板传入不同的参数就可以产生不同的结果。作为一种重用技术，这种原始的参数抽象的方法面临一个基本困难：用户需求难以事先确定，因此很难抽象出稳定的参数集，如果要满足所有需求，则配置文件中的任何一个部分都可能被抽象为参数，结果就失去了抽象存在的意义。

kustomize的核心要点在于，它将patch（差量）看作是可独立存在、独立理解、独立操作的对象，patch的结构与普通的配置文件格式基本一致（实际上可以将普通的配置文件看作是patch的特例）。patch与base之间按照精确定义的合并规则进行合并，base文件不需要事先知道任何patch的知识，不需要事先为未来可能存在的patch做任何准备。当我们心智所关注的重点从base转移为patch时，很多问题的复杂度得到降低，例如base文件和patch文件可以由不同的人员维护，当base文件更新升级时，我们直接管理的patch文件一般情况下并不需要做什么调整（Delta差量并不随base的变化而变化）。这一点恰恰类似Docker相对于虚拟机的革命：我们只需要关心应用切片层，而不再需要直接面对复杂的操作系统基础层。

显然，kustomize中的差量合并机制是符合可逆计算原理的。事实上，早在2007年我就提出了类似kustomize的delta定制机制，可以参见我的博客文章"多版本支持"。


只要稍微留意一下，就会发现近几年来在各个领域都出现了大量Ad hoc的，类似kustomize的配置合并处理过程。例如：Java包管理工具Maven的pom文件也具有类似的继承与合并机制。

```xml
<pom>
      <parent>
           <groupId>entropy</groupId>
           <artifactId>entropy-root</artifactId>
           <version>1.0-SNAPSHOT</version>
           <relativePath>../../entropy-platform/entropy-root</relativePath>
      </parent>
      ...
      <!--    这里的配置节点会和继承的配置相合并，不同的子节点具有不同的合并规则 -->
      <build>...</build>
      <dependencies>...</dependencies>
   </pom>
```
   
kustomize相比于pom而言，它的进步之处在于定义了delete等操作，因此可以构成一个完备的Patch操作集。同时kustomize允许引入自定义的资源定义并使用同样的机制进行合并，相当于是提供了某种相对通用的机制，而不是完全写死的针对特定结构的固化算法。不过，如果按照可逆计算理论的分析，kustomize仍然缺失一些关键性的概念设计。

delta定制与kustomize的不同之处在于：

delta定制是一种通用的机制，不仅仅适用于配置文件合并，而是广泛应用于各类结构的动态组织与合并。而kustomize仍然只是针对k8s配置的一种Ad hoc的局部解决方案。
delta定制包含一个内置的元编程生成机制，因此可以很容易的支持二次抽象。使得最终用户不受限于基础模型的表达能力。k8s配置相当于是提供了某种针对云计算部署的某种领域特定语言，但是当我们在特定业务场景下使用时，仍然有很多业务领域知识可以被抽象复用，而在kustomize当前的机制下是无法封装这些业务领域特定知识的。
delta定制非常强调可逆性，并积极主动的利用可逆性。其实仔细思考一下就会发现，如果我们可以实现精确的定制，那么必然意味着我们可以精确的定位到领域结构的任何地方，那么也就意味着我们可以取出领域结构任何一处的信息。也就是说，通过领域结构表达的信息是可以被反向抽取出来的。这种可逆性如果能够系统化的被利用，就可能释放出超乎想象的生产力
例如，在满足可逆性的体系中，我们很容易实现同一信息的多种展现形式，可视化设计仅仅是可逆语义的一个简单应用。

可
视
化
界
面
界
面
生
成
器
数
据
提
取
可
视
化
界
面
界
面
生
成
器
界
面
生
成
器

领域模型采用领域特定语言（DSL）来描述，界面生成器可以理解DSL，从中抽取信息将其转换为可视化界面，同时界面生成器提供了对应的逆向信息提取机制，可以从生成的可视化界面中反向提取出DSL模型信息。所谓的可视化设计不过是模型的两种表现形式（representation）之间的可逆转换而已。

可逆计算基于差量概念建立了一个完整的软件构造理论，它的核心思想来源于物理学和数学，因此具有非常广泛的普适性，具体的理论分析可以参见我此前的文章“可逆计算：下一代软件构造理论”。事实上k8s本身的架构原理也可以在可逆计算理论框架下进行解释：
