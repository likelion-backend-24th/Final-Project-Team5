package org.example.festivalservice.festival;
import org.example.festivalservice.domain.festival.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDateTime;

@SpringBootTest(properties = "internal.auth-token=settlement-test") @AutoConfigureMockMvc
class FestivalCancellationAcceptanceTest {
    @Autowired FestivalRepository repository;
    @Autowired MockMvc mvc;
    @Test void ownerRequestsAdminApprovesAndInternalWorkerCompletes() throws Exception {
        var f = repository.save(Festival.builder().hostUserId(10L).name("cancel festival").startAt(LocalDateTime.now().plusDays(2))
                .endAt(LocalDateTime.now().plusDays(4)).festivalStatus(FestivalStatus.PUBLISHED).build());
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

    //시작된 행사(진행 중·종료)는 입장한 참가자까지 전액 환불하게 되므로 요청 자체를 받지 않는다
    @Test void startedOrClosedFestivalCannotRequestCancellation() throws Exception {
        var started = repository.save(Festival.builder().hostUserId(10L).name("started festival").startAt(LocalDateTime.now().minusHours(1))
                .endAt(LocalDateTime.now().plusDays(1)).festivalStatus(FestivalStatus.PUBLISHED).build());
        var closed = repository.save(Festival.builder().hostUserId(10L).name("closed festival").startAt(LocalDateTime.now().minusDays(4))
                .endAt(LocalDateTime.now().minusDays(2)).festivalStatus(FestivalStatus.CLOSED).build());
        mvc.perform(post("/api/host/festivals/" + started.getId() + "/cancellation-request").header("X-User-Id", 10).header("X-User-Role", "HOST")
                .contentType("application/json").content("{\"reason\":\"행사 취소\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("FESTIVAL_ALREADY_STARTED"));
        mvc.perform(post("/api/host/festivals/" + closed.getId() + "/cancellation-request").header("X-User-Id", 10).header("X-User-Role", "HOST")
                .contentType("application/json").content("{\"reason\":\"행사 취소\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("FESTIVAL_NOT_CANCELLABLE"));
    }

    @Test void adminRejectsBeforeApprovalAndFestivalReturnsToPreviousStatus() throws Exception {
        var f = repository.save(Festival.builder().hostUserId(10L).name("reject festival").startAt(LocalDateTime.now().plusDays(2))
                .endAt(LocalDateTime.now().plusDays(4)).festivalStatus(FestivalStatus.PUBLISHED).build());
        String request = "/api/host/festivals/" + f.getId() + "/cancellation-request";
        String reject = "/api/admin/festivals/" + f.getId() + "/reject-cancellation";
        //취소 요청이 없는 행사는 반려할 수 없다
        mvc.perform(post(reject).header("X-User-Id", 1).header("X-User-Role", "ADMIN")).andExpect(status().isConflict());
        mvc.perform(post(request).header("X-User-Id", 10).header("X-User-Role", "HOST")
                .contentType("application/json").content("{\"reason\":\"행사 취소\"}")).andExpect(status().isOk());
        mvc.perform(post(reject).header("X-User-Id", 10).header("X-User-Role", "HOST")).andExpect(status().isForbidden());
        mvc.perform(post(reject).header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("PUBLISHED"));
        var restored = repository.findById(f.getId()).orElseThrow();
        assertThat(restored.getFestivalStatus()).isEqualTo(FestivalStatus.PUBLISHED);
        assertThat(restored.getCancelReason()).isNull();
        assertThat(restored.getCancelledByUserId()).isNull();
        //반려 뒤 주최자가 다시 요청할 수 있고, 승인된 요청은 더 이상 반려할 수 없다
        mvc.perform(post(request).header("X-User-Id", 10).header("X-User-Role", "HOST")
                .contentType("application/json").content("{\"reason\":\"다시 취소\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/festivals/" + f.getId() + "/approve-cancellation").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(post(reject).header("X-User-Id", 1).header("X-User-Role", "ADMIN")).andExpect(status().isConflict());
    }
}
