package io.github.kk0met.netscreen;

import com.sun.jna.Pointer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * Keeps the captured window rendering while Minecraft covers it.
 *
 * <p>Chromium-based browsers (Chrome, Edge, Opera, Discord...) stop painting a window that is
 * fully covered by another opaque window. Their occlusion tracker ignores windows whose window
 * region is not a simple rectangle ({@code GetWindowRgnBox(...) == COMPLEXREGION}), so while the
 * NetScreen overlay is visible we give the Minecraft window a region that excludes a single,
 * invisible pixel in its top-left corner. The region is removed again when the overlay is hidden.
 */
public final class OcclusionGuard {

    private static final int RGN_DIFF = 4;
    private static final int HUGE = 1 << 15;

    private static boolean applied;

    private OcclusionGuard() {}

    /** Applies or removes the region so that it matches {@code wanted}. Call on the render thread. */
    public static void sync(boolean wanted) {
        if (!WindowCapture.SUPPORTED || wanted == applied) return;
        try {
            Pointer hwnd = minecraftHwnd();
            if (hwnd == null) return;
            if (wanted) {
                Pointer region = WindowCapture.G32.I.CreateRectRgn(0, 0, HUGE, HUGE);
                Pointer pixel = WindowCapture.G32.I.CreateRectRgn(0, 0, 1, 1);
                WindowCapture.G32.I.CombineRgn(region, region, pixel, RGN_DIFF);
                WindowCapture.G32.I.DeleteObject(pixel);
                // on success the system owns `region`, so it must not be deleted here
                if (WindowCapture.U32.I.SetWindowRgn(hwnd, region, true) == 0) {
                    WindowCapture.G32.I.DeleteObject(region);
                    NetScreen.LOG.warn("[NetScreen] Could not set the window region");
                    return;
                }
            } else {
                WindowCapture.U32.I.SetWindowRgn(hwnd, null, true);
            }
            applied = wanted;
        } catch (Throwable t) {
            NetScreen.LOG.warn("[NetScreen] Occlusion guard failed", t);
            applied = wanted; // do not retry every tick
        }
    }

    private static Pointer minecraftHwnd() {
        long glfwWindow = Minecraft.getInstance().getWindow().getWindow();
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
        return hwnd == 0 ? null : new Pointer(hwnd);
    }
}
