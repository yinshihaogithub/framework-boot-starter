# framework-tools

> 通用工具库：雪花 ID 生成器、树形结构工具、日期工具。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-tools</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 纯工具类，无外部依赖，引入即用。

## 工具类一览

| 类 | 说明 |
|---|---|
| `SnowflakeIdGenerator` | 雪花算法分布式 ID 生成器 |
| `TreeUtils` | 树形结构工具（列表转树/树转列表） |
| `DateUtils` | 日期时间工具（格式化/解析/转换/差值） |

## SnowflakeIdGenerator 雪花 ID

### ID 结构

```
| 1bit | 41bit 时间戳 | 10bit 机器ID | 12bit 序列号 |
  符号   ~69年         1024台机器    每毫秒4096个
```

- 起始时间：2024-01-01 00:00:00（`EPOCH = 1704067200000L`）
- 时钟回拨检测：回拨时抛出异常，拒绝生成 ID

### 使用示例

```java
// 初始化（workerId 范围 0-1023）
SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);

// 生成 ID
long id = generator.nextId();
// 输出: 1234567890123456789（趋势递增）
```

**适用场景**：订单号、用户ID、流水号等需要全局唯一且趋势递增的场景。

## TreeUtils 树形工具

### 列表转树

```java
List<Menu> menus = menuMapper.selectAll();  // 平铺列表

List<Menu> tree = TreeUtils.buildTree(
    menus,
    Menu::getId,                  // ID 提取器
    Menu::getParentId,            // 父 ID 提取器
    Menu::setChildren,            // 子节点设置器
    m -> m.getParentId() == null  // 根节点判断
);
```

### 树转列表（扁平化）

```java
List<Menu> tree = getMenuTree();
List<Menu> flatList = TreeUtils.flatten(tree, Menu::getChildren);
```

## DateUtils 日期工具

### 格式化

```java
String datetime = DateUtils.formatDateTime(LocalDateTime.now());  // 2026-06-25 09:30:00
String date = DateUtils.formatDate(LocalDate.now());              // 2026-06-25
String custom = DateUtils.format(LocalTime.now(), "HH:mm");       // 09:30
```

### 解析

```java
LocalDateTime dt = DateUtils.parseDateTime("2026-06-25 09:30:00");
LocalDate d = DateUtils.parseDate("2026-06-25");
```

### 类型转换

```java
// LocalDateTime → Date
Date date = DateUtils.toDate(LocalDateTime.now());

// Date → LocalDateTime
LocalDateTime ldt = DateUtils.toLocalDateTime(new Date());

// 时间戳转换
long epochMilli = DateUtils.toEpochMilli(LocalDateTime.now());
LocalDateTime fromTs = DateUtils.fromEpochMilli(1719235200000L);
```

### 当前时间

```java
// 获取当前时间（默认时区 Asia/Shanghai）
LocalDateTime now = DateUtils.now();
```

### 差值计算

```java
long days = DateUtils.diffDays(start, end);     // 相差天数
long hours = DateUtils.diffHours(start, end);   // 相差小时
long minutes = DateUtils.diffMinutes(start, end); // 相差分钟
```

## 常量

```java
DateUtils.PATTERN_DATETIME  // "yyyy-MM-dd HH:mm:ss"
DateUtils.PATTERN_DATE      // "yyyy-MM-dd"
DateUtils.PATTERN_TIME      // "HH:mm:ss"
DateUtils.DEFAULT_ZONE      // Asia/Shanghai
```
