package de.petrofsky.autonomousvillagers.utils;

public class PerformanceUtils {

    public static void timeMeasured(String title, long pastMillis) {
        long nowMillis = System.currentTimeMillis();
        long difference = nowMillis - pastMillis;
        long millis = difference % 1000;
        long seconds = Math.floorDiv(difference, 1000);

        String millisText = millis < 10 ? "000" + millis :
                millis < 100 ? "00" + millis : "0" + millis ;
        System.out.println(title + " time: " + seconds + ":" + millisText + " seconds");
    }
}
