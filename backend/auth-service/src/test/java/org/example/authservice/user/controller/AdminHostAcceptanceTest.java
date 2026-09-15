package org.example.authservice.user.controller;

import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 주최자 목록 API의 역할 검증과 실제 Repository 조회를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminHostAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("host@test.com", "주최자", Role.HOST));
        userRepository.save(user("user@test.com", "참가자", Role.USER));
    }

    @Test
    void 운영자는_HOST_계정만_조회한다() throws Exception {
        mockMvc.perform(get("/api/admin/hosts").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email", is("host@test.com")))
                .andExpect(jsonPath("$.data[0].nickname", is("주최자")))
                .andExpect(jsonPath("$.data[0].accountStatus", is("ACTIVE")));
    }

    @Test
    void 운영자가_아니면_HOST_계정_목록을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/hosts").header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN_ADMIN_ROLE")));
    }

    private User user(String email, String nickname, Role role) {
        User user = new User();
        user.setName(nickname);
        user.setUsername(email);
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
