package net.octyl.transcodetalker.data;

import java.util.Map;

public record UsableComponent(
    String plain,
    String html,
    Map<String, Object> full
) {
    public static final UsableComponent NONE = new UsableComponent(
        "<none>", "&lt;none&gt;", Map.of("text", "<none>")
    );
}
