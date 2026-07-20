/*
 * Java Color Thief
 * by Sven Woltmann, Fonpit AG
 *
 * https://www.androidpit.com
 * https://www.androidpit.de
 *
 * License
 * -------
 * Creative Commons Attribution 2.5 License:
 * http://creativecommons.org/licenses/by/2.5/
 *
 * Thanks
 * ------
 * Lokesh Dhakar - for the original Color Thief JavaScript version
 * available at http://lokeshdhakar.com/projects/color-thief/
 */

package de.androidpit.colorthief.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

import de.androidpit.colorthief.ColorThief;

@RunWith(Theories.class)
public class ColorThiefBufferedImageTest {

    private static final int WIDTH = 4;
    private static final int HEIGHT = 4;
    private static final int CHANNEL_TOLERANCE = 4;

    @DataPoints("allImageTypes")
    public static final int[] ALL_IMAGE_TYPES = {
            BufferedImage.TYPE_3BYTE_BGR,
            BufferedImage.TYPE_4BYTE_ABGR,
            BufferedImage.TYPE_INT_ARGB
    };

    @DataPoints("abgrImageTypes")
    public static final int[] ABGR_IMAGE_TYPES = {
            BufferedImage.TYPE_4BYTE_ABGR
    };

    @Theory
    public void extractsUniformNonBlackColors(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: each supported BufferedImage path receives solid non-black colors.
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF444444};

