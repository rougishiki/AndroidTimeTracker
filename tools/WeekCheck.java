import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;

/**
 * Ground-truth probe for the ISO week-period key used by the todo feature.
 * Run:  java _probe\WeekCheck.java
 *
 * Verifies three things that are easy to get wrong by hand:
 *   1. the key uses the ISO week-based year, not the calendar year;
 *   2. a key round-trips back to the Monday of that week;
 *   3. the naive "${year}-W${weekOfYear}" form really does break.
 */
public class WeekCheck {

    static String weekKey(LocalDate d) {
        int y = d.get(IsoFields.WEEK_BASED_YEAR);
        int w = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", y, w);
    }

    /** The tempting-but-wrong version, kept only to demonstrate the collision. */
    static String naiveKey(LocalDate d) {
        return String.format("%04d-W%02d", d.getYear(), d.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    static LocalDate weekStart(LocalDate d) {
        return d.with(ChronoField.DAY_OF_WEEK, 1);
    }

    static LocalDate parseWeekKey(String key) {
        int y = Integer.parseInt(key.substring(0, 4));
        int w = Integer.parseInt(key.substring(6));
        return LocalDate.of(y, 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, w)
                .with(ChronoField.DAY_OF_WEEK, 1);
    }

    public static void main(String[] args) {
        LocalDate[] dates = {
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 12, 27),
                LocalDate.of(2026, 12, 28),
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 3),
                LocalDate.of(2027, 1, 4),
                LocalDate.of(2025, 12, 28),
                LocalDate.of(2025, 12, 29),
                LocalDate.of(2024, 12, 30),
        };

        System.out.println("date        dow        isoKey     naiveKey   week range                 roundtrip");
        for (LocalDate d : dates) {
            LocalDate mon = weekStart(d);
            System.out.printf("%s  %-9s  %-9s  %-9s  %s..%s  %s%n",
                    d, d.getDayOfWeek(), weekKey(d), naiveKey(d), mon, mon.plusDays(6),
                    parseWeekKey(weekKey(d)).equals(mon) ? "OK" : "MISMATCH->" + parseWeekKey(weekKey(d)));
        }

        System.out.println();
        int bad = 0;
        int collisions = 0;
        for (LocalDate d = LocalDate.of(2020, 1, 1); d.isBefore(LocalDate.of(2031, 1, 1)); d = d.plusDays(1)) {
            if (!parseWeekKey(weekKey(d)).equals(weekStart(d))) {
                bad++;
                if (bad <= 5) System.out.println("roundtrip BAD: " + d);
            }
            if (!naiveKey(d).equals(weekKey(d))) {
                collisions++;
            }
        }
        System.out.println("2020-2030 roundtrip failures (must be 0): " + bad);
        System.out.println("2020-2030 days where naive key != ISO key (shows the trap size): " + collisions);
    }
}
