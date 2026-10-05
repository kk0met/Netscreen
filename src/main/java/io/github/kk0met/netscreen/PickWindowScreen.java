package io.github.kk0met.netscreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Lists the open desktop windows so the player can choose which one to mirror. */
public class PickWindowScreen extends Screen {

    private static final int PER_PAGE = 8;

    private final Screen parent;
    private List<WindowCapture.WinInfo> wins = List.of();
    private int page;

    public PickWindowScreen(Screen parent) {
        super(Component.translatable("netscreen.pick.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        wins = WindowCapture.listWindows();
        int maxPage = Math.max(0, (wins.size() - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));

        int bw = Math.min(360, width - 40);
        int x = (width - bw) / 2;
        int y = 34;
        int end = Math.min(wins.size(), (page + 1) * PER_PAGE);
        for (int i = page * PER_PAGE; i < end; i++) {
            WindowCapture.WinInfo w = wins.get(i);
            String label = font.plainSubstrByWidth(w.title(), bw - 12);
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                ScreenOverlay.select(w);
                onClose();
            }).bounds(x, y, bw, 20).build());
            y += 22;
        }

        int by = height - 28;
        int small = 40;
        int mid = (bw - 2 * small - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(x, by, small, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.translatable("netscreen.pick.refresh"), b -> rebuildWidgets())
                .bounds(x + small + 4, by, mid - 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x + small + 4 + mid + 2, by, mid - 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(x + bw - small, by, small, 20).build()).active = page < maxPage;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        if (!WindowCapture.SUPPORTED) {
            g.drawCenteredString(font, Component.translatable("netscreen.msg.windows_only"),
                    width / 2, height / 2, 0xFF5555);
        } else if (wins.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("netscreen.pick.empty"),
                    width / 2, height / 2, 0xFFAA00);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
