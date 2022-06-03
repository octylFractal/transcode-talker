package net.octyl.transcodetalker.adventure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

// Mostly taken from <https://github.com/jpenilla/squaremap>, under the MIT license.

/**
 * Attempts to flatten a {@link Component} into an HTML {@link String}.
 *
 * <p>
 * This should handle pretty much everything, which is nice.
 * </p>
 */
public class HtmlSerializer {

    private static final ComponentFlattener FLATTENER = ComponentFlattener.basic();

    public static String serializeToHtml(Component component) {
        final HtmlFlattener state = new HtmlFlattener();
        FLATTENER.flatten(component, state);
        return SANITIZER.sanitize(state.toString());
    }

    private static final PolicyFactory SANITIZER = Sanitizers.STYLES.and(Sanitizers.FORMATTING);

    private static final class HtmlFlattener implements FlattenerListener {
        private static final char[] OBFUSCATED_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        private static final String CLOSE_SPAN = "</span>";

        private static String asHtml(final TextFormat format) {
            if (format instanceof TextColor textColor) {
                return "<span style='color:" + textColor.asHexString() + "'>";
            } else if (format == TextDecoration.OBFUSCATED) {
                return ""; // handled elsewhere
            } else if (format instanceof TextDecoration decoration) {
                final String inner = switch (decoration) {
                    case BOLD -> "font-weight:bold";
                    case ITALIC -> "font-style:italic";
                    case UNDERLINED -> "text-decoration:underline";
                    case STRIKETHROUGH -> "text-decoration:line-through";
                    default -> throw new IllegalStateException("Unexpected value: " + decoration);
                };
                return "<span style='" + inner + "'>";
            }
            throw new IllegalArgumentException(
                "Cannot handle format: " + format + " (" + format.getClass().getTypeName() + ")"
            );
        }

        private final StringBuilder sb = new StringBuilder();
        private final Deque<Style> stack = new ArrayDeque<>();

        private HtmlFlattener() {
        }

        @Override
        public void pushStyle(final @NotNull Style style) {
            this.stack.push(style);
        }

        @Override
        public void component(final @NotNull String text) {
            final Style style = this.stack.stream()
                .reduce(Style.empty(), Style::merge);
            final int i = this.append(style);
            if (style.decorations().get(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) {
                text.codePoints().forEach($ ->
                    this.sb.append(OBFUSCATED_CHARS[ThreadLocalRandom.current().nextInt(OBFUSCATED_CHARS.length)]));
            } else {
                this.sb.append(text);
            }
            this.close(i);
        }

        @Override
        public void popStyle(final @NotNull Style style) {
            this.stack.removeLastOccurrence(style);
        }

        private void append(final TextFormat format) {
            this.sb.append(asHtml(format));
        }

        private void close(final int i) {
            this.sb.append(CLOSE_SPAN.repeat(Math.max(0, i)));
        }

        private int append(final Style style) {
            final int[] opened = {0};
            final @Nullable TextColor color = style.color();
            if (color != null) {
                this.append(color);
                opened[0]++;
            }
            style.decorations().forEach((decoration, state) -> {
                if (decoration == TextDecoration.OBFUSCATED) {
                    return; // handled elsewhere
                }
                if (state == TextDecoration.State.TRUE) {
                    this.append(decoration);
                    opened[0]++;
                }
            });
            return opened[0];
        }

        public String toString() {
            return this.sb.toString();
        }
    }
}
