package consumer.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;
import org.yeauty.standard.ServerEndpointConfig;
import org.yeauty.standard.ServerEndpointExporter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StarterSmokeTest {
    @Test void oneDependencyStartsAutoConfiguredWebSocketAndEchoes() throws Exception {
        SpringApplication app = new SpringApplication(Application.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
        ServerEndpointExporter exporter;
        try (ConfigurableApplicationContext context = app.run()) {
            exporter = context.getBean(ServerEndpointExporter.class);
            Integer port = ServerEndpointConfig.getRandomPort("127.0.0.1");
            assertNotNull(port);
            CompletableFuture<String> reply = new CompletableFuture<>();
            WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/echo"), new WebSocket.Listener() {
                        private final StringBuilder text = new StringBuilder();
                        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
                        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            text.append(data);
                            if (last) reply.complete(text.toString());
                            ws.request(1);
                            return null;
                        }
                        @Override public void onError(WebSocket ws, Throwable error) { reply.completeExceptionally(error); }
                    }).get(10, TimeUnit.SECONDS);
            try {
                socket.sendText("hello", true).get(5, TimeUnit.SECONDS);
                assertEquals("hello", reply.get(5, TimeUnit.SECONDS));
                socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
            } finally { socket.abort(); }
        }
        assertTrue(exporter.awaitTermination(10, TimeUnit.SECONDS));
    }

    @SpringBootApplication
    public static class Application {}

    @ServerEndpoint(path="/echo", host="127.0.0.1", port="0")
    public static class EchoEndpoint {
        @OnMessage public void message(Session session, String message) { session.sendText(message); }
    }
}
