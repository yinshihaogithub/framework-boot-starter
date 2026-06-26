# framework-job

> 任务模块：XXL-JOB 执行器自动配置，提供 `JobHandler` 到 XXL-JOB handler registry 的适配和本地触发 facade。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-job</artifactId>
    <version>1.0.0</version>
</dependency>
```

`framework-starter` 不默认聚合本模块，业务需要接入 XXL-JOB 时单独引入。

## 配置

配置前缀：`framework.job`。

```yaml
framework:
  job:
    enabled: true
    admin-addresses: http://127.0.0.1:8080/xxl-job-admin
    app-name: order-service-job
    address:
    ip:
    port: 9999
    access-token:
    log-path: /data/applogs/xxl-job/jobhandler
    log-retention-days: 30
```

## 核心能力

| 类 | 说明 |
|---|---|
| `JobAutoConfiguration` | 注册 `XxlJobSpringExecutor`、`JobService` 和 `XxlJobHandlerRegistrar` |
| `JobProperties` | 管理 XXL-JOB executor 参数并快速校验 |
| `JobHandler` | 框架侧任务扩展点，暴露任务名和执行逻辑 |
| `XxlJobHandlerRegistrar` | 将 `JobHandler` Bean 注册为 XXL-JOB `IJobHandler` |
| `JobService` | 本地按任务名触发，适合自测、补偿和运维兜底 |
| `DefaultJobService` | 默认任务注册表，自动收集所有 `JobHandler` |

也可以直接使用 XXL-JOB 原生 `@XxlJob` 注解，本模块不会阻止原生方式。
`JobHandler.name()` 会 trim 后注册，只允许字母、数字、点、下划线和短横线，不能为空，不能重复；重复名称会在启动期快速失败，避免不同任务互相覆盖。
`JobService.run(name)` 查询前同样会 trim，适合人工补偿、运维兜底等入口直接传入表单参数。

## 使用示例

```java
@Bean
public JobHandler cleanJob() {
    return new JobHandler() {
        @Override
        public String name() {
            return "clean";
        }

        @Override
        public void execute() {
            cleanService.clean();
        }
    };
}
```

XXL-JOB admin 中的 JobHandler 名称填写 `clean`，调度触发时会执行上面的 `JobHandler`。

```java
@Autowired
private JobService jobService;

boolean triggered = jobService.run("clean");
```

## 装配行为

| 条件 | 行为 |
|---|---|
| `framework.job.enabled=false` | 默认不启动 XXL-JOB executor |
| `framework.job.enabled=true` | 属性绑定阶段校验 `admin-addresses`、`app-name`、`port`、`log-path` 和 `log-retention-days`，文本配置去除首尾空格后启动 `XxlJobSpringExecutor` |
| 存在业务 `JobHandler` Bean | 校验任务名后自动注册到 XXL-JOB handler registry，并加入本地 `JobService` |
| 业务自定义 `XxlJobSpringExecutor` / `JobService` | 默认实现自动让位 |

## MySQL 初始化

`framework-job` 本身是 executor 端模块，不在业务库创建任务表；如果项目需要一并部署 XXL-JOB admin，可以使用模块内置脚本：

`framework-job/src/main/resources/db/mysql/xxl_job_admin.sql`

该脚本面向 `xxl-job-admin` 使用的 MySQL 库，包含 `xxl_job_info`、`xxl_job_log`、`xxl_job_group`、`xxl_job_user` 等核心表和默认锁/管理员初始化。它不会放入根目录业务库聚合脚本，避免把外部调度中心表误建到业务库。

## 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `framework.job.enabled` | `false` | 是否启用 XXL-JOB executor |
| `framework.job.admin-addresses` | 无 | XXL-JOB admin 地址，启用时必填 |
| `framework.job.app-name` | `framework-job-executor` | executor 应用名 |
| `framework.job.address` | 空 | executor 注册地址，通常留空自动生成 |
| `framework.job.ip` | 空 | executor 注册 IP，通常留空自动识别 |
| `framework.job.port` | `9999` | executor 端口，启用时必须在 1-65535 |
| `framework.job.access-token` | 空 | 与 XXL-JOB admin 保持一致的访问令牌 |
| `framework.job.log-path` | `logs/xxl-job/jobhandler` | XXL-JOB 执行日志目录，启用时不能为空 |
| `framework.job.log-retention-days` | `30` | 日志保留天数，`-1` 表示不清理 |
