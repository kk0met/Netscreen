package io.github.kk0met.netscreen;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client configuration ({@code config/netscreen-client.toml}, also editable from the Mods screen). */
public final class Config {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue KEEP_SOURCE_RENDERING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        KEEP_SOURCE_RENDERING = b
                .comment("Keep the captured window rendering while Minecraft covers it.",
                        "Needed for Chromium-based browsers (Chrome, Edge, ...), which stop drawing covered windows.",
                        "Turn off if you notice any issue with the game window.")
                .translation("netscreen.configuration.keepSourceRendering")
                .define("keepSourceRendering", true);
        SPEC = b.build();
    }

    private Config() {}

    public static boolean keepSourceRendering() {
        try {
            return KEEP_SOURCE_RENDERING.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
