package de.petrofsky.autonomousvillagers.utils.geometry;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Sphere {
    private static final AtomicReferenceArray<int[]> SPHERE_BORDER_MASK = new AtomicReferenceArray<>(128);

    private static int[] getSphereMask(int radius) {
        int[] mask = SPHERE_BORDER_MASK.get(radius);
        if (mask == null) {
           // mask = computeSphereMask(radius);
            SPHERE_BORDER_MASK.set(radius, mask);
        }
        return mask;
    }

    protected static long[] getSphere(long center, int radius, boolean borderExclusive,
                                      boolean shuffle, boolean sort, boolean descOrder) {
        int maxRadius = borderExclusive ? radius - 1 : radius;
        if (maxRadius < 0) return new long[0];

        return new long[100];
    }

    protected static long[] getSphereBetween(long center, int radius1, int radius2, boolean innerBorderExclusive,
            boolean outerBorderExclusive, boolean shuffle, boolean sort, boolean descOrder) {
        return new long[100];
    }

    protected static long[] getSphereBorder(long center, int radius) {
        return new long[100];
    }

    private static void shuffleRange(long[] array, int startInclusive, int endExclusive) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = endExclusive - 1; i > startInclusive; i--) {
            int index = startInclusive + rnd.nextInt(i - startInclusive + 1);
            long temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    private static void shuffleArray(long[] array) {
        shuffleRange(array, 0, array.length);
    }
}
