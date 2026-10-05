package io.github.kk0met.netscreen;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Captures a desktop window with GDI ({@code PrintWindow}) on a background thread.
 * Only the core JNA API is used, which Minecraft already ships.
 */
public final class WindowCapture implements Runnable {

    public static final boolean SUPPORTED =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    // ---------------------------------------------------------------- Win32

    public interface U32 extends Library {
        U32 I = SUPPORTED ? Native.load("user32", U32.class) : null;

        interface EnumProc extends Callback {
            boolean invoke(Pointer hwnd, Pointer data);
        }

        boolean EnumWindows(EnumProc cb, Pointer data);
        int GetWindowTextW(Pointer hwnd, char[] buf, int max);
        boolean IsWindowVisible(Pointer hwnd);
        boolean IsWindow(Pointer hwnd);
        boolean IsIconic(Pointer hwnd);
        boolean GetClientRect(Pointer hwnd, int[] rect);
        Pointer GetDC(Pointer hwnd);
        int ReleaseDC(Pointer hwnd, Pointer hdc);
        boolean PrintWindow(Pointer hwnd, Pointer hdc, int flags);
        void keybd_event(byte vk, byte scan, int flags, Pointer extra);
    }

    public interface G32 extends Library {
        G32 I = SUPPORTED ? Native.load("gdi32", G32.class) : null;

        Pointer CreateCompatibleDC(Pointer hdc);
        Pointer CreateCompatibleBitmap(Pointer hdc, int w, int h);
        Pointer SelectObject(Pointer hdc, Pointer obj);
        boolean DeleteDC(Pointer hdc);
        boolean DeleteObject(Pointer obj);
        int SetStretchBltMode(Pointer hdc, int mode);
        boolean SetBrushOrgEx(Pointer hdc, int x, int y, Pointer prev);
        boolean StretchBlt(Pointer dst, int x, int y, int w, int h,
                           Pointer src, int sx, int sy, int sw, int sh, int rop);
        int GetDIBits(Pointer hdc, Pointer bmp, int start, int lines,
                      Pointer bits, Pointer bmi, int usage);
    }

    private static final int PW_CLIENTONLY_RENDERFULL = 1 | 2;
    private static final int HALFTONE = 4;
    private static final int SRCCOPY = 0x00CC0020;

    public record WinInfo(long hwnd, String title) {}

    public record Frame(int[] abgr, int width, int height) {}

