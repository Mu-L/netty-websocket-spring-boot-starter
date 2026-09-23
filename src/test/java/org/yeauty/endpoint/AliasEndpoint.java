package org.yeauty.endpoint;

import org.yeauty.annotation.OnMessage;
import org.yeauty.annotation.OnOpen;
import org.yeauty.annotation.PathVariable;
import org.yeauty.annotation.RequestParam;
import org.yeauty.annotation.ServerEndpoint;
import org.yeauty.pojo.Session;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 参数别名端点：{@code @PathVariable} 与 {@code @RequestParam} 的 {@code value} 与 {@code name}
 * 互为别名，这里分别使用 {@code value} 简写与 {@code name} 显式写法，验证两种形式都能正确绑定。
 */
@ServerEndpoint(path = "/alias/{id}", port = "0")
public class AliasEndpoint {

    private static final List<String> OPENED = new CopyOnWriteArrayList<>();

    /**
     * @return 每次 {@code onOpen} 记录的 {@code id|token|mode}
     */
    public static List<String> opened() {
        return OPENED;
    }

    @OnOpen
    public void onOpen(Session session,
                       @PathVariable("id") String id,
                       @RequestParam("token") String token,
                       @RequestParam(name = "mode", defaultValue = "raw") String mode) {
        OPENED.add(id + "|" + token + "|" + mode);
    }

    /**
     * 路径变量声明为 {@code int}：{@code @OnMessage} 中的 {@code String} 参数会被
     * {@link org.yeauty.support.TextMethodArgumentResolver} 优先匹配（框架既有行为）。
     */
    @OnMessage
    public void onMessage(Session session, @PathVariable("id") int id, String message) {
        session.sendText("alias:" + id + ":" + message);
    }
}
