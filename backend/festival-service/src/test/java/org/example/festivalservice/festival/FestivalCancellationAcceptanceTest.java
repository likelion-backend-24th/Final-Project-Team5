package org.example.festivalservice.festival;
import org.example.festivalservice.domain.festival.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDateTime;

@SpringBootTest(properties = "internal.auth-token=settlement-test") @AutoConfigureMockMvc
class FestivalCancellationAcceptanceTest {
    @Autowired FestivalRepository repository;
    @Autowired MockMvc mvc;
    @Test void ownerRequestsAdminApprovesAndInternalWorkerCompletes() throws Exception {
        var f = repository.save(Festival.builder().hostUserId(10L).name("cancel festival").startAt(LocalDateTime.now().minusDays(4))
                .endAt(LocalDateTime.now().minusDays(2)).festivalStatus(FestivalStatus.CLOSED).build());
        String path = "/api/host/festivals/" + f.getId() + "/cancellation-request";
        mvc.perform(post(path).header("X-User-Id", 20).header("X-User-Role", "HOST")
                .contentType("application/json").content("{\"reason\":\"행사 취소\"}")).andExpect(status().isNotFound());
        mvc.perform(post(path).header("X-User-Id", 10).header("X-User-Role", "HOST")
                .contentType("application/json").content("{\"reason\":\"행사 취소\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/festivals/" + f.getId() + "/approve-cancellation").header("X-User-Id", 10).header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/festivals/" + f.getId() + "/approve-cancellation").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/v1/festivals/" + f.getId() + "/settlement-context").header("Authorization", "Bearer settlement-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"));
        mvc.perform(post("/internal/v1/festivals/" + f.getId() + "/complete-cancellation").header("Authorization", "Bearer settlement-test"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/v1/festivals/" + f.getId() + "/settlement-context").header("Authorization", "Bearer settlement-test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
