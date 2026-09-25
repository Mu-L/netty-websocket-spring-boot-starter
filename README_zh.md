netty-websocket-spring-boot-starter [![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
===================================

[English Docs](https://github.com/YeautyYE/netty-websocket-spring-boot-starter/blob/master/README.md)

### 简介
本项目帮助你在spring-boot中使用Netty来开发WebSocket服务器，并像spring-websocket的注解开发一样简单

### 要求
- JDK 17 或更高；CI 覆盖 JDK 17、21。
- Spring Boot 4 / Spring Framework 7；当前测试基线为 Spring Boot 4.1.1。
- starter 默认使用 Netty 4.2.18.Final；同时测试 Boot 4.1.1 管理的 4.2.17.Final。其他版本组合请先验证。
- Spring Boot 3 用户请继续使用 0.13.x；升级前阅读下方迁移说明。

### 快速开始

- 添加依赖:

```xml
	<dependency>
		<groupId>org.yeauty</groupId>
		<artifactId>netty-websocket-spring-boot-starter</artifactId>
		<version>1.0.0</version>
	</dependency>
```

- 在现有 Spring Boot 应用的主包或子包中添加端点。只需上面的依赖，starter 会传递引入 Spring Boot 基础依赖；已有 Web 应用也可直接接入，无需额外添加 `@Component` 或手工注册 exporter。

```java
package com.example.demo;

import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

@ServerEndpoint(path = "/ws", port = "${ws.port:8081}")
public class MyWebSocket {
    @OnMessage
    public void onMessage(Session session, String message) {
        session.sendText(message);
    }
}
```

- 启动包含 `@SpringBootApplication` 的应用，浏览器控制台运行：

```javascript
const ws = new WebSocket("ws://127.0.0.1:8081/ws");
ws.onopen = () => ws.send("hello");
ws.onmessage = event => console.log(event.data); // hello
```

WebSocket 使用独立的 Netty 监听端口；`server.port` 不会自动设置它。示例默认 8081，可用 `ws.port` 覆盖；注解未声明端口时仍为 80，以保持兼容。不同服务不要占用同一端口。端点在主包之外时，在配置类添加 `@EnableWebSocket(scanBasePackages = "com.example.endpoints")`。

### 注解
###### @ServerEndpoint 
> 当ServerEndpointExporter类通过Spring配置进行声明并被使用，它将会去扫描带有@ServerEndpoint注解的类
> 被注解的类将被注册成为一个WebSocket端点
> 所有的[配置项](#%E9%85%8D%E7%BD%AE)都在这个注解的属性中 ( 如:`@ServerEndpoint("/ws")` )

###### @BeforeHandshake 
> 当有新的连接进入时，对该方法进行回调
> 注入参数的类型:Session、HttpHeaders...

###### @OnOpen 
> 当有新的WebSocket连接完成时，对该方法进行回调
> 注入参数的类型:Session、HttpHeaders...

###### @OnClose
> 当有WebSocket连接关闭时，对该方法进行回调
> 注入参数的类型:Session

###### @OnError
> 当有WebSocket抛出异常时，对该方法进行回调
> 注入参数的类型:Session、Throwable

###### @OnMessage
> 当接收到字符串消息时，对该方法进行回调
> 注入参数的类型:Session、String

###### @OnBinary
> 当接收到二进制消息时，对该方法进行回调
> 注入参数的类型:Session、byte[]

###### @OnEvent
> 当接收到Netty的事件时，对该方法进行回调
> 注入参数的类型:Session、Object

### 配置
> 所有的配置项都在这个注解的属性中

| 属性  | 默认值 | 说明 
|---|---|---
|path|"/"|WebSocket的path,也可以用`value`来设置
|host|"0.0.0.0"|WebSocket的host,`"0.0.0.0"`即是所有本地地址
|port|80|WebSocket绑定端口号。如果为0，则使用随机端口(端口获取可见 [多端点服务](#%E5%A4%9A%E7%AB%AF%E7%82%B9%E6%9C%8D%E5%8A%A1))
|bossLoopGroupThreads|1|bossEventLoopGroup的线程数
|workerLoopGroupThreads|0|workerEventLoopGroup的线程数
|useCompressionHandler|false|是否添加WebSocketServerCompressionHandler到pipeline
|optionConnectTimeoutMillis|30000|与Netty的`ChannelOption.CONNECT_TIMEOUT_MILLIS`一致
|optionSoBacklog|128|与Netty的`ChannelOption.SO_BACKLOG`一致
|childOptionWriteSpinCount|16|与Netty的`ChannelOption.WRITE_SPIN_COUNT`一致
|childOptionWriteBufferHighWaterMark|64*1024|与Netty的`ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK`一致,但实际上是使用`ChannelOption.WRITE_BUFFER_WATER_MARK`
|childOptionWriteBufferLowWaterMark|32*1024|与Netty的`ChannelOption.WRITE_BUFFER_LOW_WATER_MARK`一致,但实际上是使用 `ChannelOption.WRITE_BUFFER_WATER_MARK`
|childOptionSoRcvbuf|-1(即未设置)|与Netty的`ChannelOption.SO_RCVBUF`一致
|childOptionSoSndbuf|-1(即未设置)|与Netty的`ChannelOption.SO_SNDBUF`一致
|childOptionTcpNodelay|true|与Netty的`ChannelOption.TCP_NODELAY`一致
|childOptionSoKeepalive|false|与Netty的`ChannelOption.SO_KEEPALIVE`一致
|childOptionSoLinger|-1|与Netty的`ChannelOption.SO_LINGER`一致
|childOptionAllowHalfClosure|false|与Netty的`ChannelOption.ALLOW_HALF_CLOSURE`一致
|readerIdleTimeSeconds|0|与`IdleStateHandler`中的`readerIdleTimeSeconds`一致，并且当它不为0时，将在`pipeline`中添加`IdleStateHandler`
|writerIdleTimeSeconds|0|与`IdleStateHandler`中的`writerIdleTimeSeconds`一致，并且当它不为0时，将在`pipeline`中添加`IdleStateHandler`
|allIdleTimeSeconds|0|与`IdleStateHandler`中的`allIdleTimeSeconds`一致，并且当它不为0时，将在`pipeline`中添加`IdleStateHandler`
|maxFramePayloadLength|65536|最大允许帧载荷长度
|maxMessagePayloadLength|1048576（1 MiB）|解压后的完整消息上限（字节，包含所有分片）；同时约束解压工作区（见生命周期说明）。必须大于 0，较大的业务消息可显式调高。
|useEventExecutorGroup|true|将消息、关闭和事件回调交给业务线程池；握手和打开回调仍在 I/O 线程执行
|eventExecutorGroupThreads|16|eventExecutorGroup的线程数
|sslKeyPassword|""(即未设置)|与spring-boot的`server.ssl.key-password`一致
|sslKeyStore|""(即未设置)|与spring-boot的`server.ssl.key-store`一致
|sslKeyStorePassword|""(即未设置)|与spring-boot的`server.ssl.key-store-password`一致
|sslKeyStoreType|""(即未设置)|与spring-boot的`server.ssl.key-store-type`一致
|sslTrustStore|""(即未设置)|与spring-boot的`server.ssl.trust-store`一致
|sslTrustStorePassword|""(即未设置)|与spring-boot的`server.ssl.trust-store-password`一致
|sslTrustStoreType|""(即未设置)|与spring-boot的`server.ssl.trust-store-type`一致
|corsOrigins|{}(即未设置)|与spring-boot的`@CrossOrigin#origins`一致
|corsAllowCredentials|""(即未设置)|与spring-boot的`@CrossOrigin#allowCredentials`一致

### 通过application.properties进行配置
> 所有参数皆可使用`${...}`占位符获取`application.properties`中的配置。如下：

- 首先在`@ServerEndpoint`注解的属性中使用`${...}`占位符
```java
@ServerEndpoint(host = "${ws.host}",port = "${ws.port}")
public class MyWebSocket {
    ...
}
```
- 接下来即可在`application.properties`中配置
```
ws.host=0.0.0.0
ws.port=8081
```

### 自定义Favicon
配置favicon的方式与spring-boot中完全一致。只需将`favicon.ico`文件放到classpath的根目录下即可。如下:
```
src/
  +- main/
    +- java/
    |   + <source code>
    +- resources/
        +- favicon.ico
```

### 自定义错误页面
配置自定义错误页面的方式与spring-boot中完全一致。你可以添加一个 `/public/error` 目录，错误页面将会是该目录下的静态页面，错误页面的文件名必须是准确的错误状态或者是一串掩码,如下：
```
src/
  +- main/
    +- java/
    |   + <source code>
    +- resources/
        +- public/
            +- error/
            |   +- 404.html
            |   +- 5xx.html
            +- <other public assets>
```

### 多端点服务
- 每个端点类添加 `@ServerEndpoint` 即可，不要求 `@Component`。
- 相同 host、port 的不同 path 共用一个 Netty 服务；线程、TLS、压缩、大小限制等服务配置应保持一致。不同 host 或 port 使用不同服务。
- 同一 host 上 `port="0"` 的端点共用一个随机端口；不同 host 分别分配。启动完成后使用 `ServerEndpointConfig.getRandomPort(host)` 查询，关闭后缓存清理。
- `0.0.0.0` 绑定所有本地地址；避免再用另一个 host 在同一端口创建冲突监听。

### 参数绑定与握手
使用本项目的 `org.yeauty.annotation.RequestParam` 和 `PathVariable`，不要混用 Spring MVC 或 Jakarta 的同名注解。显式声明名称最稳妥：

```java
@ServerEndpoint(path = "/ws/{room}", port = "8081")
public class RoomSocket {
    @OnOpen
    public void onOpen(Session session,
                       @PathVariable("room") String room,
                       @RequestParam(value = "name", defaultValue = "guest") String name) {
        session.sendText(room + ":" + name);
    }
}
```

连接 `ws://127.0.0.1:8081/ws/lobby?name=Alice`；缺省或空 `name` 使用 `guest`。此例的注解和 `Session` 导入与快速开始一致，另加 `OnOpen`、`PathVariable`、`RequestParam`。
- `required=true` 是默认值：缺少参数时拒绝握手并返回 HTTP 400；转换失败也返回 400。`required=false` 可返回 `null`，可选数字使用包装类型（例如 `Integer`）。
- `defaultValue` 适用于缺省或空的单值参数，并隐式取消必填；普通 String 的空字符串仍是存在的值，业务非空校验由应用完成。
- 多值查询使用 `@RequestParam("tag") List<String>`；全部参数使用 `@RequestParam Map<String, String>` 或 `MultiValueMap<String, String>`。
- 显式注解的参数优先于消息正文绑定，正文使用未标注的 `String` 参数。
- 省略注解名称时，应用自己的 Maven/Gradle 编译也需开启 `-parameters`；starter 的编译设置不传递给应用。
- `@BeforeHandshake` 可调用 `session.close()` 拒绝连接；回调抛异常会终止握手，而不是继续升级。子协议通过 `session.setSubprotocols("chat")` 声明，服务端只选择客户端提供且支持的一个协议。

### 线程、消息大小与生命周期
`@BeforeHandshake`、`@OnOpen` 在 I/O 线程执行，应快速返回。默认 `useEventExecutorGroup=true` 时，消息、关闭和事件回调在业务线程池执行；业务线程同样需要容量管理，避免长时间阻塞。`@OnError` 所在线程取决于错误来源。

`maxFramePayloadLength` 限制单帧；新增的 `maxMessagePayloadLength` 默认 1 MiB，限制解压后的单条消息及分片聚合，解压工作区上限为消息上限加上 `max(8192, 2 × maxFramePayloadLength)` 字节（封顶 `Integer.MAX_VALUE`），用于满足解码器的分配请求；最终消息仍严格按消息上限检查。超过聚合或消息上限会关闭连接（状态码 1009）；压缩数据解码失败也会断开。大消息应用应同时评估并配置这两个限制，例如：

```java
@ServerEndpoint(path = "/upload", port = "8081",
        maxFramePayloadLength = "1048576",
        maxMessagePayloadLength = "8388608")
```

端口绑定、TLS 初始化失败会让应用启动失败。关闭 Spring 容器会停止监听、发送 WebSocket 关闭帧、关闭连接，再按网络线程池和业务线程池的顺序释放资源。每个监听服务等待预算为 5 秒；超时记录错误，迟到的网络事件仍先于业务线程池的停止请求。长时间阻塞的用户回调可能延长实际资源释放时间；部署终止宽限期应包含这些时间。不要依赖 `bye` 文本消息。

### TLS 属性映射
本组件使用独立监听器，`server.ssl.*` 不会自动应用。需要复用配置时显式映射：

```java
@ServerEndpoint(path = "/ws", port = "${ws.port:8443}",
        sslKeyStore = "${server.ssl.key-store}",
        sslKeyStorePassword = "${server.ssl.key-store-password}",
        sslKeyStoreType = "${server.ssl.key-store-type:PKCS12}")
```

### 从 0.13.x 升级到 1.0.0
1. 先把应用升级到 Spring Boot 4 / Spring Framework 7，使用 JDK 17 或更高。
2. 更新依赖版本；自动配置改由 `AutoConfiguration.imports` 注册，正常 Boot 应用无需手工声明 exporter。
3. 检查此前依赖“缺少必填参数仍传入 null”的回调：真正可选的参数请标记 `required=false`，或提供默认值。
4. 大于 1 MiB 的消息请显式提高 `maxMessagePayloadLength`；同一监听地址的多个端点使用一致配置。
5. 检查子协议协商：只返回服务端明确支持的协议，不再原样回显整个客户端列表。
6. Boot BOM 可能管理 Netty 为不同补丁版本；如确需覆盖，应通过应用的 Netty BOM/版本属性统一整套模块，避免混搭单个模块。

### 构建与验证
维护环境：JDK 17 或 21。仓库附带 Maven Wrapper，固定 Maven 3.9.11；Windows 使用 `mvnw.cmd`。以下命令从仓库根目录执行：

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Pconsumer-test verify
./mvnw -B -ntp -Prelease -Dgpg.skip=true verify
```

消费者测试从打包后的 starter 创建独立 Boot 应用，验证仅一个应用依赖即可启动、收发消息和关闭。发布命令、签名及 Central Portal 配置见 [RELEASING.md](RELEASING.md)。上面的 release 验证跳过签名且不上传。

---
### 更新日志

#### 0.8.0

- 自动装配

#### 0.9.0

- 通过`@PathVariable`支持RESTful风格中获取参数
- 通过`@RequestParam`实现请求中query的获取参数
- 移除原来的ParameterMap,用`@RequestParam MultiValueMap`代替
- 新增 `@BeforeHandshake` 注解，可在握手之前对连接进行关闭
- 在`@BeforeHandshake`事件中可设置子协议
- 去掉配置端点类上的 `@Component`
- 更新`Netty`版本到 `4.1.44.Final`

#### 0.9.1

- 修复bug：当使用`@RequestParam MultiValueMap`时获取的对象为null
- 更新`Netty`版本到 `4.1.45.Final`

#### 0.9.2

- 兼容 0.8.0 以下版本，可以手动装配`ServerEndpointExporter`对象

#### 0.9.3

- 修复bug：当没有 `@BeforeHandshake`时会出现空指针异常

#### 0.9.4

- 修复bug：当没有 `@BeforeHandshake`时 `OnOpen`中的`Session`为null.

#### 0.9.5

- 修复bug：`OnError`事件中的`Throwable`为null.

#### 0.10.0

- 修改`bossLoopGroupThreads`默认值为1
- 支持通过配置`useEventExecutorGroup`让同步且耗时的业务逻辑在EventExecutorGroup中执行，防止I/O线程被耗时的任务阻塞
- 支持SSL
- 支持跨域
- 更新`Netty`版本到 `4.1.59.Final`

#### 0.11.0

- 当`ServerEndpoint`类被cglib代理时(如aop增强)，仍能正常运行

#### 0.12.0

- `@EnableWebSocket`增加`scanBasePackages`属性
- `@ServerEndpoint`不再依赖`@Component`
- 更新`Netty`版本到 `4.1.67.Final`

#### 0.13.0

- 修复了无法进行WebSocket压缩的问题
- 升级支持spring-boot3
- 响应头带上前端的`Sec-WebSocket-Protocol`
- 关闭行为以当前生命周期说明为准；客户端不应依赖 `bye` 文本消息
- 更新`Netty`版本到 `4.1.118.Final`

#### 1.0.0

- 升级支持spring-boot4（Spring Framework 7）；最低 jdk 为 17，与 spring-boot 4 的基线一致，本项目在 JDK 17、21 上构建与测试
- 更新`Netty`版本到 `4.2.18.Final`
- 自动配置注册方式由 `META-INF/spring.factories` 迁移到 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 适配Spring 7 / Netty 4.2 中被移除或废弃的API

- starter 传递引入 Spring Boot 基础依赖，新增独立消费者集成测试及 JDK/Netty CI 矩阵。
- 修复必填参数、空值默认参数、字符串消息参数绑定和子协议协商；握手异常终止升级。
- 增加可配置的完整消息大小上限及解压缓冲区限制。
- 修复上下文关闭时的执行器顺序、端口冲突启动失败、扫描回退和参数别名。
- 更新中英文接入与迁移文档；发布迁移至 Central Portal，打包项目 LICENSE。
