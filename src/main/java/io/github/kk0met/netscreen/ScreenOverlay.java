package io.github.kk0met.netscreen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The in-game screen drawn on the HUD (top-right by default). */
public final class ScreenOverlay {

    public static final WindowCapture CAPTURE = new WindowCapture();

    private static final ResourceLocation TEX_ID =
            ResourceLocation.fromNamespaceAndPath(NetScreen.MODID, "capture");
    /** Width of the screen as a fraction of the GUI width. */
    private static final float[] SIZES = {0.28f, 0.36f, 0.46f};
    private static final String[] SIZE_KEYS = {"small", "medium", "large"};
    private static final String[] CORNER_KEYS = {"top_left", "top_right", "bottom_left", "bottom_right"};
    private static final float MAX_HEIGHT_FRACTION = 0.55f;

    private static boolean visible;
    private static int sizeIdx = 1;
    private static int corner = 1;

    private static DynamicTexture tex;
    private static int texW, texH;

    private ScreenOverlay() {}

    // ------------------------------------------------------------ commands

    public static boolean isVisible() {
        return visible;
    }

    public static void toggle() {
        visible = !visible;
        if (!visible) {
            CAPTURE.stop();
            return;
        }
        if (!WindowCapture.SUPPORTED) {
            msg(Component.translatable("netscreen.msg.windows_only"));
            visible = false;
            return;
        }
        CAPTURE.start();
        if (CAPTURE.target() == 0 && !reopenLastWindow()) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new PickWindowScreen(mc.screen));
        }
    }

    public static void select(WindowCapture.WinInfo w) {
        CAPTURE.setTarget(w.hwnd());
        CAPTURE.start();
        visible = true;
        saveLastTitle(w.title());
        msg(Component.translatable("netscreen.msg.now_showing", w.title()));
    }

    public static void nextSize() {
        sizeIdx = (sizeIdx + 1) % SIZES.length;
        msg(Component.translatable("netscreen.msg.size",
                Component.translatable("netscreen.size." + SIZE_KEYS[sizeIdx])));
    }

    public static void nextCorner() {
        corner = (corner + 1) % 4;
        msg(Component.translatable("netscreen.msg.corner",
                Component.translatable("netscreen.corner." + CORNER_KEYS[corner])));
    }

    /** Re-attaches to the window used last time (exact title first, then partial match). */
    private static boolean reopenLastWindow() {
        String saved = loadLastTitle();
        if (saved == null || saved.isEmpty()) return false;
        List<WindowCapture.WinInfo> wins = WindowCapture.listWindows();
        for (WindowCapture.WinInfo w : wins) {
            if (w.title().equals(saved)) { select(w); return true; }
        }
        for (WindowCapture.WinInfo w : wins) {
            if (w.title().contains(saved) || saved.contains(w.title())) { select(w); return true; }
        }
        return false;
    }

    // ------------------------------------------------------------ rendering

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        uploadLatestFrame(mc);

        int sw = g.guiWidth(), sh = g.guiHeight();
        float aspect = texW > 0 ? texH / (float) texW : 9f / 16f;
        int margin = 4, border = 1;
        int w = Math.round(sw * SIZES[sizeIdx]);
        int h = Math.round(w * aspect);
        int maxH = Math.round(sh * MAX_HEIGHT_FRACTION);
        if (h > maxH) { h = maxH; w = Math.round(h / aspect); }

        int x = (corner % 2 == 0) ? margin + border : sw - w - margin - border;
        int y = (corner < 2) ? margin + border : sh - h - margin - border;

        // capture only as many pixels as are actually shown
        CAPTURE.setMaxWidth((int) (w * mc.getWindow().getGuiScale()));

        g.fill(x - border, y - border, x + w + border, y + h + border, 0xC0000000);
        if (tex != null && texW > 0) {
            g.blit(TEX_ID, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        } else {
            g.fill(x, y, x + w, y + h, 0xFF000000);
        }

        String status = CAPTURE.status;
        if (status != null) {
            g.drawCenteredString(mc.font, Component.translatable(status), x + w / 2, y + h / 2 - 4, 0xFFFFFF);
        } else if (CAPTURE.blackFrames > 60) {
            g.drawCenteredString(mc.font, Component.translatable("netscreen.status.black_1"),
                    x + w / 2, y + h / 2 - 10, 0xFF5555);
            g.drawCenteredString(mc.font, Component.translatable("netscreen.status.black_2"),
                    x + w / 2, y + h / 2 + 2, 0xFF5555);
        }
    }

    private static void uploadLatestFrame(Minecraft mc) {
        WindowCapture.Frame f = CAPTURE.takeFrame();
        if (f == null) return;
        if (tex == null || f.width() != texW || f.height() != texH) {
            if (tex != null) mc.getTextureManager().release(TEX_ID);
            tex = new DynamicTexture(f.width(), f.height(), false);
            mc.getTextureManager().register(TEX_ID, tex);
            tex.setFilter(true, false);
            texW = f.width();
            texH = f.height();
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        int[] px = f.abgr();
        int fw = f.width();
        for (int yy = 0; yy < f.height(); yy++) {
            int row = yy * fw;
            for (int xx = 0; xx < fw; xx++) {
                img.setPixelRGBA(xx, yy, px[row + xx]);
            }
        }
        tex.upload();
    }

    // ------------------------------------------------------------ helpers

    private static void msg(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c, true);
    }

    private static Path stateFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("netscreen-last-window.txt");
    }

    private static void saveLastTitle(String t) {
        try {
            Files.createDirectories(stateFile().getParent());
            Files.writeString(stateFile(), t, StandardCharsets.UTF_8);
        } catch (Exception e) {
            NetScreen.LOG.warn("[NetScreen] Could not save last window title", e);
        }
    }

    private static String loadLastTitle() {
        try {
            Path p = stateFile();
            return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