    /** Lists visible, titled top-level windows (excluding Minecraft itself). */
    public static List<WinInfo> listWindows() {
        List<WinInfo> out = new ArrayList<>();
        if (!SUPPORTED) return out;
        char[] buf = new char[512];
        U32.EnumProc proc = (h, d) -> {
            try {
                if (U32.I.IsWindowVisible(h)) {
                    int n = U32.I.GetWindowTextW(h, buf, buf.length);
                    if (n > 0) {
                        String t = new String(buf, 0, n).trim();
                        int[] r = new int[4];
                        U32.I.GetClientRect(h, r);
                        boolean hasSize = (r[2] - r[0]) > 50 && (r[3] - r[1]) > 50;
                        if (!t.isEmpty() && hasSize && !t.startsWith("Minecraft")
                                && !t.equals("Program Manager")) {
                            out.add(new WinInfo(Pointer.nativeValue(h), t));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            return true;
        };
        U32.I.EnumWindows(proc, null);
        return out;
    }

    /** Sends the system-wide media Play/Pause key (handled by browsers and media players). */
    public static void mediaPlayPause() {
        if (!SUPPORTED) return;
        byte vk = (byte) 0xB3; // VK_MEDIA_PLAY_PAUSE
        U32.I.keybd_event(vk, (byte) 0, 1, null);       // KEYEVENTF_EXTENDEDKEY
        U32.I.keybd_event(vk, (byte) 0, 1 | 2, null);   // + KEYEVENTF_KEYUP
    }

    // ---------------------------------------------------------------- state

    private volatile long hwnd;
    private volatile boolean running;
    private volatile int maxWidth = 960;
    private Thread thread;

    /** Translation key describing the current state, or {@code null} while frames are flowing. */
    public volatile String status = "netscreen.status.pick";
    public volatile int blackFrames;

    private final Object lock = new Object();
    private Frame ready;

    // GDI resources (touched only by the capture thread)
    private Pointer screenDC, srcDC, srcBmp, srcOld, dstDC, dstBmp;
    private int srcW, srcH, dstW, dstH;
    private Memory bits, bmi;

    public long target() { return hwnd; }

    public void setTarget(long h) {
        hwnd = h;
        blackFrames = 0;
        status = "netscreen.status.connecting";
    }

    public void setMaxWidth(int w) {
        maxWidth = Math.max(320, Math.min(1600, w));
    }

    private volatile boolean paused;

    /** Starts the capture thread once, or resumes it if paused. */
    public synchronized void start() {
        if (!SUPPORTED) return;
        paused = false;
        if (running) return;
        running = true;
        thread = new Thread(this, "NetScreen-Capture");
        thread.setDaemon(true);
        thread.start();
    }

    /** Pauses capturing (screen hidden) so no CPU is used. */
    public void stop() {
        paused = true;
    }

    /** Returns the latest new frame, or {@code null} if none arrived since the last call. */
    public Frame takeFrame() {
        synchronized (lock) {
            Frame f = ready;
            ready = null;
            return f;
        }
    }

    @Override
    public void run() {
        try {
            screenDC = U32.I.GetDC(null);
            while (running) {
                long t0 = System.nanoTime();
                long h = hwnd;
                if (h == 0 || paused) {
                    sleep(150);
                    continue;
                }
                try {
                    captureOnce(new Pointer(h));
                } catch (Throwable e) {
                    status = "netscreen.status.error";
                    NetScreen.LOG.warn("[NetScreen] Capture error", e);
                    sleep(500);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                sleep(Math.max(1, 33 - ms)); // ~30 fps
            }
        } finally {
            freeSrc();
            freeDst();
            if (screenDC != null) U32.I.ReleaseDC(null, screenDC);
            screenDC = null;
        }
    }

    private void captureOnce(Pointer h) {
        if (!U32.I.IsWindow(h)) {
            status = "netscreen.status.closed";
            hwnd = 0;
            return;
        }
        if (U32.I.IsIconic(h)) {
            status = "netscreen.status.minimized";
            sleep(200);
            return;
        }
        int[] r = new int[4];
        U32.I.GetClientRect(h, r);
        int sw = r[2] - r[0], sh = r[3] - r[1];
        if (sw <= 0 || sh <= 0) {
            status = "netscreen.status.empty";
            return;
        }
        int dw = Math.min(sw, maxWidth);
        int dh = Math.max(1, Math.round(sh * (dw / (float) sw)));

        if (srcDC == null || sw != srcW || sh != srcH) {
            freeSrc();
            srcDC = G32.I.CreateCompatibleDC(screenDC);
            srcBmp = G32.I.CreateCompatibleBitmap(screenDC, sw, sh);
            srcOld = G32.I.SelectObject(srcDC, srcBmp);
            srcW = sw;
            srcH = sh;
        }
        if (dstDC == null || dw != dstW || dh != dstH) {
            freeDst();
            dstDC = G32.I.CreateCompatibleDC(screenDC);
            dstBmp = G32.I.CreateCompatibleBitmap(screenDC, dw, dh);
            dstW = dw;
            dstH = dh;
            bits = new Memory((long) dw * dh * 4);
            bmi = new Memory(44);
            bmi.clear();
            bmi.setInt(0, 40);      // biSize
            bmi.setInt(4, dw);      // biWidth
            bmi.setInt(8, -dh);     // negative biHeight = top-down rows
            bmi.setShort(12, (short) 1);  // biPlanes
            bmi.setShort(14, (short) 32); // biBitCount
            bmi.setInt(16, 0);      // BI_RGB
        }

        if (!U32.I.PrintWindow(h, srcDC, PW_CLIENTONLY_RENDERFULL)) {
            status = "netscreen.status.unsupported";
            return;
        }

        Pointer old = G32.I.SelectObject(dstDC, dstBmp);
        G32.I.SetStretchBltMode(dstDC, HALFTONE);
        G32.I.SetBrushOrgEx(dstDC, 0, 0, null);
        G32.I.StretchBlt(dstDC, 0, 0, dw, dh, srcDC, 0, 0, sw, sh, SRCCOPY);
        G32.I.SelectObject(dstDC, old);

        // GetDIBits requires the bitmap NOT to be selected into a DC
        bmi.setInt(4, dw);
        bmi.setInt(8, -dh);
        int lines = G32.I.GetDIBits(dstDC, dstBmp, 0, dh, bits, bmi, 0);
        if (lines <= 0) {
            status = "netscreen.status.read_failed";
            return;
        }

        int[] px = bits.getIntArray(0, dw * dh);
        boolean allBlack = true;
        for (int i = 0; i < px.length; i++) {
            int p = px[i]; // 0x00RRGGBB (BGRA in memoria)
            if ((p & 0xFFFFFF) > 0x101010) allBlack = false;
            px[i] = 0xFF000000 | ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF); // -> ABGR
        }
        blackFrames = allBlack ? blackFrames + 1 : 0;
        status = null;

        synchronized (lock) {
            ready = new Frame(px, dw, dh);
        }
    }

    private void freeSrc() {
        if (srcDC != null) {
            if (srcOld != null) G32.I.SelectObject(srcDC, srcOld);
            G32.I.DeleteDC(srcDC);
        }
        if (srcBmp != null) G32.I.DeleteObject(srcBmp);
        srcDC = srcBmp = srcOld = null;
        srcW = srcH = 0;
    }

    private void freeDst() {
        if (dstDC != null) G32.I.DeleteDC(dstDC);
        if (dstBmp != null) G32.I.DeleteObject(dstBmp);
        dstDC = dstBmp = null;
        dstW = dstH = 0;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
