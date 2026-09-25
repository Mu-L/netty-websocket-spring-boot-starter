netty-websocket-spring-boot-starter [![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
===================================

[中文文档](https://github.com/YeautyYE/netty-websocket-spring-boot-starter/blob/master/README_zh.md) (Chinese Docs)

### About
netty-websocket-spring-boot-starter will help you develop WebSocket server by using Netty in spring-boot,it is easy to develop by using annotation like spring-websocket 

### Requirement
- JDK 17 or later; CI covers JDK 17 and 21.
- Spring Boot 4 / Spring Framework 7; the tested baseline is Spring Boot 4.1.1.
- The starter defaults to Netty 4.2.18.Final; Boot 4.1.1's managed 4.2.17.Final is also tested. Validate other combinations before use.
- Spring Boot 3 users should stay on 0.13.x; read the migration notes before upgrading.

### Quick Start

- add Dependencies:

```xml
	<dependency>
		<groupId>org.yeauty</groupId>
		<artifactId>netty-websocket-spring-boot-starter</artifactId>
		<version>1.0.0</version>
	</dependency>
```

- Add this endpoint in your existing Spring Boot application's package or a subpackage. The dependency above includes Spring Boot basics transitively. Existing web applications can use it too; no extra `@Component` or manual exporter registration is needed.

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

- Start your `@SpringBootApplication` and run this in the browser console:

```javascript
const ws = new WebSocket("ws://127.0.0.1:8081/ws");
ws.onopen = () => ws.send("hello");
ws.onmessage = event => console.log(event.data); // hello
```

WebSocket uses its own Netty listener; `server.port` does not configure it automatically. This example defaults to 8081, overridable with `ws.port`; the annotation's default remains 80 for compatibility. Use separate ports for separate servers. For endpoints outside the main package, add `@EnableWebSocket(scanBasePackages = "com.example.endpoints")` to a configuration class.

### Annotation
###### @ServerEndpoint 
> declaring `ServerEndpointExporter` in Spring configuration,it will scan for WebSocket endpoints that be annotated  with `ServerEndpoint` .
> beans that be annotated with `ServerEndpoint` will be registered as a WebSocket endpoint.
> all [configurations](#configuration) are inside this annotation ( e.g. `@ServerEndpoint("/ws")` )

###### @BeforeHandshake 
> when there is a connection accepted,the method annotated with `@BeforeHandshake` will be called  
> classes which be injected to the method are:Session,HttpHeaders...

###### @OnOpen 
> when there is a WebSocket connection completed,the method annotated with `@OnOpen` will be called  
> classes which be injected to the method are:Session,HttpHeaders...

###### @OnClose
> when a WebSocket connection closed,the method annotated with `@OnClose` will be called
> classes which be injected to the method are:Session

###### @OnError
> when a WebSocket connection throw Throwable, the method annotated with `@OnError` will be called
> classes which be injected to the method are:Session,Throwable

###### @OnMessage
> when a WebSocket connection received a message,the method annotated with `@OnMessage` will be called
> classes which be injected to the method are:Session,String

###### @OnBinary
> when a WebSocket connection received the binary,the method annotated with `@OnBinary` will be called
> classes which be injected to the method are:Session,byte[]

###### @OnEvent
> when a WebSocket connection received the event of Netty,the method annotated with `@OnEvent` will be called
> classes which be injected to the method are:Session,Object

### Configuration
> all configurations are configured in `@ServerEndpoint`'s property 

| property  | default | description 
|---|---|---
|path|"/"|path of WebSocket can be aliased for `value`
|host|"0.0.0.0"|host of WebSocket.`"0.0.0.0"` means all of local addresses
|port|80|port of WebSocket。if the port equals to 0，it will use a random and available port(to get the port [Multi-Endpoint](#multi-endpoint))
|bossLoopGroupThreads|1|num of threads in bossEventLoopGroup
|workerLoopGroupThreads|0|num of threads in workerEventLoopGroup
|useCompressionHandler|false|whether add WebSocketServerCompressionHandler to pipeline
|optionConnectTimeoutMillis|30000|the same as `ChannelOption.CONNECT_TIMEOUT_MILLIS` in Netty
|optionSoBacklog|128|the same as `ChannelOption.SO_BACKLOG` in Netty
|childOptionWriteSpinCount|16|the same as `ChannelOption.WRITE_SPIN_COUNT` in Netty
|childOptionWriteBufferHighWaterMark|64*1024|the same as `ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK` in Netty,but use `ChannelOption.WRITE_BUFFER_WATER_MARK` in fact.
|childOptionWriteBufferLowWaterMark|32*1024|the same as `ChannelOption.WRITE_BUFFER_LOW_WATER_MARK` in Netty,but use `ChannelOption.WRITE_BUFFER_WATER_MARK` in fact.
|childOptionSoRcvbuf|-1(mean not set)|the same as `ChannelOption.SO_RCVBUF` in Netty
|childOptionSoSndbuf|-1(mean not set)|the same as `ChannelOption.SO_SNDBUF` in Netty
|childOptionTcpNodelay|true|the same as `ChannelOption.TCP_NODELAY` in Netty
|childOptionSoKeepalive|false|the same as `ChannelOption.SO_KEEPALIVE` in Netty
|childOptionSoLinger|-1|the same as `ChannelOption.SO_LINGER` in Netty
|childOptionAllowHalfClosure|false|the same as `ChannelOption.ALLOW_HALF_CLOSURE` in Netty
|readerIdleTimeSeconds|0|the same as `readerIdleTimeSeconds` in `IdleStateHandler` and add `IdleStateHandler` to `pipeline` when it is not 0
|writerIdleTimeSeconds|0|the same as `writerIdleTimeSeconds` in `IdleStateHandler` and add `IdleStateHandler` to `pipeline` when it is not 0
|allIdleTimeSeconds|0|the same as `allIdleTimeSeconds` in `IdleStateHandler` and add `IdleStateHandler` to `pipeline` when it is not 0
|maxFramePayloadLength|65536|Maximum allowable frame payload length.
|maxMessagePayloadLength|1048576 (1 MiB)|Maximum decoded message size in bytes, including all fragments; also bounds decompression workspace (see lifecycle notes). Must be positive; raise explicitly for larger application messages.
|useEventExecutorGroup|true|Dispatch message, close and event callbacks to a business executor; handshake and open callbacks still run on the I/O thread
|eventExecutorGroupThreads|16|Number of business executor threads
|sslKeyPassword|""(mean not set)|the same as `server.ssl.key-password` in spring-boot
|sslKeyStore|""(mean not set)|the same as `server.ssl.key-store` in spring-boot
|sslKeyStorePassword|""(mean not set)|the same as `server.ssl.key-store-password` in spring-boot
|sslKeyStoreType|""(mean not set)|the same as `server.ssl.key-store-type` in spring-boot
|sslTrustStore|""(mean not set)|the same as `server.ssl.trust-store` in spring-boot
|sslTrustStorePassword|""(mean not set)|the same as `server.ssl.trust-store-password` in spring-boot
|sslTrustStoreType|""(mean not set)|the same as `server.ssl.trust-store-type` in spring-boot
|corsOrigins|{}(mean not set)|the same as `@CrossOrigin#origins` in spring-boot
|corsAllowCredentials|""(mean not set)|the same as `@CrossOrigin#allowCredentials` in spring-boot

### Configuration by application.properties
> You can get the configurate of `application.properties` by using `${...}` placeholders. for example：

- first,use `${...}` in `@ServerEndpoint` 
```java
@ServerEndpoint(host = "${ws.host}",port = "${ws.port}")
public class MyWebSocket {
    ...
}
```
- then configurate in `application.properties`
```
ws.host=0.0.0.0
ws.port=8081
```

### Custom Favicon
The way of configure favicon is the same as spring-boot.If `favicon.ico` is presented in the root of the classpath,it will be automatically used as the favicon of the application.the example is following:
```
src/
  +- main/
      +- java/
      |   + <source code>
      +- resources/
          +- favicon.ico
```

### Custom Error Pages
The way of configure favicon is the same as spring-boot.you can add a file to an `/public/error`
folder.The name of the error page should be the exact status code or a series mask.the example is following:
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

### Multi Endpoint
- Add `@ServerEndpoint` to each endpoint class; `@Component` is not required.
- Different paths on the same host and port share one Netty server. Keep thread, TLS, compression and size-limit settings consistent on that listener. Different hosts or ports use separate servers.
- Endpoints with `port="0"` share a random port only on the same host. Query it after startup with `ServerEndpointConfig.getRandomPort(host)`; the cache is cleared on shutdown.
- `0.0.0.0` binds all local addresses; avoid overlapping listeners on the same port with another host.

### Parameters and handshakes
Use this project's `org.yeauty.annotation.RequestParam` and `PathVariable`, rather than similarly named Spring MVC or Jakarta annotations. Explicit names are recommended:

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

Connect to `ws://127.0.0.1:8081/ws/lobby?name=Alice`; absent or empty `name` uses `guest`. Use the imports from Quick Start, plus this project's `OnOpen`, `PathVariable` and `RequestParam`.
- `required=true` is the default: a missing parameter rejects the handshake with HTTP 400, as does a conversion failure. `required=false` permits `null`; use wrapper types such as `Integer` for optional numbers.
- `defaultValue` covers absent or empty scalar parameters and implicitly makes them optional. An empty String without a default is still a supplied value; applications own non-blank validation.
- For repeated values use `@RequestParam("tag") List<String>`; for all parameters use `@RequestParam Map<String, String>` or `MultiValueMap<String, String>`.
- Explicit parameter annotations take precedence over message-body binding; use an unannotated `String` for the text body.
- Omitting annotation names requires `-parameters` in the application's own Maven/Gradle compilation. The starter's compiler settings do not propagate.
- In `@BeforeHandshake`, call `session.close()` to reject a connection. A thrown callback exception aborts the handshake. Declare supported subprotocols with `session.setSubprotocols("chat")`; the server negotiates a single supported protocol from the client's offer.

### Threads, message limits and lifecycle
`@BeforeHandshake` and `@OnOpen` run on the I/O thread and should return promptly. With the default `useEventExecutorGroup=true`, message, close and event callbacks run on the business executor. Size this pool for the workload and avoid long blocking operations. The thread for `@OnError` depends on the source of the error.

`maxFramePayloadLength` limits individual frames. The new `maxMessagePayloadLength` defaults to 1 MiB and bounds decoded messages and fragment aggregation. Decompression allocation is bounded separately at the message limit plus `max(8192, 2 × maxFramePayloadLength)` bytes of decoder workspace (capped at `Integer.MAX_VALUE`). Oversized decoded/aggregated messages close the connection with code 1009; decompression failures also disconnect. Configure both limits deliberately for large messages, for example:

```java
@ServerEndpoint(path = "/upload", port = "8081",
        maxFramePayloadLength = "1048576",
        maxMessagePayloadLength = "8388608")
```

Bind and TLS initialization failures fail application startup. Closing the Spring context stops listening, sends WebSocket close frames, closes connections, then terminates network and business executors in order. The caller waits up to 5 seconds per listener; timeouts are logged, with late network events still preceding business-executor shutdown. Long-running user callbacks can delay actual resource termination; allow for this in deployment termination grace periods. Do not expect a `bye` text message.

### Mapping TLS properties
This component owns a separate listener; `server.ssl.*` is not applied automatically. Map shared properties explicitly:

```java
@ServerEndpoint(path = "/ws", port = "${ws.port:8443}",
        sslKeyStore = "${server.ssl.key-store}",
        sslKeyStorePassword = "${server.ssl.key-store-password}",
        sslKeyStoreType = "${server.ssl.key-store-type:PKCS12}")
```

### Migrating from 0.13.x to 1.0.0
1. Upgrade the application to Spring Boot 4 / Spring Framework 7 and JDK 17 or later.
2. Update the dependency. Auto-configuration uses `AutoConfiguration.imports`; normal Boot applications need no manual exporter bean.
3. Review callbacks that relied on missing required parameters becoming null. Mark genuinely optional parameters `required=false` or supply defaults.
4. Raise `maxMessagePayloadLength` explicitly for messages over 1 MiB; keep all settings consistent across endpoints sharing a listener.
5. Review subprotocol negotiation: only an explicitly supported protocol is returned, rather than echoing the entire client offer.
6. Boot's BOM may manage a different Netty patch version. If overriding it, align all Netty modules through the application's Netty BOM/version property instead of mixing individual modules.

### Building and verifying
Use JDK 17 or 21. The included Maven Wrapper pins Maven 3.9.11; use `mvnw.cmd` on Windows. Run from the repository root:

```bash
./mvnw -B -ntp clean verify
./mvnw -B -ntp -Pconsumer-test verify
./mvnw -B -ntp -Prelease -Dgpg.skip=true verify
```

The consumer test creates an independent Boot application from the packaged starter and verifies startup, messages and shutdown with a single application dependency. See [RELEASING.md](RELEASING.md) for signing and Central Portal publishing. The release verification above skips signing and does not upload.

---
### Change Log

#### 0.8.0

- Auto-Configuration

#### 0.9.0

- Support RESTful by `@PathVariable`
- Get param by`@RequestParam` from query
- Remove `ParameterMap` ,instead of `@RequestParam MultiValueMap`
- Add `@BeforeHandshake` annotation，you can close the connect before handshake
- Set sub-protocol in `@BeforeHandshake` event
- Remove  the `@Component` on endpoint class
- Update `Netty` version to `4.1.44.Final`

#### 0.9.1

- Bug fixed : it was null when using `@RequestParam MultiValueMap` to get value
- Update `Netty` version to `4.1.45.Final`

#### 0.9.2

-  There are compatibility version under 0.8.0 that can configure the `ServerEndpointExporter` manully 

#### 0.9.3

- Bug fixed ：when there is no  `@BeforeHandshake` , NullPointerException will appear

#### 0.9.4

- Bug fixed ：when there is no  `@BeforeHandshake` , `Session` in `OnOpen` is null

#### 0.9.5

- Bug fixed ：`Throwable` in `OnError` event  is null

#### 0.10.0

- Modified the default value of `bossLoopGroupThreads` to 1
- Supports configuring `useEventExecutorGroup` to run synchronous and time-consuming business logic in EventExecutorGroup, so that the I/O thread is not blocked by a time-consuming task
- SSL supported
- CORS supported
- Update `Netty` version to `4.1.49.Final`

#### 0.11.0

- When the `ServerEndpoint` class is proxied by CGLIB (as with AOP enhancement), it still works

#### 0.12.0

- `@enableWebSocket` adds the `scanBasePackages` attribute
- `@serverEndpoint` no longer depends on `@Component`
- Update `Netty` version to `4.1.67.Final`

#### 0.13.0

- Fixed the issue where WebSocket compression was not working.
- Upgraded support for Spring Boot 3.
- Included the client's `Sec-WebSocket-Protocol` in the response header.
- See the current lifecycle notes for close behavior; clients should not depend on a `bye` text message.
- Updated `Netty` version to `4.1.118.Final`.

#### 1.0.0

- Upgraded support for Spring Boot 4 (Spring Framework 7). Minimum JDK is 17, matching Spring Boot 4's baseline; built and tested on JDK 17 and 21.
- Updated `Netty` version to `4.2.18.Final`.
- Migrated auto-configuration registration from `META-INF/spring.factories` to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Adapted to APIs removed or deprecated in Spring Framework 7 / Netty 4.2.
- Include Spring Boot basics transitively; add a standalone consumer integration test and JDK/Netty CI matrix.
- Fix required/default parameter handling, annotated text-message arguments and subprotocol negotiation; abort failed handshakes.
- Add configurable decoded-message and decompression allocation limits.
- Fix executor shutdown ordering, bind-failure startup, scan fallbacks and parameter aliases.
- Update bilingual setup/migration documentation; migrate publishing to Central Portal and package the project LICENSE.
