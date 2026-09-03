package org.example.festivalservice.hostapplication;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.festivalservice.domain.hostapplication.HostApplication;
import org.example.festivalservice.domain.hostapplication.HostApplicationRepository;
import org.example.festivalservice.domain.hostapplication.HostApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HostApplicationAcceptanceTest {

    private static final String ENDPOINT = "/api/host-applications";
    private static final String REQUEST_BODY = """
            {"introduction":"소개","contact":"010-0000-0000"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HostApplicationRepository hostApplicationRepository;

    @BeforeEach
    void setUp() {
        hostApplicationRepository.deleteAll();
    }

    @Test
    void submitThenGetMyApplicationSucceeds() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PENDING")));

        mockMvc.perform(get(ENDPOINT + "/me").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    void submitRejectsDuplicatePendingApplication() throws Exception {
        hostApplicationRepository.save(new HostApplication(1L, "소개", "010-0000-0000"));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_APPLICATION")));
    }

    @Test
    void submitRejectsAlreadyHost() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ALREADY_HOST")));
    }

    @Test
    void submitAllowsReapplicationAfterRejection() throws Exception {
        HostApplication rejected = new HostApplication(1L, "소개", "010-0000-0000");
        ReflectionTestUtils.setField(rejected, "status", HostApplicationStatus.REJECTED);
        hostApplicationRepository.save(rejected);

        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    void submitWithoutAuthHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }
}
