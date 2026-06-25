# framework-datasource

> 数据源模块：MyBatis-Plus 分页插件、审计字段自动填充、数据源配置元数据。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-datasource</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置

配置前缀：`framework.datasource`。

```yaml
framework:
  datasource:
    enabled: true
    db-type: MYSQL
    max-limit: 1000
    audit:
      enabled: true
      create-time-field: createTime
      update-time-field: updateTime
```

## 核心能力

| 类 | 说明 |
|---|---|
| `MybatisPlusInterceptor` | 注册 MyBatis-Plus 分页插件 |
| `FrameworkMetaObjectHandler` | 自动填充创建/更新时间字段 |
| `DatasourceAutoConfiguration` | 有 MyBatis-Plus 依赖时自动增强数据访问 |

## 装配行为

模块不会主动创建 `DataSource`，只增强业务应用已有的数据访问栈；业务自定义同类型 Bean 时默认实现让位。
