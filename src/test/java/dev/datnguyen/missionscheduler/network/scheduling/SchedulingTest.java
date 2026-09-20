package dev.datnguyen.missionscheduler.network.scheduling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;

class SchedulingTest {
    @Test void demoBeatsBaselineAndRespectsConstraints() {
        var s=Scheduling.demo(); var result=Scheduling.solve(s);
        assertTrue(result.priorityServed()>result.baselinePriority()); assertFeasible(s,result);
        assertFalse(result.decisions().stream().filter(d->Set.of("C09","C11").contains(d.contact().id())).anyMatch(Scheduling.Decision::selected));
    }
    @Test void outageExcludesStationAndRestorationRecoversPlan() {
        var s=Scheduling.demo(); var offline=new Scheduling.Scenario(s.name(),s.date(),s.stations().stream().map(st->new Scheduling.Station(st.id(),st.name(),!st.id().equals("west"),st.maintenance())).toList(),s.contacts());
        var result=Scheduling.solve(offline); assertFeasible(offline,result);
        assertTrue(result.decisions().stream().filter(d->d.contact().station().equals("west")).noneMatch(Scheduling.Decision::selected));
        assertTrue(Scheduling.solve(s).priorityServed()>result.priorityServed());
    }
    @Test void touchingWindowsAreCompatibleButSatelliteCannotDoubleBook() {
        var a=new Scheduling.Contact("a","sat","one",0,10,1);
        assertFalse(Scheduling.conflict(a,new Scheduling.Contact("b","sat","one",10,20,1)));
        assertTrue(Scheduling.conflict(a,new Scheduling.Contact("b","sat","two",5,20,1)));
        assertFalse(Scheduling.conflict(a,new Scheduling.Contact("b","other","two",5,20,1)));
    }
    @Test void validatesImportsAndHandlesEmptySchedule() {
        var s=Scheduling.demo();
        assertThrows(IllegalArgumentException.class,()->new Scheduling.Contact("x","sat","west",20,10,2));
        assertThrows(IllegalArgumentException.class,()->new Scheduling.Contact("x","sat","west",0,10,0));
        assertThrows(IllegalArgumentException.class,()->new Scheduling.Scenario("x",s.date(),s.stations(),List.of(s.contacts().get(0),s.contacts().get(0))));
        assertThrows(IllegalArgumentException.class,()->new Scheduling.Scenario("x",s.date(),s.stations(),List.of(new Scheduling.Contact("x","s","missing",0,10,1))));
        assertThrows(IllegalArgumentException.class,()->new Scheduling.Scenario("x",s.date(),s.stations(),Collections.nCopies(25,s.contacts().get(0))));
        assertEquals(0,Scheduling.solve(new Scheduling.Scenario("empty",s.date(),s.stations(),List.of())).selectedCount());
    }
    @Test void exactSolverMatchesIndependentExhaustiveOracle() {
        Random random=new Random(2026);
        for(int sample=0;sample<60;sample++) {
            var contacts=new ArrayList<Scheduling.Contact>();
            for(int i=0;i<10;i++){int start=random.nextInt(100);contacts.add(new Scheduling.Contact("C"+i,"SAT"+random.nextInt(3),"S"+random.nextInt(2),start,start+1+random.nextInt(40),1+random.nextInt(10)));}
            var s=new Scheduling.Scenario("test","2026-09-20",List.of(new Scheduling.Station("S0","A",true,List.of()),new Scheduling.Station("S1","B",true,List.of())),contacts);
            int best=-1,count=-1;
            for(int mask=0;mask<(1<<contacts.size());mask++) {
                int score=0,n=0;boolean valid=true;
                for(int i=0;i<contacts.size();i++)if((mask&(1<<i))!=0){var a=contacts.get(i);score+=a.priority();n++;
                    for(int j=0;j<i;j++)if((mask&(1<<j))!=0){var b=contacts.get(j);if((a.station().equals(b.station())||a.satellite().equals(b.satellite()))&&Math.max(a.start(),b.start())<Math.min(a.end(),b.end()))valid=false;}}
                if(valid&&(score>best||score==best&&n>count)){best=score;count=n;}
            }
            var result=Scheduling.solve(s);assertEquals(best,result.priorityServed());assertEquals(count,result.selectedCount());assertFeasible(s,result);
        }
    }
    @Test void recordsReproducibleDemoBenchmark() throws Exception {
        var s=Scheduling.demo();for(int i=0;i<50;i++)Scheduling.solve(s);
        long[] times=new long[200];Scheduling.Result result=null;
        for(int i=0;i<times.length;i++){result=Scheduling.solve(s);times[i]=result.solveMicros();}Arrays.sort(times);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/benchmark.md"),"# Local demo benchmark\n\nScenario: 12 fixed contact windows, 3 stations, 2 maintenance windows. 50 warmup solves, then 200 measured solves.\n\nEnvironment: "+System.getProperty("os.name")+" / "+System.getProperty("os.arch")+", Java "+System.getProperty("java.version")+".\n\nExact solver: "+result.priorityServed()+" priority, "+result.selectedCount()+" contacts. First-come baseline: "+result.baselinePriority()+" priority, "+result.baselineCount()+" contacts.\n\nMeasured solver median: "+times[100]+" microseconds; p95: "+times[189]+" microseconds. This small synthetic benchmark is not a scalability or production latency guarantee. The exact search is exponential in the worst case and accepts at most 24 contacts.\n");
    }
    private void assertFeasible(Scheduling.Scenario s,Scheduling.Result r){var chosen=r.decisions().stream().filter(Scheduling.Decision::selected).map(Scheduling.Decision::contact).toList();for(int i=0;i<chosen.size();i++){var c=chosen.get(i);var st=s.stations().stream().filter(x->x.id().equals(c.station())).findFirst().orElseThrow();assertTrue(st.available());for(var m:st.maintenance())assertFalse(c.start()<m.end()&&m.start()<c.end());for(int j=0;j<i;j++)assertFalse(Scheduling.conflict(c,chosen.get(j)));}}
}
