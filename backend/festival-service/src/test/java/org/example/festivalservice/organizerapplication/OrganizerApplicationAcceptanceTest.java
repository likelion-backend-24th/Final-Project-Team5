package org.example.festivalservice.organizerapplication;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OrganizerApplicationAcceptanceTest {

    private static final String ENDPOINT = "/api/organizer-applications";
    private static final String REQUEST_BODY = """
            {"introduction":"소개","contact":"010-0000-0000"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizerApplicationRepository organizerApplicationRepository;

    @BeforeEach
    void setUp() {
        organizerApplicationRepository.deleteAll();
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
        organizerApplicationRepository.save(new OrganizerApplication(1L, "소개", "010-0000-0000"));

        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("DUPLICATE_APPLICATION")));
    }

    @Test
    void submitRejectsAlreadyOrganizer() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ALREADY_ORGANIZER")));
    }

    @Test
    void submitAllowsReapplicationAfterRejection() throws Exception {
        OrganizerApplication rejected = new OrganizerApplication(1L, "소개", "010-0000-0000");
        ReflectionTestUtils.setField(rejected, "status", OrganizerApplicationStatus.REJECTED);
        organizerApplicationRepository.save(rejected);

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
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }
}
