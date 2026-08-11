# Simple Secret Encrypt Starter

`simple-secret-springboot-starter-encrypt` 提供 AES-GCM、RSA-OAEP-SHA256、SM2、SM4-GCM、Base64
字符串处理，以及可选的 MyBatis 字段加密和 Servlet API v1 混合加密。模块默认关闭，不包含默认密钥，
不依赖其他 Simple Secret starter、Honeybee、Hutool、Lombok、Jackson、MyBatis-Plus、Redis 或认证模块。

## Maven 依赖

推荐先导入 Simple Secret BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.ss</groupId>
            <artifactId>simple-secret-common-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-encrypt</artifactId>
    </dependency>
</dependencies>
```

核心密码服务只传递 Bouncy Castle、Spring Boot 自动配置和 Spring 基础契约。Servlet、Spring WebMVC、
MyBatis 均为 optional：只使用核心服务的应用不会被迫引入 Web 或数据库框架。

## 密钥配置

总开关默认关闭。对称 key 必须是原始 key 字节的 Base64，不是密码字符串：

```yaml
simple-secret:
  encrypt:
    enabled: true
    keys:
      primary:
        secret-key: ${APP_AES_KEY_BASE64}
      api-request:
        private-key: ${API_REQUEST_PRIVATE_KEY_BASE64}
      api-response:
        public-key: ${API_RESPONSE_PUBLIC_KEY_BASE64}
```

生成 AES-256 key：

```bash
openssl rand -base64 32
```

生成 2048 位 RSA key pair：

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out request-private.pem
openssl pkey -in request-private.pem -pubout -out request-public.pem
```

配置支持 PEM 和无头尾的 Base64 DER。生产密钥应来自环境变量、容器 Secret 或 KMS，不应写入仓库。
同一 key id 不能同时配置对称材料和非对称材料。应用也可以完全替换默认 provider：

```java
import com.ss.encrypt.key.EncryptionKeyProvider;
import org.springframework.context.annotation.Bean;

@Bean
EncryptionKeyProvider encryptionKeyProvider(KmsClient kms) {
    return (keyId, algorithm) -> kms.loadEncryptionMaterial(keyId, algorithm);
}
```

`EncryptionMaterial.toString()` 永远只输出 `<redacted>`，但应用自己的 provider 仍不得记录实际 key。

## 核心 API

```java
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionRequest;
import com.ss.encrypt.core.EncryptionService;

EncryptionRequest request = new EncryptionRequest(
        EncryptionAlgorithm.AES_GCM,
        CipherEncoding.BASE64,
        "primary");

String ciphertext = encryptionService.encrypt("13800138000", request);
String plaintext = encryptionService.decrypt(ciphertext, request);
```

内置算法：

- `AES_GCM`：16/24/32 字节 key，随机 12 字节 nonce，128 位认证 tag。
- `RSA_OAEP_SHA256`：X.509 公钥加密、PKCS#8 私钥解密，主摘要和 MGF1 都使用 SHA-256。
- `SM2`：标准 X.509/PKCS#8 EC key。
- `SM4_GCM`：16 字节 key，随机 nonce 和认证 tag。
- `BASE64`：仅编码兼容，不提供机密性、完整性或身份认证，不能用于保护秘密。

AES/SM4 密文每次都不同，修改任意密文字节会导致解密失败。应用可提供 `StringEncryptor` Bean 按算法
覆盖内置实现，也可直接提供 `EncryptionService` Bean 接管全部路由。

## MyBatis 字段加密

应用自行引入 MyBatis Spring Boot starter；Encrypt starter 只发布 optional 的标准 MyBatis API：

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
```

```yaml
simple-secret:
  encrypt:
    enabled: true
    keys:
      database:
        secret-key: ${DATABASE_AES_KEY_BASE64}
    mybatis:
      enabled: true
      algorithm: AES_GCM
      encoding: BASE64
      key-id: database
```

```java
import com.ss.encrypt.annotation.EncryptField;

