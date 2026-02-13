package br.com.enderfy.enderskygrid.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class TextUtils implements ComponentLike {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();

    private final List<Component> parts = new ArrayList<>(1);

    public TextUtils() {}

    public TextUtils(@NotNull String text) {
        if (!text.isEmpty()) parts.add(parse(text));
    }

    public TextUtils(@NotNull Component component) {
        if (!component.equals(Component.empty())) parts.add(component);
    }

    public TextUtils(@NotNull List<String> lines) {
        for (String line : lines) {
            if (!line.isEmpty()) parts.add(parse(line));
        }
    }

    public static @NotNull TextUtils of(@NotNull String text) { return new TextUtils(text); }
    public static @NotNull TextUtils of(@NotNull Component component) { return new TextUtils(component); }
    public static @NotNull TextUtils of(@NotNull List<String> lines) { return new TextUtils(lines); }
    public static @NotNull TextUtils empty() { return new TextUtils(); }

    private static @NotNull Component parse(@NotNull String input) {
        if (input.isEmpty()) return Component.empty();

        input = input.replace("\\n", "\n");

        if (input.indexOf('§') != -1) {
            input = input.replace("§", "&");
        }

        Component parsed;

        try { parsed = MINI.deserialize(input); }
        catch (Exception e) { parsed = Component.text(input); }
        String legacy = AMP.serialize(parsed);
        parsed = AMP.deserialize(legacy);

        return parsed.decoration(TextDecoration.ITALIC, false);
    }

    public static @NotNull List<Component> parseAll(@NotNull List<String> lines) {
        List<Component> list = new ArrayList<>(lines.size());
        for (String line : lines) list.add(parse(line));
        return list;
    }

    public static @NotNull List<String> parseAllLegacy(@NotNull List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(SEC.serialize(parse(line)));
        return out;
    }

    public TextUtils replaceAll(@NotNull Map<String, ?> placeholders) {
        parts.replaceAll(comp -> replacePlaceholders(comp, placeholders));
        return this;
    }
    public TextUtils replace(@NotNull String placeholder, @NotNull Object replacement) {
        return replaceAll(Map.of(placeholder, replacement));
    }
    private static Component replacePlaceholders(Component comp, Map<String, ?> placeholders) {
        Component result = comp;
        List<Map.Entry<String, ?>> sorted = new ArrayList<>(placeholders.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        for (Map.Entry<String, ?> entry : sorted) {
            String ph = entry.getKey().startsWith("{") ? entry.getKey() : "{" + entry.getKey() + "}";
            Component val = asComponent(entry.getValue());
            result = result.replaceText(builder -> builder.matchLiteral(ph).replacement(val));
        }
        return result;
    }

    public static @NotNull Component asComponent(Object obj) {
        return switch (obj) {
            case null -> Component.empty();
            case Component c -> c;
            case TextUtils cb -> cb.build();
            case ComponentLike cl -> cl.asComponent();
            default -> parse(obj.toString());
        };
    }

    public TextUtils append(@NotNull String text) {
        parts.add(parse(text));
        return this;
    }
    public TextUtils append(@NotNull Component comp) {
        parts.add(comp);
        return this;
    }
    public TextUtils append(@NotNull TextUtils builder) {
        parts.add(builder.build());
        return this;
    }
    public TextUtils appendAny(@NotNull Object obj) {
        return append(asComponent(obj));
    }

    public TextUtils hover(@NotNull String hoverText) { return hover(parse(hoverText)); }
    public TextUtils hover(@NotNull Component hoverComponent) {
        setOnLast(comp -> comp.hoverEvent(HoverEvent.showText(hoverComponent)));
        return this;
    }
    public TextUtils clickRun(@NotNull String command) {
        setOnLast(comp -> comp.clickEvent(ClickEvent.runCommand(command)));
        return this;
    }
    public TextUtils clickSuggest(@NotNull String command) {
        setOnLast(comp -> comp.clickEvent(ClickEvent.suggestCommand(command)));
        return this;
    }
    public TextUtils clickUrl(@NotNull String url) {
        setOnLast(comp -> comp.clickEvent(ClickEvent.openUrl(url)));
        return this;
    }
    public TextUtils clickChangePage(int page) {
        setOnLast(comp -> comp.clickEvent(ClickEvent.changePage(page)));
        return this;
    }
    public TextUtils clickCopy(@NotNull String text) {
        setOnLast(comp -> comp.clickEvent(ClickEvent.copyToClipboard(text)));
        return this;
    }
    public TextUtils insertion(@NotNull String text) {
        setOnLast(comp -> comp.insertion(text));
        return this;
    }

    private void setOnLast(UnaryOperator<Component> modifier) {
        if (!parts.isEmpty()) {
            int idx = parts.size() - 1;
            parts.set(idx, modifier.apply(parts.get(idx)));
        }
    }

    public @NotNull Component build() {
        if (parts.isEmpty()) return Component.empty();
        Component component = Component.empty();
        for (Component part : parts) {
            component = component.append(part);
            component = component.append(TextUtils.reset());
        }
        return component;
    }

    public boolean isEmpty() { return PlainTextComponentSerializer.plainText().serialize(build()).isBlank(); }
    public @NotNull String toMiniMessage() { return MINI.serialize(build()); }
    public @NotNull String toLegacy() { return AMP.serialize(build()); }
    public @NotNull String toLegacySection() { return SEC.serialize(build()); }
    public @NotNull String toPlain() { return PlainTextComponentSerializer.plainText().serialize(build()); }

    public static @NotNull String componentToPlainString(@NotNull Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    public static @NotNull String componentToLegacyString(@NotNull Component component) { return AMP.serialize(component); }

    public static String stripColorAndMiniMessage(String input) {
        if (input == null) return null;
        String out = input.replaceAll("(?i)[§&][0-9A-FK-ORX]", "");
        out = out.replaceAll("(?i)</?[a-z0-9:_-]+[^>]*>", "");
        return out;
    }

    public static @NotNull Component reset() {
        return Component.text("")
                .color(null)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.UNDERLINED, false)
                .decoration(TextDecoration.STRIKETHROUGH, false)
                .decoration(TextDecoration.OBFUSCATED, false);
    }

    @Override
    public @NotNull Component asComponent() {
        return build();
    }
}