        for (int color : colors) {
            BufferedImage image = createImage(imageType, WIDTH, HEIGHT, color);

            // Operation: extract the dominant color through the shared public path.
            int[] extractedColor = ColorThief.getColor(image, 1, false);

            // Assertions: a solid image returns its original color within quantization tolerance.
            assertColorCloseTo(color, extractedColor);
        }
    }

    @Theory
    public void extractsDistinctColorsFromSimpleImage(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: the parameterized image contains equal red and green halves.
        BufferedImage image = createImage(imageType, 4, 4, 0x00000000);
        fillRect(image, 0, 0, 2, 4, 0xFFFF0000);
        fillRect(image, 2, 0, 2, 4, 0xFF00FF00);

        // Operation: request a two-color palette using every pixel.
        int[][] palette = ColorThief.getPalette(image, 2, 1, false);

        // Assertions: both source colors are represented in the palette.
        assertNotNull(palette);
        assertEquals(2, palette.length);
        assertPaletteContains(palette, 0xFFFF0000);
        assertPaletteContains(palette, 0xFF00FF00);
    }

    @Theory
    public void extractsDistinctColorsFromFourQuadrants(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: the parameterized image contains four equal color quadrants.
        BufferedImage image = createImage(imageType, 4, 4, 0x00000000);
        fillRect(image, 0, 0, 2, 2, 0xFFFF0000);
        fillRect(image, 2, 0, 2, 2, 0xFF00FF00);
        fillRect(image, 0, 2, 2, 2, 0xFF0000FF);
        fillRect(image, 2, 2, 2, 2, 0xFFFFFFFF);

        // Operation: request one palette color for each quadrant.
        int[][] palette = ColorThief.getPalette(image, 4, 1, false);

        // Assertions: each quadrant color is represented after quantization.
        assertNotNull(palette);
        assertEquals(4, palette.length);
        assertPaletteContains(palette, 0xFFFF0000);
        assertPaletteContains(palette, 0xFF00FF00);
        assertPaletteContains(palette, 0xFF0000FF);
        assertPaletteContains(palette, 0xFFFFFFFF);
    }

    @Theory
    public void ignoresPixelsBelowAlphaThresholdForAbgr(@FromDataPoints("abgrImageTypes") int imageType) {
        // Setup: only the ABGR fast path applies the current alpha-threshold behavior.
        BufferedImage image = createImage(imageType, 2, 1, 0x00000000);
        setPixel(image, 0, 0, 0x7CFF0000);
        setPixel(image, 1, 0, 0xFF0000FF);

        // Operation: extract a palette without white filtering.
        int[][] palette = ColorThief.getPalette(image, 2, 1, false);

        // Assertions: only the opaque pixel should contribute to the palette.
        assertNotNull(palette);
        assertEquals(1, palette.length);
        assertColorCloseTo(0xFF0000FF, palette[0]);
    }

    @Theory
    public void keepsPixelsAtAlphaThresholdForAbgr(@FromDataPoints("abgrImageTypes") int imageType) {
        // Setup: only the ABGR fast path applies the current alpha-threshold behavior.
        BufferedImage image = createImage(imageType, 1, 1, 0x7DFF0000);

        // Operation: extract a palette without white filtering.
        int[][] palette = ColorThief.getPalette(image, 2, 1, false);

        // Assertions: threshold-alpha pixels are treated as visible.
        assertNotNull(palette);
        assertEquals(1, palette.length);
        assertColorCloseTo(0xFFFF0000, palette[0]);
    }

    @Theory
    public void ignoresWhitePixelsWhenRequested(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: the parameterized image contains one white pixel and one red pixel.
        BufferedImage image = createImage(imageType, 2, 1, 0x00000000);
        setPixel(image, 0, 0, 0xFFFFFFFF);
        setPixel(image, 1, 0, 0xFFFF0000);

        // Operation: extract a palette with white filtering enabled.
        int[][] palette = ColorThief.getPalette(image, 2, 1, true);

        // Assertions: the white pixel is ignored and the red pixel remains.
        assertNotNull(palette);
        assertEquals(1, palette.length);
        assertColorCloseTo(0xFFFF0000, palette[0]);
    }

    @Theory
    public void keepsWhitePixelsWhenNotIgnored(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: the parameterized image contains one white pixel and one red pixel.
        BufferedImage image = createImage(imageType, 2, 1, 0x00000000);
        setPixel(image, 0, 0, 0xFFFFFFFF);
        setPixel(image, 1, 0, 0xFFFF0000);

        // Operation: extract a palette with white filtering disabled.
        int[][] palette = ColorThief.getPalette(image, 2, 1, false);

        // Assertions: white remains part of the palette when it is not filtered.
        assertNotNull(palette);
        assertEquals(2, palette.length);
        assertPaletteContains(palette, 0xFFFFFFFF);
        assertPaletteContains(palette, 0xFFFF0000);
    }

    @Theory
    public void usesQualityAsSamplingStride(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: red pixels are at sampled positions and green pixels are skipped by quality 2.
        BufferedImage image = createImage(imageType, 4, 1, 0x00000000);
        setPixel(image, 0, 0, 0xFFFF0000);
        setPixel(image, 1, 0, 0xFF00FF00);
        setPixel(image, 2, 0, 0xFFFF0000);
        setPixel(image, 3, 0, 0xFF00FF00);

        // Operation: extract a palette using quality as the pixel stride.
        int[][] palette = ColorThief.getPalette(image, 2, 2, false);

        // Assertions: only sampled red pixels contribute to the palette.
        assertNotNull(palette);
        assertColorCloseTo(0xFFFF0000, palette[0]);
        assertFalse(containsColorCloseTo(palette, 0xFF00FF00));
    }

    @Theory
    public void throwsForInvalidQuality(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: a valid parameterized image is used so only quality validation can fail.
        BufferedImage image = createImage(imageType, 2, 1, 0xFFFF0000);

        // Operation and assertions: every shared public path rejects quality below 1.
        assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                ColorThief.getPalette(image, 2, 0, false);
            }
        });
        assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                ColorThief.getColorMap(image, 2, -1, false);
            }
        });
        assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                ColorThief.getColor(image, 0, false);
            }
        });
    }

    @Theory
    public void throwsForInvalidColorCounts(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: a valid parameterized image is used so only color-count validation can fail.
        BufferedImage image = createImage(imageType, 2, 1, 0xFFFF0000);

        // Operation and assertions: the public contract rejects color counts outside 2..256.
        assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                ColorThief.getPalette(image, 1, 1, false);
            }
        });
        assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                ColorThief.getPalette(image, 257, 1, false);
            }
        });
        assertThrows(IllegalArgumentException.class, new ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                ColorThief.getColorMap(image, 0, 1, false);
            }
        });
    }

    private static BufferedImage createImage(int type, int width, int height, int argbColor) {
        BufferedImage image = new BufferedImage(width, height, type);
        fillRect(image, 0, 0, width, height, argbColor);
        return image;
    }

    private static void fillRect(BufferedImage image, int x, int y, int width, int height, int argbColor) {
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                setPixel(image, x + dx, y + dy, argbColor);
            }
        }
    }

    private static void setPixel(BufferedImage image, int x, int y, int argbColor) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_3BYTE_BGR || type == BufferedImage.TYPE_4BYTE_ABGR) {
            byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            int red = red(argbColor);
            int green = green(argbColor);
            int blue = blue(argbColor);
            int alpha = (argbColor >> 24) & 0xFF;

            if (type == BufferedImage.TYPE_3BYTE_BGR) {
                int offset = (y * image.getWidth() + x) * 3;
                pixels[offset] = (byte) blue;
                pixels[offset + 1] = (byte) green;
                pixels[offset + 2] = (byte) red;
            } else {
                int offset = (y * image.getWidth() + x) * 4;
                pixels[offset] = (byte) alpha;
                pixels[offset + 1] = (byte) blue;
                pixels[offset + 2] = (byte) green;
                pixels[offset + 3] = (byte) red;
            }
        } else {
            image.setRGB(x, y, argbColor);
        }
    }

    private static void assertPaletteContains(int[][] palette, int expectedArgbColor) {
        if (!containsColorCloseTo(palette, expectedArgbColor)) {
            fail("Expected palette to contain color close to " + toHex(expectedArgbColor)
                    + ", got " + paletteToString(palette));
        }
    }

    private static boolean containsColorCloseTo(int[][] palette, int expectedArgbColor) {
        assertNotNull(palette);
        for (int[] color : palette) {
            if (isColorCloseTo(expectedArgbColor, color)) {
                return true;
            }
        }
        return false;
    }

    private static void assertColorCloseTo(int expectedArgbColor, int[] actualColor) {
        assertNotNull(actualColor);
        assertEquals(3, actualColor.length);
        assertTrue("Expected color close to " + toHex(expectedArgbColor) + ", got "
                + colorToString(actualColor), isColorCloseTo(expectedArgbColor, actualColor));
    }

    private static boolean isColorCloseTo(int expectedArgbColor, int[] actualColor) {
        return actualColor != null
                && actualColor.length == 3
                && isChannelClose(red(expectedArgbColor), actualColor[0])
                && isChannelClose(green(expectedArgbColor), actualColor[1])
                && isChannelClose(blue(expectedArgbColor), actualColor[2]);
    }

    private static boolean isChannelClose(int expected, int actual) {
        return Math.abs(expected - actual) <= CHANNEL_TOLERANCE;
    }

    private static int red(int argbColor) {
        return (argbColor >> 16) & 0xFF;
    }

    private static int green(int argbColor) {
        return (argbColor >> 8) & 0xFF;
    }

    private static int blue(int argbColor) {
        return argbColor & 0xFF;
    }

    private static String toHex(int argbColor) {
        return String.format("0x%08X", argbColor);
    }

    private static String paletteToString(int[][] palette) {
        if (palette == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < palette.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(colorToString(palette[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String colorToString(int[] color) {
        if (color == null) {
            return "null";
        }
        return "[" + color[0] + ", " + color[1] + ", " + color[2] + "]";
    }

}
