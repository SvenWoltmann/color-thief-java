/*
 * Java Color Thief
 * by Sven Woltmann, Fonpit AG
 * 
 * http://www.androidpit.com
 * http://www.androidpit.de
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

package de.androidpit.colorthief;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;

import de.androidpit.colorthief.MMCQ.CMap;

public class ColorThief
{

    private static final int DEFAULT_QUALITY = 10;
    private static final boolean DEFAULT_IGNORE_WHITE = true;

    /**
     * Use the median cut algorithm to cluster similar colors and return the
     * base color from the largest cluster.
     *
     * @param sourceImage
     *            the source image
     *
     * @return the dominant color as RGB array
     */
    public static int[] getColor(BufferedImage sourceImage)
    {
        int[][] palette = getPalette(sourceImage, 5);
        if (palette == null)
        {
            return null;
        }
        int[] dominantColor = palette[0];
        return dominantColor;
    }

    /**
     * Use the median cut algorithm to cluster similar colors and return the
     * base color from the largest cluster.
     *
     * @param sourceImage
     *            the source image
     * @param quality
     *            0 is the highest quality settings. 10 is the default. There is
     *            a trade-off between quality and speed. The bigger the number,
     *            the faster a color will be returned but the greater the
     *            likelihood that it will not be the visually most dominant
     *            color.
     * @param ignoreWhite
     *            if <code>true</code>, white pixels are ignored
     *
     * @return the dominant color as RGB array
     */
    public static int[] getColor(
            BufferedImage sourceImage,
            int quality,
            boolean ignoreWhite)
    {
        int[][] palette = getPalette(sourceImage, 5, quality, ignoreWhite);
        if (palette == null)
        {
            return null;
        }
        int[] dominantColor = palette[0];
        return dominantColor;
    }

    /**
     * Use the median cut algorithm to cluster similar colors.
     * 
     * @param sourceImage
     *            the source image
     * @param colorCount
     *            the size of the palette; the number of colors returned
     * 
     * @return the palette as array of RGB arrays
     */
    public static int[][] getPalette(BufferedImage sourceImage, int colorCount)
    {
        CMap cmap = getColorMap(sourceImage, colorCount);
        if (cmap == null)
        {
            return null;
        }
        return cmap.palette();
    }

    /**
     * Use the median cut algorithm to cluster similar colors.
     * 
     * @param sourceImage
     *            the source image
     * @param colorCount
     *            the size of the palette; the number of colors returned
     * @param quality
     *            0 is the highest quality settings. 10 is the default. There is
     *            a trade-off between quality and speed. The bigger the number,
     *            the faster the palette generation but the greater the
     *            likelihood that colors will be missed.
     * @param ignoreWhite
     *            if <code>true</code>, white pixels are ignored
     * 
     * @return the palette as array of RGB arrays
     */
    public static int[][] getPalette(
            BufferedImage sourceImage,
            int colorCount,
            int quality,
            boolean ignoreWhite)
    {
        CMap cmap = getColorMap(sourceImage, colorCount, quality, ignoreWhite);
        if (cmap == null)
        {
            return null;
        }
        return cmap.palette();
    }

    /**
     * Use the median cut algorithm to cluster similar colors.
     * 
     * @param sourceImage
     *            the source image
     * @param colorCount
     *            the size of the palette; the number of colors returned
     * 
     * @return the color map
     */
    public static CMap getColorMap(BufferedImage sourceImage, int colorCount)
    {
        return getColorMap(
                sourceImage,
                colorCount,
                DEFAULT_QUALITY,
                DEFAULT_IGNORE_WHITE);
    }

    /**
     * Use the median cut algorithm to cluster similar colors.
     * 
     * @param sourceImage
     *            the source image
     * @param colorCount
     *            the size of the palette; the number of colors returned
     * @param quality
     *            0 is the highest quality settings. 10 is the default. There is
     *            a trade-off between quality and speed. The bigger the number,
     *            the faster the palette generation but the greater the
     *            likelihood that colors will be missed.
     * @param ignoreWhite
     *            if <code>true</code>, white pixels are ignored
     * 
     * @return the color map
     */
    public static CMap getColorMap(
            BufferedImage sourceImage,
            int colorCount,
            int quality,
            boolean ignoreWhite)
    {
        DataBufferByte imageData = (DataBufferByte) sourceImage
                .getRaster()
                .getDataBuffer();
        byte[] pixels = imageData.getData();
        int pixelCount = sourceImage.getWidth() * sourceImage.getHeight();
        int colorDepth = pixels.length / pixelCount;

        // Store the RGB values in an array format suitable for quantize
        // function
        int numRegardedPixels = pixelCount / quality;
        int numUsedPixels = 0;
        int[][] pixelArray = new int[numRegardedPixels][];
        int offset, r, g, b, a;

        // Do the switch outside of the loop, that's much faster
        if (colorDepth == 4)
        {
            for (int i = 0; i < pixelCount; i += quality)
            {
                offset = i * 4;
                r = pixels[offset + 3] & 0xFF;
                g = pixels[offset + 2] & 0xFF;
                b = pixels[offset + 1] & 0xFF;
                a = pixels[offset] & 0xFF;

                // If pixel is mostly opaque and not white
                if (a >= 125 && !(ignoreWhite && r > 250 && g > 250 && b > 250))
                {
                    pixelArray[numUsedPixels] = new int[] {r, g, b};
                    numUsedPixels++;
                }
            }
        }
        else
        {
            for (int i = 0; i < pixelCount; i += quality)
            {
                offset = i * 3;
                r = pixels[offset + 2] & 0xFF;
                g = pixels[offset + 1] & 0xFF;
                b = pixels[offset] & 0xFF;

                // If pixel is not white
                if (!(ignoreWhite && r > 250 && g > 250 && b > 250))
                {
                    pixelArray[numUsedPixels] = new int[] {r, g, b};
                    numUsedPixels++;
                }
            }
        }

        // Remove unused pixels from the array
        pixelArray = Arrays.copyOfRange(pixelArray, 0, numUsedPixels);

        // Send array to quantize function which clusters values using median
        // cut algorithm
        CMap cmap = MMCQ.quantize(pixelArray, colorCount);
        return cmap;
    }

}
