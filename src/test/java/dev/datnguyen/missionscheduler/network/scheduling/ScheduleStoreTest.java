package dev.datnguyen.missionscheduler.network.scheduling;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class ScheduleStoreTest {
    @TempDir Path dir;
    @Test void persistsHistoryAndRejectsStaleUpdates() throws Exception {
        Path p=dir.resolve("state.json"); var store=new ScheduleStore(new ObjectMapper(),p.toString());
        store.save(1,Scheduling.demo());
        var reopened=new ScheduleStore(new ObjectMapper(),p.toString());assertEquals(2,reopened.state().versions().get(0).revision());assertEquals(2,reopened.state().versions().size());
        assertThrows(ScheduleStore.StaleRevision.class,()->reopened.save(1,Scheduling.demo()));
        for(int i=2;i<25;i++)reopened.save(i,Scheduling.demo());assertEquals(20,reopened.state().versions().size());
    }
    @Test void failedWritePreservesInMemoryStateAndCorruptionFailsExplicitly() throws Exception {
        Path parent=dir.resolve("blocked");var store=new ScheduleStore(new ObjectMapper(),parent.resolve("state.json").toString());Files.writeString(parent,"not a directory");
        assertThrows(IOException.class,()->store.save(1,Scheduling.demo()));assertEquals(1,store.state().versions().get(0).revision());
        Path corrupt=dir.resolve("broken.json");Files.writeString(corrupt,"bad-json");assertThrows(IOException.class,()->new ScheduleStore(new ObjectMapper(),corrupt.toString()));
    }
}
