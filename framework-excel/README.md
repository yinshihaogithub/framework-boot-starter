# framework-excel

> Excel 模块：基于 EasyExcel 提供导入、导出、模板生成和行级错误收集。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-excel</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.excel`。

```yaml
framework:
  excel:
    enabled: true
    default-sheet-name: Sheet1
    max-rows: 100000
```

## 导出

导出会校验 `headClass`、`sheetName`、`rows` 和 `max-rows`，超出限制时抛出清晰的 `IllegalArgumentException`。

```java
@Autowired
private ExcelExportService excelExportService;

byte[] bytes = excelExportService.export(UserExcelRow.class, rows);
```

## 导入

导入会先校验输入流是否为常见 Excel 文件头（xlsx/xls），无效文件、空输入流和行解析异常都会进入 `ExcelImportResult.errors`。

```java
@Autowired
private ExcelImportService excelImportService;

ExcelImportResult<UserExcelRow> result =
        excelImportService.importExcel(inputStream, UserExcelRow.class);

if (result.hasErrors()) {
    return result.getErrors();
}
```
