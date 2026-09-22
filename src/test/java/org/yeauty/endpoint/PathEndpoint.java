package org.yeauty.endpoint;

import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.OnOpen;
import org.yeauty.annotation.PathVariable;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 路径参数端点：验证 {@code @PathVariable} 在 Spring 7 下仍能正确解析。
 * <p>
 * 注意：框架只有在 {@code @OnOpen} 参数中出现 {@code @PathVariable} 时才会为
 * 该路径注册 {@link org.yeauty.support.AntPathMatcherWrapper}，因此这里在 onOpen 与 onMessage 上都声明了该参数。
 * <p>
 * 另外，{@code @PathVariable} 的 {@code value} 与 {@code name} 互为别名，但框架读取的是 {@code name()}，
 * 因此这里显式使用 {@code name = "id"}（构建未开启 {@code -parameters} 时无法从字节码推断参数名）。
 */
@ServerEndpoint(path = "/user/{id}", port = "0")
public class PathEndpoint {

    private static final List<String> OPENED_IDS = new CopyOnWriteArrayList<>();

    public static List<String> openedIds() {
        return OPENED_IDS;
    }

    @OnOpen
    public void onOpen(Session session, @PathVariable(name = "id") String id) {
        OPENED_IDS.add(id);
    }

    /**
     * 这里把路径变量声明为 {@code int}：{@code @OnMessage} 方法中的 {@code String} 参数会被
     * 优先级更高的 {@link org.yeauty.support.TextMethodArgumentResolver} 抢先匹配（框架既有行为），
     * 使用非 String 类型才能命中 {@link org.yeauty.support.PathVariableMethodArgumentResolver}。
     */
    @OnMessage
    public void onMessage(Session session, @PathVariable(name = "id") int id, String message) {
        session.sendText("user:" + id + ":" + message);
    }
}
