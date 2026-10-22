package com.chatapp.server.model;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hands out random, readable pastel background colours for users.
 *
 * <p>The first hue is random; each next hue is offset by the golden-ratio angle with a little
 * jitter, so colours stay random but consecutive users never get near-identical shades.
 * Implemented without JavaFX/AWT so the model stays UI-independent.</p>
 */
public final class ColorPalette {

    private static final double GOLDEN_RATIO_CONJUGATE = 0.618033988749895;
    private final AtomicReference<Double> hue = new AtomicReference<>(ThreadLocalRandom.current().nextDouble());

    public String next() {
        double h = hue.updateAndGet(previous -> {
            double jitter = ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
            return (previous + GOLDEN_RATIO_CONJUGATE + jitter + 1.0) % 1.0;
        });
        double saturation = ThreadLocalRandom.current().nextDouble(0.38, 0.55);
        double brightness = ThreadLocalRandom.current().nextDouble(0.93, 0.99);
        return toHex(h, saturation, brightness);
    }

    /** HSB (all 0..1) to {@code #RRGGBB}. */
    static String toHex(double h, double s, double v) {
        double hh = (h % 1.0) * 6.0;
        int sector = (int) Math.floor(hh);
        double f = hh - sector;
        double p = v * (1 - s);
        double q = v * (1 - s * f);
        double t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return String.format("#%02X%02X%02X", Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }
}
