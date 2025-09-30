# NopCli Command-Line Tool

## Reverse Engineering

You can connect to the database via JDBC and retrieve metadata to generate an Excel-formatted data model definition.

```shell
java -jar nop-cli.jar reverse-db litemall -c=com.mysql.cj.jdbc.Driver --username=litemall --password=litemall123456 --jdbcUrl="jdbc:mysql://127.0.0.1:3306/litemall?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC"
```

The reverse-db command of nop-cli requires the [database schema name] parameter, e.g., litemall, and passes JDBC connection information through options such as jdbcUrl.

```
Usage: nop-cli reverse-db [-dhV] -c=<driverClassName> -j=<jdbcUrl>
                          [-o=<outputFile>] [-p=<password>] [-t=<table>]
                          -u=<username> <catalog>
Perform reverse engineering on the database and generate an Excel model file
      <catalog>             Database schema name
  -c, --driverClass=<driverClassName>
                            JDBC driver class
  -d, --dump                Dump output (defaults to outputting to the console)
  -h, --help                Show this help message and exit.
  -j, --jdbcUrl=<jdbcUrl>   JDBC connection
  -o, --output=<outputFile> Output file (defaults to printing to the console)
  -p, --password=<password> Database password
  -t, --table=<table>       Database table pattern, e.g., litemal% matches tables with the litemall prefix
  -u, --username=<username> Database username
  -V, --version             Print version information and exit.
```

## Code Generation

If you already have the Excel data model, you can use the gen command of the nop-cli tool to generate the initial project code.

```shell
java -jar nop-cli.jar gen -t=/nop/templates/orm model/app-mall.orm.xlsx
```

The generated content is as follows:

```
├─app-mall-api       Public API definitions and message definitions
├─app-mall-codegen   Auxiliary project for code generation; updates current project code based on the ORM model
├─app-mall-dao       Database entity definitions and ORM models
├─app-mall-service   GraphQL service implementation
├─app-mall-web       AMIS page files and View model definitions
├─app-mall-app       Packaging project for testing
├─deploy             Database DDL scripts generated from the Excel model
```

### Generate Only the DAO Module

If you only want to use NopORM without generating frontend code or GraphQL services, you can use the orm-dao template.

```shell
java -jar nop-cli.jar gen -t=/nop/templates/orm-dao -o=app-dao model/app-mall.orm.xlsx
```

### Use Your Own Custom Generation Templates

