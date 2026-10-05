import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Ground truth for the daylight-saving behaviour of SessionTimes.spanOf.
 *
 * Run:  java _probe\DstCheck.java
 *
 * The implementation resolves each local time against the zone; the naive
 * version adds minutes to the day's start, which is wrong on the two days a
 * year that are not 24 hours long.
 */
public class DstCheck {

    static long instant(ZoneId z, LocalDate day, int minutes) {
        return day.atTime(minutes / 60, minutes % 60).atZone(z).toInstant().toEpochMilli();
    }

    static long spanMinutes(ZoneId z, LocalDate day, int start, int end) {
        long s = instant(z, day, start);
        LocalDate endDay = end < start ? day.plusDays(1) : day;
        long e = instant(z, endDay, end);
        return (e - s) / 60_000L;
    }

    static long naiveMinutes(ZoneId z, LocalDate day, int start, int end) {
        long dayStart = day.atStartOfDay(z).toInstant().toEpochMilli();
        LocalDate endDay = end < start ? day.plusDays(1) : day;
        long endStart = endDay.atStartOfDay(z).toInstant().toEpochMilli();
        return ((endStart + end * 60_000L) - (dayStart + start * 60_000L)) / 60_000L;
    }

    public static void main(String[] args) {
        ZoneId berlin = ZoneId.of("Europe/Berlin");

        System.out.println("=== spring forward, Berlin ===");
        for (LocalDate d = LocalDate.of(2026, 3, 27); d.isBefore(LocalDate.of(2026, 4, 1)); d = d.plusDays(1)) {
            System.out.printf("%s dayLength=%dh  01:30=%s  02:30=%s  03:30=%s  04:30=%s%n",
                    d,
                    (d.plusDays(1).atStartOfDay(berlin).toInstant().toEpochMilli()
                            - d.atStartOfDay(berlin).toInstant().toEpochMilli()) / 3_600_000L,
                    d.atTime(1, 30).atZone(berlin),
                    d.atTime(2, 30).atZone(berlin),
                    d.atTime(3, 30).atZone(berlin),
                    d.atTime(4, 30).atZone(berlin));
        }

        System.out.println();
        System.out.println("=== fall back, Berlin ===");
        for (LocalDate d = LocalDate.of(2026, 10, 23); d.isBefore(LocalDate.of(2026, 10, 27)); d = d.plusDays(1)) {
            System.out.printf("%s dayLength=%dh  01:30=%s  02:30=%s  03:30=%s%n",
                    d,
                    (d.plusDays(1).atStartOfDay(berlin).toInstant().toEpochMilli()
                            - d.atStartOfDay(berlin).toInstant().toEpochMilli()) / 3_600_000L,
                    d.atTime(1, 30).atZone(berlin),
                    d.atTime(2, 30).atZone(berlin),
                    d.atTime(3, 30).atZone(berlin));
        }

        System.out.println();
        System.out.println("=== expected lengths (minutes) ===");
        System.out.println("2026-03-29 01:30 -> 03:30  resolved=" + spanMinutes(berlin, LocalDate.of(2026, 3, 29), 90, 210)
                + "  naive=" + naiveMinutes(berlin, LocalDate.of(2026, 3, 29), 90, 210));
        System.out.println("2026-03-29 01:30 -> 04:30  resolved=" + spanMinutes(berlin, LocalDate.of(2026, 3, 29), 90, 270)
                + "  naive=" + naiveMinutes(berlin, LocalDate.of(2026, 3, 29), 90, 270));
        System.out.println("2026-10-25 01:30 -> 02:30  resolved=" + spanMinutes(berlin, LocalDate.of(2026, 10, 25), 90, 150)
                + "  naive=" + naiveMinutes(berlin, LocalDate.of(2026, 10, 25), 90, 150));
        System.out.println("2026-10-25 01:30 -> 03:30  resolved=" + spanMinutes(berlin, LocalDate.of(2026, 10, 25), 90, 210)
                + "  naive=" + naiveMinutes(berlin, LocalDate.of(2026, 10, 25), 90, 210));

        System.out.println();
        System.out.println("=== a no-DST zone must agree with the naive form ===");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        int mismatches = 0;
        for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2027, 1, 1)); d = d.plusDays(1)) {
            for (int s : new int[] { 0, 90, 720, 1410 }) {
                for (int e : new int[] { 60, 210, 900, 1439 }) {
                    if (spanMinutes(shanghai, d, s, e) != naiveMinutes(shanghai, d, s, e)) {
                        mismatches++;
                    }
                }
            }
        }
        System.out.println("Shanghai mismatches over a year (must be 0): " + mismatches);
    }
}
