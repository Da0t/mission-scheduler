package dev.datnguyen.missionscheduler.network.scheduling;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="mission.store=${java.io.tmpdir}/mission-api-${random.uuid}.json")
class ScheduleApiTest {
    @Autowired TestRestTemplate http;
    @Test void scheduleWorkflowAndHttpErrors() {
        var initial=http.getForObject("/api/schedules",ScheduleStore.State.class);long revision=initial.versions().get(0).revision();
        var update=new ScheduleController.Update(revision,Scheduling.demo());
        var saved=http.postForEntity("/api/schedules",update,ScheduleStore.State.class);assertEquals(HttpStatus.OK,saved.getStatusCode());assertEquals(revision+1,saved.getBody().versions().get(0).revision());
        assertEquals(HttpStatus.CONFLICT,http.postForEntity("/api/schedules",update,String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,http.postForEntity("/api/schedules",new ScheduleController.Update(revision+1,null),String.class).getStatusCode());
        assertTrue(http.getForObject("/",String.class).contains("Make every window count"));assertTrue(http.getForObject("/network.html",String.class).contains("Follow the signal"));
    }
}
