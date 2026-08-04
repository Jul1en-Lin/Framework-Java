# Global Exception Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Servlet 服务和 WebFlux Gateway 增加统一异常响应，并让 common-security 通过 Spring Boot 3 自动装配生效。

**Architecture:** `lien-common-domain` 提供 `ServerException` 和统一的 `Result`/`EnumCode`；`lien-common-security` 提供只在 Servlet Web 应用中生效的 `@RestControllerAdvice`，通过 `AutoConfiguration.imports` 自动装配；`lien-gateway` 单独提供 `ErrorWebExceptionHandler`，因为 Gateway 使用 WebFlux，不能复用 Servlet 异常处理器。项目当前是 Spring Boot 3.0.2 / Spring Framework 6.0.x，因此 Servlet 侧使用 `NoHandlerFoundException`，WebFlux 侧使用 404 的 `ResponseStatusException`，不引用 6.1 才有的 `NoResourceFoundException`。

**Tech Stack:** Java 17, Spring Boot 3.0.2, Spring MVC, Spring WebFlux, Spring Cloud Gateway, Lombok, JUnit 5。

## Global Constraints

- 统一响应类型使用 `domain.Result`，不引入 `R` 或 `ResultCode` 别名。
- 统一错误码使用 `domain.EnumCode`。
- `ServerException` 必须继承 `RuntimeException`。
- Apache Commons Lang 只负责字符串判空；Servlet 异常处理使用 Spring MVC，Gateway 异常处理使用 WebFlux。
- Spring Boot 自动装配文件使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

---

### Task 1: Domain exception

**Files:**
- Create: `lien-common/lien-common-domain/src/main/java/domain/exception/ServerException.java`
- Test: `lien-common/lien-common-domain/src/test/java/domain/exception/ServerExceptionTest.java`

**Interfaces:**
- Consumes: `domain.EnumCode`。
- Produces: `ServerException(EnumCode)`, `ServerException(String)`, `ServerException(String, int)`, `getCode()`, inherited `getMessage()`。

- [ ] **Step 1: Write the failing test**

测试三个构造方法：枚举构造方法使用枚举 code/message；只传 message 时使用 `EnumCode.ERROR`；自定义构造方法保留传入 code/message。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lien-common/lien-common-domain -am -Dtest=ServerExceptionTest test`

Expected: 编译失败，因为 `ServerException` 尚不存在。

- [ ] **Step 3: Write minimal implementation**

```java
package domain.exception;

import domain.EnumCode;

public class ServerException extends RuntimeException {

    private final int code;

    public ServerException(EnumCode resultCode) {
        this(resultCode.getMsg(), resultCode.getCode());
    }

    public ServerException(String message) {
        this(message, EnumCode.ERROR.getCode());
    }

    public ServerException(String message, int code) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl lien-common/lien-common-domain -am -Dtest=ServerExceptionTest test`

Expected: PASS。

### Task 2: Servlet global exception handling and auto-configuration

**Files:**
- Create: `lien-common/lien-common-security/src/main/java/security/GlobalExceptionHandler.java`
- Create: `lien-common/lien-common-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `lien-common/lien-common-security/pom.xml`
- Modify: `lien-common/lien-common-domain/pom.xml` only if the domain dependency is not inherited by the security module
- Test: `lien-common/lien-common-security/src/test/java/security/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `Result`, `EnumCode`, `ServerException`。
- Produces: `@RestControllerAdvice` handlers for `ServerException`, `HttpRequestMethodNotSupportedException`, `MethodArgumentTypeMismatchException`, `NoHandlerFoundException`, `RuntimeException`, and `Exception`。

- [ ] **Step 1: Write failing handler tests**

直接调用 handler 方法，检查返回的 `Result` code/message 以及 `MockHttpServletResponse` 的 HTTP status；覆盖自定义异常、405、参数类型不匹配、404、运行时异常和系统异常。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lien-common/lien-common-security -am -Dtest=GlobalExceptionHandlerTest test`

Expected: 编译失败，因为 handler 尚不存在。

- [ ] **Step 3: Implement handler and auto-configuration**

使用 `@AutoConfiguration`、`@ConditionalOnWebApplication(type = SERVLET)` 和 `@RestControllerAdvice` 直接标记 `GlobalExceptionHandler`，imports 文件直接写入 `security.GlobalExceptionHandler`，不再增加中间的 `SecurityAutoConfiguration`。响应码由业务码前 3 位映射，例如 `400000 -> HttpStatus 400`。Spring Boot 3.0.2 不支持参考代码的 `NoResourceFoundException`，使用 `NoHandlerFoundException`。

`SecurityAutoConfiguration` 使用 `@AutoConfiguration`、`@ConditionalOnWebApplication(type = SERVLET)` 和 `@Import(GlobalExceptionHandler.class)`，imports 文件只写：

```text
security.SecurityAutoConfiguration
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl lien-common/lien-common-security -am -Dtest=GlobalExceptionHandlerTest test`

Expected: PASS。

### Task 3: Gateway WebFlux exception handling

**Files:**
- Create: `lien-gateway/src/main/java/com/lien/gateway/handler/GatewayExceptionHandler.java`
- Create: `lien-gateway/src/main/java/com/lien/gateway/util/ServletUtil.java`
- Modify: `lien-gateway/pom.xml`
- Test: `lien-gateway/src/test/java/com/lien/gateway/handler/GatewayExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ServerException`, `Result`, `EnumCode`, `ServerWebExchange`。
- Produces: `ErrorWebExceptionHandler#handle(ServerWebExchange, Throwable)`，返回 JSON `Result` 并设置 HTTP status。

- [ ] **Step 1: Write failing WebFlux handler tests**

使用 `MockServerWebExchange` 覆盖 `ServerException`、404 `ResponseStatusException`、普通异常和已提交响应；检查 status、Content-Type、JSON code/message。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lien-gateway -am -Dtest=GatewayExceptionHandlerTest test`

Expected: 编译失败，因为 handler 尚不存在。

- [ ] **Step 3: Implement handler and response writer**

Gateway handler 使用 `@Order(-1)`、`@Configuration`、`ErrorWebExceptionHandler`；已提交响应直接返回 `Mono.error(ex)`。业务异常返回自身 code/message，404 返回 `EnumCode.SERVICE_NOT_FOUND`，其他异常返回 `EnumCode.ERROR`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl lien-gateway -am -Dtest=GatewayExceptionHandlerTest test`

Expected: PASS。

### Task 4: Dependency wiring and final verification

**Files:**
- Modify: `lien-common/lien-common-security/pom.xml`
- Modify: `lien-gateway/pom.xml`
- Modify: `lien-mstemplate/lien-mstemplate-service/pom.xml` if the Servlet service should consume common-security auto-configuration

- [ ] **Step 1: Add only required module dependencies**

Security needs `lien-common-domain` and `spring-webmvc`; Gateway needs `lien-common-domain` and Jackson/WebFlux classes already supplied by its Gateway starter。

- [ ] **Step 2: Run module tests**

Run: `mvn -pl lien-common/lien-common-core,lien-common/lien-common-domain,lien-common/lien-common-security,lien-gateway -am test`

Expected: 所有模块编译通过，测试无失败。

- [ ] **Step 3: Check package resource and dependency tree**

Run: `find lien-common/lien-common-security/src/main/resources -type f -print` and `mvn -pl lien-common/lien-common-security,lien-gateway dependency:tree`

Expected: 能看到 `AutoConfiguration.imports`，并且没有因为引用 Servlet 类型而把 MVC 依赖错误带进 Gateway。
