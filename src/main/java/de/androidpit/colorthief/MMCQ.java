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

package de.androidpit.colorthief;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MMCQ {

    private static final int SIGBITS = 5;
    private static final int RSHIFT = 8 - SIGBITS;
    private static final int MULT = 1 << RSHIFT;
    private static final int HISTOSIZE = 1 << (3 * SIGBITS);
    private static final int VBOX_LENGTH = 1 << SIGBITS;
    private static final double FRACT_BY_POPULATION = 0.75;
    private static final int MAX_ITERATIONS = 1000;

    /**
     * Get reduced-space color index for a pixel.
     * 
     * @param r
     *            the red value
     * @param g
     *            the green value
     * @param b
     *            the blue value
     * 
     * @return the color index
     */
    static int getColorIndex(int r, int g, int b) {
        return (r << (2 * SIGBITS)) + (g << SIGBITS) + b;
    }

    /**
     * 3D color space box.
     */
    public static class VBox {
        int r1;
        int r2;
        int g1;
        int g2;
        int b1;
        int b2;

        private final int[] histo;

        private int[] _avg;
        private Integer _volume;
        private Integer _count;

        public VBox(int r1, int r2, int g1, int g2, int b1, int b2, int[] histo) {
            this.r1 = r1;
            this.r2 = r2;
            this.g1 = g1;
            this.g2 = g2;
            this.b1 = b1;
            this.b2 = b2;

            this.histo = histo;
        }

        @Override
        public String toString() {
            return "r1: " + r1 + " / r2: " + r2 + " / g1: " + g1 + " / g2: " + g2 + " / b1: " + b1
                    + " / b2: " + b2;
        }

        public int volume(boolean force) {
            if (_volume == null || force) {
                _volume = ((r2 - r1 + 1) * (g2 - g1 + 1) * (b2 - b1 + 1));
            }

            return _volume;
        }

        public int count(boolean force) {
            if (_count == null || force) {
                int npix = 0;
                int i, j, k, index;

                for (i = r1; i <= r2; i++) {
                    for (j = g1; j <= g2; j++) {
                        for (k = b1; k <= b2; k++) {
                            index = getColorIndex(i, j, k);
                            npix += histo[index];
                        }
                    }
                }

                _count = npix;
            }

            return _count;
        }

        @Override
        public VBox clone() {
            return new VBox(r1, r2, g1, g2, b1, b2, histo);
        }

        public int[] avg(boolean force) {
            if (_avg == null || force) {
                int ntot = 0;

                int rsum = 0;
                int gsum = 0;
                int bsum = 0;

                int hval, i, j, k, histoindex;

                for (i = r1; i <= r2; i++) {
                    for (j = g1; j <= g2; j++) {
                        for (k = b1; k <= b2; k++) {
                            histoindex = getColorIndex(i, j, k);
                            hval = histo[histoindex];
                            ntot += hval;
                            rsum += (hval * (i + 0.5) * MULT);
                            gsum += (hval * (j + 0.5) * MULT);
                            bsum += (hval * (k + 0.5) * MULT);
                        }
                    }
                }

                if (ntot > 0) {
                    _avg = new int[] {~~(rsum / ntot), ~~(gsum / ntot), ~~(bsum / ntot)};
                } else {
                    _avg = new int[] {~~(MULT * (r1 + r2 + 1) / 2), ~~(MULT * (g1 + g2 + 1) / 2),
                            ~~(MULT * (b1 + b2 + 1) / 2)};
                }
            }

            return _avg;
        }

        public boolean contains(int[] pixel) {
            int rval = pixel[0] >> RSHIFT;
            int gval = pixel[1] >> RSHIFT;
            int bval = pixel[2] >> RSHIFT;

            return (rval >= r1 && rval <= r2 && gval >= g1 && gval <= g2 && bval >= b1
                    && bval <= b2);
        }

    }

    /**
     * Color map.
     */
    public static class CMap {

        public final ArrayList<VBox> vboxes = new ArrayList<>();

        public void push(VBox box) {
            vboxes.add(box);
        }

        public int[][] palette() {
            int numVBoxes = vboxes.size();
            int[][] palette = new int[numVBoxes][];
            for (int i = 0; i < numVBoxes; i++) {
                palette[i] = vboxes.get(i).avg(false);
            }
            return palette;
        }

        public int size() {
            return vboxes.size();
        }

        public int[] map(int[] color) {
            int numVBoxes = vboxes.size();
            for (int i = 0; i < numVBoxes; i++) {
                VBox vbox = vboxes.get(i);
                if (vbox.contains(color)) {
                    return vbox.avg(false);
                }
            }
            return nearest(color);
        }

        public int[] nearest(int[] color) {
            double d1 = Double.MAX_VALUE;
            double d2;
            int[] pColor = null;

            int numVBoxes = vboxes.size();
            for (int i = 0; i < numVBoxes; i++) {
                int[] vbColor = vboxes.get(i).avg(false);
                d2 = Math
                        .sqrt(
                                Math.pow(color[0] - vbColor[0], 2)
                                        + Math.pow(color[1] - vbColor[1], 2)
                                        + Math.pow(color[2] - vbColor[2], 2));
                if (d2 < d1) {
                    d1 = d2;
                    pColor = vbColor;
                }
            }
            return pColor;
        }

    }

    /**
     * Histo (1-d array, giving the number of pixels in each quantized region of color space), or
     * null on error.
     */
    private static int[] getHisto(int[][] pixels) {
        int[] histo = new int[HISTOSIZE];
        int index, rval, gval, bval;

        int numPixels = pixels.length;
        for (int i = 0; i < numPixels; i++) {
            int[] pixel = pixels[i];
            rval = pixel[0] >> RSHIFT;
            gval = pixel[1] >> RSHIFT;
            bval = pixel[2] >> RSHIFT;
            index = getColorIndex(rval, gval, bval);
            histo[index]++;
        }
        return histo;
    }

    private static VBox vboxFromPixels(int[][] pixels, int[] histo) {
        int rmin = 1000000, rmax = 0;
        int gmin = 1000000, gmax = 0;
        int bmin = 1000000, bmax = 0;

        int rval, gval, bval;

        // find min/max
        int numPixels = pixels.length;
        for (int i = 0; i < numPixels; i++) {
            int[] pixel = pixels[i];
            rval = pixel[0] >> RSHIFT;
            gval = pixel[1] >> RSHIFT;
            bval = pixel[2] >> RSHIFT;

            if (rval < rmin) {
                rmin = rval;
            }
            if (rval > rmax) {
                rmax = rval;
            }

            if (gval < gmin) {
                gmin = gval;
            }
            if (gval > gmax) {
                gmax = gval;
            }

            if (bval < bmin) {
                bmin = bval;
            }
            if (bval > bmax) {
                bmax = bval;
            }
        }

        return new VBox(rmin, rmax, gmin, gmax, bmin, bmax, histo);
    }

    private enum RgbAxis {
        RED, GREEN, BLUE;

        int binCount(VBox box) {
            if (this == RED) {
                return box.r2 - box.r1 + 1;
            } else if (this == GREEN) {
                return box.g2 - box.g1 + 1;
            } else {
                return box.b2 - box.b1 + 1;
            }
        }
    }

    private static VBox[] trySplitBox(int[] histogram, VBox box) {
        if (box.count(false) < 2 || box.volume(false) == 1) {
            return null;
        }

        int[] cumulativeCounts = new int[VBOX_LENGTH];

        RgbAxis[] axes = RgbAxis.values();
        // Try other axes because the widest range may contain no variation among the pixels.
        Arrays.sort(axes, Comparator.comparingInt((RgbAxis axis) -> axis.binCount(box)).reversed());
        for (RgbAxis axis : axes) {
            if (axis.binCount(box) <= 1) {
                continue;
            }

            buildCumulativeCounts(histogram, box, axis, cumulativeCounts);
            VBox[] children = trySplitAlongAxis(box, axis, cumulativeCounts);
            if (children != null) {
                return children;
            }
        }

        return null;
    }

    private static void buildCumulativeCounts(
            int[] histogram,
            VBox box,
            RgbAxis axis,
            int[] cumulativeCounts) {
        // Overwrite the axis's entire range so later attempts can safely reuse the array.
        int total = 0;

        if (axis == RgbAxis.RED) {
            for (int red = box.r1; red <= box.r2; red++) {
                for (int green = box.g1; green <= box.g2; green++) {
                    for (int blue = box.b1; blue <= box.b2; blue++) {
                        total += histogram[getColorIndex(red, green, blue)];
                    }
                }
                cumulativeCounts[red] = total;
            }
        } else if (axis == RgbAxis.GREEN) {
            for (int green = box.g1; green <= box.g2; green++) {
                for (int red = box.r1; red <= box.r2; red++) {
                    for (int blue = box.b1; blue <= box.b2; blue++) {
                        total += histogram[getColorIndex(red, green, blue)];
                    }
                }
                cumulativeCounts[green] = total;
            }
        } else {
            for (int blue = box.b1; blue <= box.b2; blue++) {
                for (int red = box.r1; red <= box.r2; red++) {
                    for (int green = box.g1; green <= box.g2; green++) {
                        total += histogram[getColorIndex(red, green, blue)];
                    }
                }
                cumulativeCounts[blue] = total;
            }
        }
    }

    private static VBox[] trySplitAlongAxis(VBox box, RgbAxis axis, int[] cumulativeCounts) {
        int axisMin;
        int axisMax;

        if (axis == RgbAxis.RED) {
            axisMin = box.r1;
            axisMax = box.r2;
        } else if (axis == RgbAxis.GREEN) {
            axisMin = box.g1;
            axisMax = box.g2;
        } else {
            axisMin = box.b1;
            axisMax = box.b2;
        }

        int cutCoordinate = findCutCoordinate(axisMin, axisMax, cumulativeCounts);
        if (cutCoordinate < 0) {
            return null;
        }

        VBox lower = box.clone();
        VBox upper = box.clone();
        if (axis == RgbAxis.RED) {
            lower.r2 = cutCoordinate;
            upper.r1 = cutCoordinate + 1;
        } else if (axis == RgbAxis.GREEN) {
            lower.g2 = cutCoordinate;
            upper.g1 = cutCoordinate + 1;
        } else {
            lower.b2 = cutCoordinate;
            upper.b1 = cutCoordinate + 1;
        }

        return new VBox[] {lower, upper};
    }

    /**
     * Returns the last coordinate of the lower half, or -1 if this axis cannot separate the pixels.
     */
    private static int findCutCoordinate(int axisMin, int axisMax, int[] cumulativeCounts) {
        int totalCount = cumulativeCounts[axisMax];
        int medianCoordinate = axisMin;
        while (cumulativeCounts[medianCoordinate] <= totalCount / 2) {
            medianCoordinate++;
        }

        int leftSpan = medianCoordinate - axisMin;
        int rightSpan = axisMax - medianCoordinate;
        int cutCoordinate;
        if (leftSpan <= rightSpan) {
            cutCoordinate = Math.min(axisMax - 1, medianCoordinate + rightSpan / 2);
        } else {
            // Divide before truncating because rounding an odd span first shifts the cut.
            cutCoordinate = Math.max(axisMin, (int) (medianCoordinate - 1 - leftSpan / 2.0));
        }

        // Keep pixels on both sides because an empty child can have inverted bounds.
        while (cutCoordinate < axisMax && cumulativeCounts[cutCoordinate] == 0) {
            cutCoordinate++;
        }
        while (cumulativeCounts[cutCoordinate] == totalCount && cutCoordinate > axisMin
                && cumulativeCounts[cutCoordinate - 1] > 0) {
            cutCoordinate--;
        }

        if (cumulativeCounts[cutCoordinate] == totalCount) {
            return -1;
        }
        return cutCoordinate;
    }

    public static CMap quantize(int[][] pixels, int maxcolors) {
        // short-circuit
        if (pixels.length == 0 || maxcolors < 2 || maxcolors > 256) {
            return null;
        }

        int[] histo = getHisto(pixels);

        // get the beginning vbox from the colors
        VBox vbox = vboxFromPixels(pixels, histo);
        List<VBox> boxes = new ArrayList<>();
        boxes.add(vbox);

        // Keep finished palette colors separate so neither phase retries an unsplittable box.
        List<VBox> finishedBoxes = new ArrayList<>();

        // Round up to have the same behaviour as in JavaScript
        int target = (int) Math.ceil(FRACT_BY_POPULATION * maxcolors);

        // first set of colors, sorted by population
        splitBoxes(boxes, finishedBoxes, COMPARATOR_COUNT, target, histo);

        // Re-sort by the product of pixel occupancy times the size in color space.
        Collections.sort(boxes, COMPARATOR_PRODUCT);

        // next set - generate the median cuts using the (npix * vol) sorting.
        if (maxcolors > boxes.size() + finishedBoxes.size()) {
            splitBoxes(boxes, finishedBoxes, COMPARATOR_PRODUCT, maxcolors, histo);
        }

        if (!finishedBoxes.isEmpty()) {
            boxes.addAll(finishedBoxes);
            Collections.sort(boxes, COMPARATOR_PRODUCT);
        }

        // Reverse to put the highest elements first into the color map
        Collections.reverse(boxes);

        // calculate the actual colors
        CMap cmap = new CMap();
        for (VBox vb : boxes) {
            cmap.push(vb);
        }

        return cmap;
    }

    private static void splitBoxes(
            List<VBox> candidates,
            List<VBox> finishedBoxes,
            Comparator<VBox> comparator,
            int target,
            int[] histo) {
        int attempts = 0;

        while (candidates.size() + finishedBoxes.size() < target && !candidates.isEmpty()
                && attempts++ < MAX_ITERATIONS) {
            VBox box = candidates.remove(candidates.size() - 1);

            VBox[] children = trySplitBox(histo, box);
            if (children == null) {
                finishedBoxes.add(box);
                continue;
            }

            candidates.add(children[0]);
            candidates.add(children[1]);
            Collections.sort(candidates, comparator);
        }
    }

    private static final Comparator<VBox> COMPARATOR_COUNT = new Comparator<VBox>() {
        @Override
        public int compare(VBox a, VBox b) {
            return a.count(false) - b.count(false);
        }
    };

    private static final Comparator<VBox> COMPARATOR_PRODUCT = new Comparator<VBox>() {
        @Override
        public int compare(VBox a, VBox b) {
            int aCount = a.count(false);
            int bCount = b.count(false);
            int aVolume = a.volume(false);
            int bVolume = b.volume(false);

            // If count is 0 for both (or the same), sort by volume
            if (aCount == bCount) {
                return aVolume - bVolume;
            }

            // Otherwise sort by products
            return Long.compare((long) aCount * aVolume, (long) bCount * bVolume);
        }
    };

}
