package dev.datnguyen.missionscheduler.network.scheduling;

import java.util.*;

/** Single UTC-day fixed contact windows; one antenna per station, one link per satellite. */
public final class Scheduling {
    public record Window(int start, int end) {
        public Window { if (start < 0 || end > 1440 || start >= end) throw new IllegalArgumentException("Windows require 0 <= start < end <= 1440 UTC minutes."); }
    }
    public record Station(String id, String name, boolean available, List<Window> maintenance) {
        public Station { text(id); text(name); maintenance = List.copyOf(Objects.requireNonNull(maintenance)); }
    }
    public record Contact(String id, String satellite, String station, int start, int end, int priority) {
        public Contact { text(id); text(satellite); text(station); new Window(start, end); if (priority < 1 || priority > 100) throw new IllegalArgumentException("Priority must be 1–100."); }
    }
    public record Scenario(String name, String date, List<Station> stations, List<Contact> contacts) {
        public Scenario {
            text(name); java.time.LocalDate.parse(date);
            stations = List.copyOf(Objects.requireNonNull(stations)); contacts = List.copyOf(Objects.requireNonNull(contacts));
            if (stations.isEmpty() || stations.size() > 8 || contacts.size() > 24) throw new IllegalArgumentException("Use 1–8 stations and at most 24 contacts.");
            Set<String> ids = new HashSet<>();
            for (Station s : stations) { if (!ids.add(s.id)) throw new IllegalArgumentException("Duplicate station ID: " + s.id); if (s.maintenance.size() > 24) throw new IllegalArgumentException("At most 24 maintenance windows per station."); }
            Set<String> contactIds = new HashSet<>();
            for (Contact c : contacts) { if (!ids.contains(c.station)) throw new IllegalArgumentException("Unknown station: " + c.station); if (!contactIds.add(c.id)) throw new IllegalArgumentException("Duplicate contact ID: " + c.id); }
        }
    }
    public record Decision(Contact contact, boolean selected, String reason) {}
    public record Result(List<Decision> decisions, int priorityServed, int baselinePriority, int selectedCount,
                         int baselineCount, int contactMinutes, long solveMicros, String algorithm) {}
    private static void text(String s) { if (s == null || s.isBlank() || s.length() > 100) throw new IllegalArgumentException("Names and IDs require 1–100 characters."); }
    private static boolean overlap(int a, int b, int c, int d) { return a < d && c < b; }
    public static boolean conflict(Contact a, Contact b) {
        return (a.station.equals(b.station) || a.satellite.equals(b.satellite)) && overlap(a.start, a.end, b.start, b.end);
    }
    private static String blocked(Contact c, Map<String, Station> stations) {
        Station s = stations.get(c.station);
        if (!s.available) return "Station unavailable: " + s.name;
        if (s.maintenance.stream().anyMatch(w -> overlap(c.start, c.end, w.start, w.end))) return "Overlaps station maintenance";
        return null;
    }
    public static Result solve(Scenario scenario) {
        long started = System.nanoTime();
        Map<String, Station> stations = new HashMap<>(); scenario.stations.forEach(s -> stations.put(s.id, s));
        List<Contact> eligible = scenario.contacts.stream().filter(c -> blocked(c, stations) == null)
            .sorted(Comparator.comparingInt(Contact::priority).reversed().thenComparing(Contact::id)).toList();
        Search search = new Search(eligible); search.walk(0, 0, new ArrayList<>());
        List<Contact> baseline = new ArrayList<>();
        eligible.stream().sorted(Comparator.comparingInt(Contact::start).thenComparing(Contact::id)).forEach(c -> {
            if (baseline.stream().noneMatch(b -> conflict(b, c))) baseline.add(c);
        });
        Set<String> selected = new HashSet<>(); search.best.forEach(c -> selected.add(c.id));
        List<Decision> decisions = scenario.contacts.stream().sorted(Comparator.comparingInt(Contact::start).thenComparing(Contact::id)).map(c -> {
            if (selected.contains(c.id)) return new Decision(c, true, "Selected in maximum-priority feasible schedule");
            String reason = blocked(c, stations);
            if (reason == null) reason = "Conflicts with selected contact(s): " + search.best.stream().filter(b -> conflict(b, c)).map(Contact::id).sorted().reduce((a,b) -> a + ", " + b).orElse("none");
            return new Decision(c, false, reason);
        }).toList();
        return new Result(decisions, search.bestScore, baseline.stream().mapToInt(Contact::priority).sum(), search.best.size(), baseline.size(),
            search.best.stream().mapToInt(c -> c.end - c.start).sum(), (System.nanoTime() - started) / 1000,
            "Exact branch-and-bound · maximize total priority, then contact count");
    }
    private static final class Search {
        final List<Contact> contacts; final int[] remaining;
        int bestScore = -1; List<Contact> best = List.of();
        Search(List<Contact> contacts) { this.contacts = contacts; remaining = new int[contacts.size()+1]; for(int i=contacts.size()-1;i>=0;i--) remaining[i]=remaining[i+1]+contacts.get(i).priority; }
        void walk(int i, int score, List<Contact> chosen) {
            if (score + remaining[i] < bestScore) return;
            if (i == contacts.size()) { if (score > bestScore || score == bestScore && chosen.size() > best.size()) { bestScore = score; best = List.copyOf(chosen); } return; }
            Contact c = contacts.get(i);
            if (chosen.stream().noneMatch(b -> conflict(b,c))) { chosen.add(c); walk(i+1, score+c.priority, chosen); chosen.remove(chosen.size()-1); }
            walk(i+1, score, chosen);
        }
    }
    public static Scenario demo() {
        return new Scenario("University constellation · training scenario", "2026-09-20", List.of(
            new Station("west", "West station", true, List.of(new Window(720,750))),
            new Station("east", "East station", true, List.of(new Window(840,870))),
            new Station("south", "South station", true, List.of())), List.of(
            new Contact("C01","CUBESAT-A","west",540,610,3), new Contact("C02","CUBESAT-B","west",550,580,8),
            new Contact("C03","CUBESAT-C","west",580,610,7), new Contact("C04","CUBESAT-A","east",545,575,6),
            new Contact("C05","CUBESAT-B","south",555,585,4), new Contact("C06","CUBESAT-D","east",600,640,5),
            new Contact("C07","CUBESAT-A","west",650,680,8), new Contact("C08","CUBESAT-C","east",660,695,7),
            new Contact("C09","CUBESAT-B","west",725,745,9), new Contact("C10","CUBESAT-B","south",730,765,6),
            new Contact("C11","CUBESAT-D","east",845,865,9), new Contact("C12","CUBESAT-A","south",850,890,5)));
    }
}
