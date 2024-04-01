# 1. 解析Excel文件得到Workbook对象

```
ExcelWorkbook wk = new ExcelWorkbookParser().parseFromVirtualPath(path);
或者
wk = new ExcelWorkbookParser().parseFromResource(new FileResource(file));
```

## 2. 解析Excel文件得到领域对象

根据imp.xml导入配置来实现自动解析Excel文件为领域模型对象。

```
FileResource resource = new FileResource(new File("c:/test.orm.xlsx"));
OrmModel ormModel = (OrmModel) new XlsxObjectLoader("/nop/orm/imp/orm.imp.xml").parseFromResource(resource);
```

## 3. 保存Workbook对象到文件中

```
new ExcelTemplate(workbook).generateToFile(file, DisabledEvalScope.INSTANCE);
```

## 4. 将Workbook转换为HTML格式

```
ITemplateOutput output = reportEngine.getRendererForExcel(workbook, XptConstants.RENDER_TYPE_HTML);
output.generateToFile(file, DisabledEvalScope.INSTANCE);

或者

ITextTemplateOutput output = reportEngine.getHtmlRenderer(workbook);
String html = output.generateText(DisabledEvalScope.INSTANCE);
```

## 5. 将领域对象保存为XLSX格式

参考GenOrmHelper.saveOrmToExcel函数

```
IEvalScope scope = XLang.newEvalScope();
scope.setLocalValue(null, XptConstants.VAR_ENTITY, ormModel);

ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel("/nop/orm/imp/orm.imp.xml");
ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
output.generateToFile(outputFile, scope);
```

## 6. 解析ExcelSheet得到领域对象

```
ExcelSheet configSheet = workbook.getSheet("Config");
XLangCompileTool compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

ImportModel importModel = ImportModelHelper.getImportModel(RuleConstants.IMP_PATH_RULE);
ImportSheetModel sheetModel = importModel.getSheet(RuleConstants.SHEET_NAME_CONFIG);
RuleModel rule = ImportModelHelper.parseSheet(sheetModel, configSheet, compileTool, RuleModel.class);
```
