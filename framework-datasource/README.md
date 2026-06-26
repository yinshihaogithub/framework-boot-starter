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

## 工程约束

- 模块不会主动创建 `DataSource`，只增强业务应用已有的数据访问栈。
- 分页插件固定使用 MySQL，`db-type` 不能为空且必须为 `MYSQL`，`max-limit` 必须大于 0，用于限制单页最大返回数量。
- 审计填充开启时，插入会填充空的 `createTime` 和 `updateTime`；更新会刷新 `updateTime`。
- 审计字段名可配置；审计开启时字段名不能为空，启动期会去除首尾空格并校验为合法 Java 字段名，实体没有对应 setter 时自动跳过。

## 装配行为

模块不会主动创建 `DataSource`，只增强业务应用已有的数据访问栈；业务自定义同类型 Bean 时默认实现让位。
