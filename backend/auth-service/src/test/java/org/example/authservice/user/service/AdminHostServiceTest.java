package org.example.authservice.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminHostServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminHostService adminHostService;

    @Test
    void 운영자는_실제_HOST_계정_목록을_조회한다() {
        User host = new User();
        host.setId(7L);
        host.setNickname("주최자");
        host.setUsername("host@example.com");
        host.setRole(Role.HOST);
        host.setStatus(AccountStatus.ACTIVE);
        host.setCreatedAt(LocalDateTime.of(2026, 9, 15, 12, 0));
        given(userRepository.findByRoleOrderByCreatedAtDesc(Role.HOST)).willReturn(List.of(host));

        var response = adminHostService.listHosts("ADMIN");

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.nickname()).isEqualTo("주최자");
            assertThat(item.email()).isEqualTo("host@example.com");
            assertThat(item.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(item.joinedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0));
        });
    }

    @Test
    void 운영자가_아니면_HOST_계정_목록을_조회할_수_없다() {
        assertThatThrownBy(() -> adminHostService.listHosts("HOST"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(UserErrorCode.FORBIDDEN_ADMIN_ROLE));
    }
}
