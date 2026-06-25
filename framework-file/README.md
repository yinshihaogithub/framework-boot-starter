# framework-file

> 文件模块：统一文件模型、本地存储默认实现、对象存储扩展点。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-file</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.file`。

```yaml
framework:
  file:
    enabled: true
    base-path: /tmp/framework-files
    public-url-prefix: /files
    max-size: 104857600
    allowed-extensions:
      - jpg
      - png
      - pdf
      - xlsx
```

业务方可以自定义 `FileStorageService` 接入 MinIO、OSS 或 COS。

## 使用示例

```java
@Autowired
private FileStorageService fileStorageService;

StoredFile file = fileStorageService.store(originalFilename, inputStream);
```

`StoredFile` 会返回 `key`、原始文件名、文件大小、访问 URL 和 `contentType`。本地默认实现会校验空输入、文件大小和扩展名，写入超限失败时会清理已写入的临时内容。

## 装配行为

默认注册 `LocalFileStorageService`；业务提供 `FileStorageService` Bean 后，默认本地存储自动让位。
