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

package de.androidpit.colorthief.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import de.androidpit.colorthief.ColorThief;
import de.androidpit.colorthief.MMCQ.CMap;
import de.androidpit.colorthief.MMCQ.VBox;

public class ColorThiefTest {

    public static void main(String[] args) throws IOException {
        printStyleHeader();
        test("examples/img/photo1.jpg");
        test("examples/img/photo2.jpg");
        test("examples/img/photo3.jpg");
    }

    /**
     * Prints a style header.
     */
    private static void printStyleHeader() {
        System.out.println(
                "<style>div.color{width:4em;height:4em;float:left;margin:0 1em 1em 0;}"
                        + "th{text-align:left}"
                        + "td{vertical-align:top;padding-right:1em}</style>");
    }

    /**
     * Tests the color thief with the image at the given path name.
     * 
     * @param pathname
     *            the image path name
     * 
     * @throws IOException
     *             if an I/O error occurs
     */
    private static void test(String pathname) throws IOException {
        System.out.println("<h1>Image: &quot;" + pathname + "&quot</h1>");

        BufferedImage img = ImageIO.read(new File(pathname));

        // The dominant color is taken from a 5-map
        System.out.println("<h2>Dominant Color</h2>");
        CMap result = ColorThief.getColorMap(img, 5);
        VBox dominantColor = result.vboxes.get(0);
        printVBox(dominantColor);

        // Get the full palette
        System.out.println("<h2>Palette</h2>");
        result = ColorThief.getColorMap(img, 10);
        for (VBox vbox : result.vboxes) {
            printVBox(vbox);
        }
    }

    /**
     * Prints HTML code for a VBox.
     * 
     * @param vbox
     *            the vbox
     */
    private static void printVBox(VBox vbox) {
        int[] rgb = vbox.avg(false);

        // Create color String representations
        String rgbString = createRGBString(rgb);
        String rgbHexString = createRGBHexString(rgb);

        StringBuilder line = new StringBuilder();

        line.append("<div>");

        // Print color box
        line
                .append("<div class=\"color\" style=\"background:") //
                .append(rgbString)
                .append(";\"></div>");

        // Print table with color code and VBox information
        line.append(
                "<table><tr><th>Color code:</th>" //
                        + "<th>Volume &times pixel count:</th>" //
                        + "<th>VBox:</th></tr>");

        // Color code
        line
                .append("<tr><td>") //
                .append(rgbString)
                .append(" / ")
                .append(rgbHexString)
                .append("</td>");

        // Volume / pixel count
        int volume = vbox.volume(false);
        int count = vbox.count(false);
        line
                .append("<td>")
                .append(String.format("%,d", volume))
                .append(" &times; ")
                .append(String.format("%,d", count))
                .append(" = ")
                .append(String.format("%,d", volume * count))
                .append("</td>");

        // VBox
        line
                .append("<td>") //
                .append(vbox.toString())
                .append("</td></tr></table>");

        // Stop floating
        line.append("<div style=\"clear:both\"></div>");

        line.append("</div>");

        System.out.println(line);
    }

    /**
     * Creates a string representation of an RGB array.
     * 
     * @param rgb
     *            the RGB array
     * 
     * @return the string representation
     */
    private static String createRGBString(int[] rgb) {
        return "rgb(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")";
    }

    /**
     * Creates an HTML hex color code for the given RGB array (e.g. <code>#ff0000</code> for red).
     * 
     * @param rgb
     *            the RGB array
     * 
     * @return the HTML hex color code
     */
    private static String createRGBHexString(int[] rgb) {
        String rgbHex = Integer.toHexString(rgb[0] << 16 | rgb[1] << 8 | rgb[2]);

        // Left-pad with 0s
        int length = rgbHex.length();
        if (length < 6) {
            rgbHex = "00000".substring(0, 6 - length) + rgbHex;
        }

        return "#" + rgbHex;
    }

}
