package de.petrofsky.autonomousvillagers.utils;

import java.util.Locale;

public class PerformanceUtils {

    public static void timeMeasured(String title, long past, boolean nano) {
        if (nano) {
            long differenceNanos = System.nanoTime() - past;
            double micros = differenceNanos / 1_000.0;
            double millis = differenceNanos / 1_000_000.0;

            System.out.printf(Locale.US, "%s time: %.3f ms (%.1f µs)%n", title, millis, micros);
        } else {
            long differenceMillis = System.currentTimeMillis() - past;
            long seconds = Math.floorDiv(differenceMillis, 1000);
            long millis = differenceMillis % 1000;

            System.out.printf(Locale.US, "%s time: %d:%03d seconds%n", title, seconds, millis);
        }
    }
}