In addition to using the built-in code generation templates of the Nop platform, you can create your own template project. For details, see the project [bsin-codegen](https://gitee.com/canonical-entropy/bsin-codegen).
Bilibili video: [Principles of Reversible Computation and an Introduction to the Nop Platform with Q&A](https://www.bilibili.com/video/BV1u84y1w7kX/), starting at 44:20

```shell

java -Xbootclasspath/a:bsin-codegen-template/src/main/resources/ -jar nop-cli-2.0.0-BETA.1.jar  gen bsin-demo/model/bsin-demo.orm.xlsx -t=/bsin/templates/orm -o=bsin-demo
```

Use -Xbootclasspath/a:
bsin-codegen-template/src/main/resources/ to add external JARs or directories to the classpath, then reference the template files under the classpath via -t=/bsin/templates/orm to generate code.

## Dynamically Watch a Directory and Trigger Code Generation on Changes

With NopCli you can watch a specified directory and automatically execute script code when files under it change.

```shell
java -jar nop-cli.jar watch app-meta -e=taks/gen-web.xrun
```

The above configuration watches the app-meta directory and runs the gen-web.xrun script when files change.

In this script file, you can dynamically generate code via Xpl template tags such as GenWithDependsCache.

```xml

<c:unit xmlns:c="c" xmlns:run="run" xmlns:xpl="xpl">
  <run:GenWithCache xpl:lib="/nop/codegen/xlib/run.xlib"
                    srcDir="/meta/test" appName="Test"
                    targetDir="./target/gen"
                    tplDir="/nop/test/meta-web"/>
</c:unit>
```

The GenWithCache tag sets the srcDir and appName attributes, then executes the code generation template specified by tplDir; the output directory is specified by targetDir.

Dependency tracking is enabled during code generation. After the first run, when gen-web.xrun is triggered again, it will automatically check the dependency model files corresponding to the output files; regeneration occurs only when dependencies change, otherwise the task is skipped.

## Parse Excel Models and Export to JSON or XML

```
java -jar nop-cli.jar extract test.orm.xlsx -o=my.orm.json
```

The extract command detects the file extension and selects a registered parser to parse it, obtains a JSON object, and then exports it as a JSON file. If a corresponding xdef meta-model definition exists, you can also export as XML.

Use the -o output parameter to specify XML or JSON, e.g., -o=my.orm.xml; the file extension determines the export format.

## Generate Excel from JSON

```
java -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml
```

gen-file exports Excel according to the template file specified by -t. If the template file is imp.xml, it uses the export template associated with the import model. It can also be a report template like xpt.xlsx, in which case it exports according to the report model implementation. The object parsed from the JSON file corresponds to an object named entity during report export.

## Execute a Report Template and Export

Use the gen-file command with -t set to a report template file.

```
java -jar nop-cli.jar gen-file data.json -t=/my/test.xpt.xlsx -o=target/test.xlsx
```

The first parameter data.json is parsed into a Map and passed to the report template as the entity parameter. Use -o to specify the output file location and type.

## Execute an Orchestration Task

```
java -jar nop-cli.jar run-task my.task.xml -if=inputs.json
```

Read inputs.json as the task input parameters and run the my.task.xml orchestration model.

## Repackaging

```
java -jar nop-cli.jar repackage -i=app -o=my-tool.jar
```

The repackage command bundles the _vfs directory and the application.yaml and bootstrap.yaml files from the input directory into the current nop-cli.jar to produce a new executable JAR.

## Database Import/Export

```
java -jar nop-cl.jar export-db test.export-db.xml -o=data
```

Exports data from the database to the data directory according to export-db.xml configuration; supports CSV or SQL formats. During export you can perform field renaming, value transformations, etc., and select only certain fields.

```
java -jar nop-cl.jar import-db test.import-db.xml -i=data
```

Import data from the data directory into the specified database; during import you can deduplicate by keyFields and choose whether to allow updates or only inserts. You can perform field renaming and value transformations, and select only certain fields.

You can save the import status by specifying the -s parameter.

```
java -jar nop-cl.jar import-db test.import-db.xml -i=data -s=import-status.json
```

## convert Format Conversion

Convert between XML/JSON/YAML/XLSX formats for DSL models

### Command format

```bash
java -jar nop-cli.jar transform <inputFile>
    [-o|--output <outputFile>]
```

### Parameter descriptions

| Parameter        | Description                                                                      |
|-----------------|----------------------------------------------------------------------------------|
| `<inputFile>`   | Input file (must specify an explicit file type, e.g., app.orm.xml, app.orm.xlsx) |
| `-o, --output`  | Output file (must specify an explicit file type)                                 |

Implemented via DocumentConvertManager.

1. Register conversion logic between formats through the convert.xml registration file
2. DSL formats registered in register-model.xml can automatically convert bidirectionally
3. If A->B and B->C are registered, A->C conversions are automatically supported (only one level of inference is supported)

### Usage examples

 ```bash
 # XML → XLSX
 java -jar nop-cli.jar convert app.orm.xml  -o app.orm.xlsx

 # JSON → YAML
 java -jar nop-cli.jar convert app.orm.json -o app.orm.yaml
 ```

## Generate Page JSON Files from `page.yaml`

```
java -jar nop-cli.jar run scripts/render-pages.xrun -i="{moduleId:'app/demo'}" -o=target
```

The run command can execute xpl script files; the render-pages.xrun script calls PageProvider to generate page JSON files; -i specifies input parameters and -o specifies the output directory.

```xml
<!-- Contents of render-pages.xrun -->
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

The renderPagesTo function iterates over `_vfs/{moduleId}/pages/*/*.page.yaml` files and renders templates. In `page.yaml` you can include the View model via tags such as `<web:GenPage>`.

## FAQ

1. How to adjust log output level

```
java -Dquarkus.config.locations=application.yaml -jar nop-cli.jar gen-file my.orm.json -t=/nop/orm/imp/orm.imp.xml
```

The default configuration sets `quarkus.log.level=INFO` and `quarkus.log.category."io.nop".level=ERROR`. You can override the default Quarkus settings via an external application.yaml.

2. How to use an external application.yaml configuration file

Specify the external configuration file with `-Dnop.config.location=application.yaml`.

<!-- SOURCE_MD5:12b127f7e3f9bf0552a5372533ac9ab8-->
