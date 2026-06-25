# framework-starter

> 聚合 Starter：一次引入常用脚手架能力。

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

如果业务只需要部分能力，建议按模块单独引入，避免无关依赖进入应用。

已聚合：core、web、auth、security、cache、lock、idempotent、crypto、log、rate-limiter、
mq、retry、tools、notify、local-message、excel、datasource、redis、feign、monitor、job、file。