public class CustomerEntity {
    private Long id;

    @EncryptField
    private String phone;

    @EncryptField(keyId = "database")
    private String idCard;
}
```

只允许注解 `String` 字段。参数 interceptor 在 MyBatis 绑定参数前临时写入密文，并在成功或异常后恢复
原业务对象；结果 interceptor 在查询返回后解密。处理 Map、Iterable、数组、继承字段和循环引用，不扫描
`typeAliasesPackage`，也不依赖 MyBatis-Plus。

AES-GCM/SM4-GCM 使用随机 nonce，因此加密列不能直接用明文做 `=`、`IN`、唯一索引或排序。需要精确查询时，
应增加独立的 HMAC 索引列，并把 HMAC key 与加密 key 分离；本模块不会提供 ECB、固定 nonce 或其他
确定性弱加密来换取查询能力。

## Servlet API v1

Web 应用自行引入服务器和 MVC：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

```yaml
simple-secret:
  encrypt:
    enabled: true
    keys:
      api-request:
        private-key: ${API_REQUEST_PRIVATE_KEY_BASE64}
      api-response:
        public-key: ${API_RESPONSE_PUBLIC_KEY_BASE64}
    api:
      enabled: true
      header-name: X-Encrypt-Key
      request-key-id: api-request
      response-key-id: api-response
      max-request-size: 1MB
      max-response-size: 1MB
```

```java
import com.ss.encrypt.annotation.ApiEncrypt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecretController {

    @ApiEncrypt(response = true)
    @PostMapping("/secret")
    SecretView create(@RequestBody SecretCommand command) {
        return service.create(command);
    }
}
```

`request=true` 默认要求 POST、PUT、PATCH 请求使用加密 body；`response=false` 默认不加密响应。注解也可放在
Controller 类型上，方法注解优先。未标注的端点完全透传。

v1 协议：

1. 客户端生成随机 32 字节 AES key。
2. 请求 body 使用 AES-GCM 加密；AES key 使用服务端 request public key 和 RSA-OAEP-SHA256 加密。
3. 两者都编码为 `v1.` + Base64URL 无填充字符串；加密 key 放入 `X-Encrypt-Key`。
4. 服务端响应会生成另一把随机 AES key，使用配置的 response public key 包裹。
5. 客户端使用 response private key 解包响应 AES key，再验证并解密响应 body。

starter 不设置 `Access-Control-Allow-Origin`、`Access-Control-Allow-Methods` 或
`Access-Control-Expose-Headers`。浏览器应用需要读取加密 header 时，应在自己的 CORS 配置中只对可信来源
暴露 `X-Encrypt-Key`，不能使用隐式 `*`。

API filter 会保留业务状态码和业务 header，只替换 body、content type、content length 和加密 key header。
缺 header、错误版本、错误 key 或认证 tag 损坏默认返回 400；超过大小上限返回 413；错误响应不包含密码异常。
应用可提供 `ApiEncryptionFailureHandler` 统一为自己的错误格式。

## 安全和迁移边界

- API 加密不能替代 TLS、认证、授权、防重放、CSRF、防刷或限流；生产环境仍必须使用 HTTPS。
- 该协议没有内置时间戳或 nonce 存储，不阻止捕获后的完整请求重放。
- 不要在 SSE、文件下载、流式响应或大对象接口上使用 `@ApiEncrypt`，filter 必须在内存中缓冲完整 body。
- RSA 只适合包裹短 key，不能直接加密任意长 JSON；超长输入会失败关闭。
- Honeybee 的 AES/SM4 弱模式、RSA PKCS#1 v1.5 和默认示例私钥没有迁移。新密文不兼容旧 Honeybee 密文，
  数据库升级应通过受控离线迁移完成，不能在一次查询中猜测两种格式。
- Base64 不是加密；任何只用 Base64 的字段都等同于明文暴露。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-encrypt \
  clean verify
```

独立消费者验证：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f integration-tests/pom.xml -pl consumer-encrypt test
```
