# framework-crypto

> 加解密工具库：AES 对称加密、MD5/SHA 摘要、BCrypt 密码哈希、数据脱敏。

## 引入依赖

```xml
<dependency>
    <groupId>com.framework</groupId>
    <artifactId>framework-crypto</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 纯工具类，无需配置，引入即用。依赖 jbcrypt（BCrypt 密码哈希）。

## 工具类一览

| 类 | 说明 |
|---|---|
| `AesUtils` | AES-CBC 加解密（256位密钥，PKCS5Padding，Base64 编码） |
| `DigestUtils` | MD5 / SHA-256 / SHA-512 摘要 |
| `PasswordUtils` | BCrypt 密码哈希与校验 |
| `DesensitizeUtils` | 数据脱敏（手机/身份证/银行卡/邮箱/姓名） |

## 使用示例

### AES 加解密

```java
// 1. 生成密钥（应用启动时生成一次，持久化存储）
String key = AesUtils.generateKey();
// 输出: "xK8j2N..." (Base64 编码的 256 位密钥)

// 2. 加密
String cipherText = AesUtils.encrypt("敏感数据", key);
// 输出: "Y3J5cHRlZ..." (Base64)

// 3. 解密
String plainText = AesUtils.decrypt(cipherText, key);
// 输出: "敏感数据"
```

**适用场景**：数据库字段加密、接口数据传输加密。

### 摘要算法

```java
String md5 = DigestUtils.md5("hello");           // 5d41402abc4b2a76b9719d911017c592
String sha256 = DigestUtils.sha256("hello");      // 2cf24dba...
String sha512 = DigestUtils.sha512("hello");      // 9b71d224...
```

**适用场景**：数据完整性校验、文件指纹。不可逆，不适合密码存储（密码用 BCrypt）。

### 密码哈希（BCrypt）

```java
// 注册时：加密密码（自动加盐）
String hashed = PasswordUtils.hash("user123456");
// 输出: "$2a$10$N9qo8uLOickgx2Z..."（每次不同，含随机盐）

// 登录时：校验密码
boolean match = PasswordUtils.verify("user123456", hashed);
// true / false
```

**特点**：每次 hash 结果不同（随机盐），校验时用 BCrypt 算法对比。cost factor = 10。

### 数据脱敏

```java
// 手机号
DesensitizeUtils.phone("13812345678");      // 138****5678

// 身份证
DesensitizeUtils.idCard("110101199001011234"); // 110***********1234

// 银行卡
DesensitizeUtils.bankCard("6222123456781234"); // 6222 **** **** 1234

// 邮箱
DesensitizeUtils.email("zhangsan@qq.com");    // z***@qq.com

// 姓名
DesensitizeUtils.name("张三丰");               // 张**丰
DesensitizeUtils.name("张三");                 // 张*
DesensitizeUtils.name("张");                   // 张
```

**适用场景**：接口返回数据脱敏、日志打印脱敏。
