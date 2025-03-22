  
  # NopCli Command Line Tool
  
  ## Reverse Engineering
  
  You can use JDBC to connect to the database and generate metadata in Excel format.
  
  ```java
  java -jar nop-cli.jar reverse-db litemall -c=com.mysql.cj.jdbc.Driver --username=litemall --password=litemall123456 --jdbcUrl="jdbc:mysql://127.0.0.1:3306/litemall?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC"
  ```
  
  The `reverse-db` command in NopCli requires a parameter for the database name, such as `litemall`, followed by JDBC connection string options.
  
  ```bash
  Usage: nop-cli reverse-db [-dhV] -c=<driverClassName> -j=<jdbcUrl> [-o=<outputFile>] [-p=<password>] [-t=<table>] -u=<username> <catalog>
  ```
  
  Perform a reverse engineering analysis on the database to generate Excel model files.
  
  ### Database Tables
  ```plaintext
  ├── app-mall-api       # Exposed interfaces and message definitions
  ├── app-mall-codegen   # Code generation auxiliary project based on ORM model
  ├── app-mall-dao       # DAO layer and ORM model
  ├── app-mall-service   # GraphQL service implementation
  ├── app-mall-web       # AMIS page files and View models
  ├── app-mall-app       # Testing-related packaged project
  └── deploy             # SQL statements for database creation based on Excel model
  ```
  
  ### Generate Only DAO Layer
  
  If you only want to use NopORM without generating frontend code or GraphQL services, you can use the `orm-dao` template.
  
  ```java
  java -jar nop-cli.jar gen -t=/nop/templates/orm-dao -o=app-dao model/app-mall.orm.xlsx
  ```
  
  ### Custom Template Usage
  
  In addition to using Nop's built-in code generation templates, you can create your own template project. For details, refer to the [bsin-codegen](https://gitee.com/canonical-entropy/bsin-codegen) project.
  
  Bilibili Video: [Inverse Computing Principles and Introduction of Nop Platform](https://www.bilibili.com/video/BV1u84y1w7kX/) (starts at 44 minutes 20 seconds)
  
  
  ```shell
  java -Xbootclasspath/a:bsin-codegen-template/src/main/resources/ -jar nop-cli-2.0.0-BETA.1.jar gen bsin-demo/model/bsin-demo.orm.xlsx -t=/bsin/templates/orm -o=bsin-demo
  ```
  
  Through the `-Xbootclasspath/a` parameter, the `bsin-codegen-template/src/main/resources/` directory is added to the classpath. The `nop-cli-2.0.0-BETA.1.jar` jar file is then used to generate code from the `bsin-demo/model/bsin-demo.orm.xlsx` Excel file, using the template located at `/bsin/templates/orm` and outputting the generated code to `bsin-demo`.
  
  ## Dynamic File Monitoring
  The NopCli tool allows for dynamic monitoring of specific directories. When a file within the monitored directory changes, the corresponding script is automatically executed.
  
  ```shell
  java -jar nop-cli.jar watch app-meta -e=taks/gen-web.xrun
  ```
  
  This configuration monitors the `app-meta` directory. When any file within this directory changes, the `gen-web.xrun` script is triggered for execution.
  
  ## XML Configuration Example
  The following XML configuration demonstrates how to dynamically generate code using the `GenWithCache` tag and Xpl template tags:
  
  ```xml
  <c:unit xmlns:c="c" xmlns:run="run" xmlns:xpl="xpl">
    <run:GenWithCache xpl:lib="/nop/codegen/xlib/run.xlib"
                     srcDir="/meta/test"
                     appName="Test"
                     targetDir="./target/gen"
                     tplDir="/nop/test/meta-web"/>
  </c:unit>
  ```
  
  The `GenWithCache` tag sets the `srcDir`, `appName`, and other properties, then executes the code generation using the template located in `tplDir`. Dependencies are tracked during the generation process, ensuring that only changed files trigger a new build.
  
  ## Data Extraction from Excel
  ```shell
  java -jar nop-cli.jar extract test.orm.xlsx -o=my.orm.json
  ```
  
  The `extract` command identifies the file's suffix and selects the appropriate parser (in this case, JSON). The parsed data is then exported to `my.orm.json`.
  
  ## Exporting Data to Excel
  ```shell
  java -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml -o=target/test.xlsx
  ```
  
  Here, `gen-file` uses the `my.orm.json` input file and the template located at `/nop/orm/imp/orm.imp.xml` to generate an Excel file named `target/test.xlsx`. The output format is determined by the file suffix.
  
  ## Running Logic Arrangements
  ```shell
  java -jar nop-cli.jar run-task my.task.xml -if=inputs.json
  ```
  
  This command reads the `inputs.json` file as task input and executes the logic defined in `my.task.xml`.
  
  ## Repackaging Application Components
  ```shell
  java -jar nop-cli.jar repackage -i=app -o=my-tool.jar
  ```
  
  The `repackage` command packages all components from the input directory (`-i=app`) into a single jar file named `my-tool.jar`. This includes any necessary configuration files like `_vfs` and YAML files.

```java
java -jar nop-cl.jar export-db test.export-db.xml -o=data
```

The data from the database is exported to the `data` directory based on the configuration in `export-db.xml`. You can choose to export the data as CSV or SQL format. Field renaming, value transformations, etc., can be performed during the export process. Partial fields can be selected for export.

```java
java -jar nop-cl.jar import-db test.import-db.xml -i=data
```

Data from the `data` directory is imported into a specified database. During import, you can choose to remove duplicate entries based on `keyFields`, or allow updates, insertions only, etc. Field renaming and value transformations can be performed during import. Partial fields can be selected for import.

The `-s` parameter can be used to save the status of the import operation.

```java
java -jar nop-cl.jar import-db test.import-db.xml -i=data -s=import-status.json
```


## Generating a JSON File from `page.yaml`

```java
java -jar nop-cli.jar run scripts/render-pages.xrun -i="{moduleId:'app/demo'}" -o=target
```

The `render-pages.xrun` script is used to generate a JSON file. This script calls the `PageProvider` to render the page based on the configuration in `page.yaml`. The `-i` parameter specifies the input parameters, and the `-o` parameter specifies the output directory.


## XML Script Content from `render-pages.xrun`

```xml
<!-- Content of render-pages.xrun file -->
<c:script>
  import io.nop.web.page.PageProvider;
  import java.io.File;

  const pageProvider = new PageProvider();
  const options = {
    moduleId: moduleId,
    resolveI18n: true,
    useResolver: true,
    threadCount: 4
  };

  pageProvider.renderPagesTo(options, outputDir);
</c:script>
```

The `renderPagesTo` function traverses the files in the `_vfs/{moduleId}/pages/*/*.page.yaml` directory and renders them using templates. Page models can be imported into the `View` component via tags like `<web:GenPage>`.


## Common Issues

1. **Adjusting Log Output Level**

```java
java -Dquarkus.config.locations=application.yaml -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml
```

By default, `quarkus.log.level` is set to `INFO`, and `quarkus.log.category."io.nop".level` is set to `ERROR`. You can override these settings by specifying a configuration in `application.yaml`.

2. **Using an External `application.yaml` Configuration File**

```java
java -Dnop.config.location=application.yaml -jar nop-cli.jar run
```

The `-Dnop.config.location=application.yaml` parameter allows you to specify an external configuration file that will override the default Quarkus and NOP configurations.

