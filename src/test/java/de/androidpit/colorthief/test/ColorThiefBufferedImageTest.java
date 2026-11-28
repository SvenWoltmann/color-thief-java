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

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.Test;
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
    // Allow small RGB differences because palette colors approximate the source colors.
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

    // Exercise both palette-size limits and a typical ten-color request.
    @DataPoints("requestedPaletteSizes")
    public static final int[] REQUESTED_PALETTE_SIZES = {2, 10, 256};

    @Theory
    public void returnsSourceColorAsDominantForSolidImages(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: create solid red, green, blue, and gray images.
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF444444};

        for (int color : colors) {
            BufferedImage image = createImage(imageType, WIDTH, HEIGHT, color);

            // Operation: extract the dominant color from each image.
            int[] extractedColor = ColorThief.getColor(image, 1, false);

            // Assertions: the dominant color is close to the solid source color.
            assertColorCloseTo(color, extractedColor);
        }
    }

    @Theory
    public void preservesDistinctColorsWithMinimumPaletteSize(@FromDataPoints("allImageTypes") int imageType) {
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
    public void preservesDistinctColorsAcrossTheWholeImage(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: the parameterized image contains four equal color quadrants.
        BufferedImage image = createImage(imageType, 4, 4, 0x00000000);
        fillRect(image, 0, 0, 2, 2, 0xFFFF0000);
        fillRect(image, 2, 0, 2, 2, 0xFF00FF00);
        fillRect(image, 0, 2, 2, 2, 0xFF0000FF);
        fillRect(image, 2, 2, 2, 2, 0xFFFFFFFF);

        // Operation: request one palette color for each quadrant.
        int[][] palette = ColorThief.getPalette(image, 4, 1, false);

        // Assertions: the palette represents all four quadrant colors.
        assertNotNull(palette);
        assertEquals(4, palette.length);
        assertPaletteContains(palette, 0xFFFF0000);
        assertPaletteContains(palette, 0xFF00FF00);
        assertPaletteContains(palette, 0xFF0000FF);
        assertPaletteContains(palette, 0xFFFFFFFF);
    }

    @Theory
    public void doesNotInventColorsForSolidImages(
            @FromDataPoints("allImageTypes") int imageType,
            @FromDataPoints("requestedPaletteSizes") int requestedPaletteSize) {
        // Setup: create solid images in primary colors, black, and white.
        Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.WHITE};

        for (Color color : colors) {
            BufferedImage image = createImage(imageType, WIDTH, HEIGHT, color.getRGB());

            // Operation: request a palette larger than the image's single source color.
            int[][] palette = ColorThief.getPalette(image, requestedPaletteSize, 1, false);

            // Assertions: one valid color represents the entire image.
            assertNotNull(palette);
            assertEquals(1, palette.length);
            assertValidPalette(palette);
            assertColorCloseTo(color.getRGB(), palette[0]);
        }
    }

    @Theory
    public void preservesDominantColorWhenPaletteSizeIsLimited(
            @FromDataPoints("allImageTypes") int imageType) {
        // Setup: cover most of a six-color image with red.
        int redColumns = 11;
        Color[] accentColors = {Color.GREEN, Color.BLUE, Color.BLACK, Color.WHITE, Color.CYAN};
        BufferedImage image = createImage(imageType, redColumns + accentColors.length, HEIGHT,
                Color.RED.getRGB());
        for (int i = 0; i < accentColors.length; i++) {
            fillRect(image, redColumns + i, 0, 1, HEIGHT, accentColors[i].getRGB());
        }

        // Operation: request five palette colors and the dominant color.
        int[][] palette = ColorThief.getPalette(image, 5, 1, false);
        int[] dominantColor = ColorThief.getColor(image, 1, false);

        // Assertions: the palette respects the limit and retains dominant red.
        assertNotNull(palette);
        assertEquals(5, palette.length);
        assertValidPalette(palette);
        assertPaletteContains(palette, Color.RED.getRGB());
        assertColorCloseTo(Color.RED.getRGB(), dominantColor);
    }

    @Theory
    public void doesNotInventColorsToFillRequestedPalette(@FromDataPoints("allImageTypes") int imageType) {
        // Setup: create six differently colored stripes.
        Color[] sourceColors = {Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, Color.WHITE, Color.CYAN};
        BufferedImage image = createStripedImage(imageType, sourceColors);

        // Operation: request a palette of ten colors.
        int[][] palette = ColorThief.getPalette(image, 10, 1, false);

        // Assertions: all six source colors appear without extra colors.
        assertNotNull(palette);
        assertEquals(sourceColors.length, palette.length);
        assertValidPalette(palette);
        for (Color color : sourceColors) {
            assertPaletteContains(palette, color.getRGB());
        }
    }

    @Theory
    public void preservesDistinctDarkColorsAlongsideBrightColors(
            @FromDataPoints("allImageTypes") int imageType) {
        // Setup: put black and another dark shade beside a much brighter color.
        Color darkRed = new Color(64, 0, 0);
        Color darkGreen = new Color(0, 64, 0);
        Color darkBlue = new Color(0, 0, 64);
        Color[][] sourcePalettes = {
                {Color.BLACK, darkRed, new Color(0, 240, 248)},
                {Color.BLACK, darkRed, new Color(0, 248, 240)},
                {Color.BLACK, darkGreen, new Color(240, 0, 248)},
                {Color.BLACK, darkGreen, new Color(248, 0, 240)},
                {Color.BLACK, darkBlue, new Color(240, 248, 0)},
                {Color.BLACK, darkBlue, new Color(248, 240, 0)}
        };

        for (Color[] sourceColors : sourcePalettes) {
            BufferedImage image = createStripedImage(imageType, sourceColors);

            // Operation: request one palette entry for each stripe.
            int[][] palette = ColorThief.getPalette(image, sourceColors.length, 1, false);

            // Assertions: both dark shades remain distinct and the bright color is also represented.
            assertNotNull(palette);
            assertEquals(sourceColors.length, palette.length);
            assertValidPalette(palette);
            for (Color color : sourceColors) {
                assertPaletteContains(palette, color.getRGB());
            }
        }
    }

    @Test
    public void returnsValidRgbColorsForGrayscaleImages() {
        // Setup: create light gray and dark gray halves.
        Color nearWhite = new Color(248, 248, 248);
        Color darkerGray = new Color(160, 160, 160);
        BufferedImage image = createImage(BufferedImage.TYPE_BYTE_GRAY, WIDTH, HEIGHT, nearWhite.getRGB());
        fillRect(image, 0, 0, WIDTH / 2, HEIGHT, darkerGray.getRGB());

        // Operation: request ten colors while ignoring white pixels.
        int[][] palette = ColorThief.getPalette(image, 10, 1, true);

        // Assertions: both gray source colors remain and all RGB values are valid.
        assertNotNull(palette);
        assertValidPalette(palette);
        assertEquals(2, palette.length);
        assertPaletteContains(palette, nearWhite.getRGB());
        assertPaletteContains(palette, darkerGray.getRGB());
    }

    @Theory
    public void ignoresPixelsBelowAlphaThresholdForAbgr(@FromDataPoints("abgrImageTypes") int imageType) {
        // Setup: put a partly transparent red pixel beside an opaque blue pixel.
        BufferedImage image = createImage(imageType, 2, 1, 0x00000000);
        image.setRGB(0, 0, 0x7CFF0000);
        image.setRGB(1, 0, 0xFF0000FF);

        // Operation: extract a palette without white filtering.
        int[][] palette = ColorThief.getPalette(image, 2, 1, false);

        // Assertions: only the opaque pixel should contribute to the palette.
        assertNotNull(palette);
        assertEquals(1, palette.length);
        assertColorCloseTo(0xFF0000FF, palette[0]);
    }

    @Theory
    public void keepsPixelsAtAlphaThresholdForAbgr(@FromDataPoints("abgrImageTypes") int imageType) {
        // Setup: create one red pixel with alpha 125.
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
        image.setRGB(0, 0, 0xFFFFFFFF);
        image.setRGB(1, 0, 0xFFFF0000);

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
        image.setRGB(0, 0, 0xFFFFFFFF);
        image.setRGB(1, 0, 0xFFFF0000);

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
        image.setRGB(0, 0, 0xFFFF0000);
        image.setRGB(1, 0, 0xFF00FF00);
        image.setRGB(2, 0, 0xFFFF0000);
        image.setRGB(3, 0, 0xFF00FF00);

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

    private static BufferedImage createStripedImage(int imageType, Color... stripeColors) {
        BufferedImage image = new BufferedImage(stripeColors.length, HEIGHT, imageType);
        for (int stripe = 0; stripe < stripeColors.length; stripe++) {
            fillRect(image, stripe, 0, 1, HEIGHT, stripeColors[stripe].getRGB());
        }
        return image;
    }

    private static void fillRect(BufferedImage image, int x, int y, int width, int height, int argbColor) {
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                image.setRGB(x + dx, y + dy, argbColor);
            }
        }
    }

    private static void assertPaletteContains(int[][] palette, int expectedArgbColor) {
        if (!containsColorCloseTo(palette, expectedArgbColor)) {
            fail("Expected palette to contain color close to " + toHex(expectedArgbColor)
                    + ", got " + paletteToString(palette));
        }
    }

    private static void assertValidPalette(int[][] palette) {
        for (int[] color : palette) {
            assertNotNull(color);
            assertEquals(3, color.length);
            for (int channel : color) {
                assertTrue("Invalid color " + colorToString(color), channel >= 0 && channel <= 255);
            }
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
