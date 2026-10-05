package io.github.kk0met.netscreen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * NetScreen entry point. Client-only: registers key mappings, the HUD layer
 * and the per-tick key handling.
 */
@Mod(value = NetScreen.MODID, dist = Dist.CLIENT)
public final class NetScreen {

    public static final String MODID = "netscreen";
    public static final Logger LOG = LogUtils.getLogger();

    private static final String CATEGORY = "key.categories." + MODID;
    public static final KeyMapping KEY_TOGGLE = key("toggle", GLFW.GLFW_KEY_Y);
    public static final KeyMapping KEY_PICK = key("pick", GLFW.GLFW_KEY_U);
    public static final KeyMapping KEY_SIZE = key("size", GLFW.GLFW_KEY_I);
    public static final KeyMapping KEY_CORNER = key("corner", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping KEY_PLAY = key("play", InputConstants.UNKNOWN.getValue());

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key." + MODID + "." + name, code, CATEGORY);
    }

    public NetScreen(IEventBus modBus) {
        if (!WindowCapture.SUPPORTED) {
            LOG.warn("[NetScreen] Window capture is only supported on Windows; the mod will stay idle.");
        }
        modBus.addListener(NetScreen::onRegisterKeys);
        modBus.addListener(NetScreen::onRegisterLayers);
        NeoForge.EVENT_BUS.addListener(NetScreen::onClientTick);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_TOGGLE);
        event.register(KEY_PICK);
        event.register(KEY_SIZE);
        event.register(KEY_CORNER);
        event.register(KEY_PLAY);
    }

    private static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MODID, "screen"), ScreenOverlay::render);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (KEY_TOGGLE.consumeClick()) ScreenOverlay.toggle();
        while (KEY_PICK.consumeClick()) mc.setScreen(new PickWindowScreen(mc.screen));
        while (KEY_SIZE.consumeClick()) ScreenOverlay.nextSize();
        while (KEY_CORNER.consumeClick()) ScreenOverlay.nextCorner();
        while (KEY_PLAY.consumeClick()) WindowCapture.mediaPlayPause();
    }
}
