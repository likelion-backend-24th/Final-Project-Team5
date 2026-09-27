package org.example.authservice.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.example.authservice.admin.service.AdminHostService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        host.setStatus(AccountStatus.SUSPENDED);
        host.setCreatedAt(LocalDateTime.of(2026, 9, 15, 12, 0));
        host.setSuspendReason("허위 행사 등록");
        host.setSuspendedAt(LocalDateTime.of(2026, 9, 20, 10, 0));

        Pageable pageable = PageRequest.of(0, 10);
        given(userRepository.searchForAdmin("주최", Role.HOST, AccountStatus.SUSPENDED, pageable))
                .willReturn(new PageImpl<>(List.of(host), pageable, 1));

        var response = adminHostService.listHosts("ADMIN", "  주최  ", AccountStatus.SUSPENDED, pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.nickname()).isEqualTo("주최자");
            assertThat(item.email()).isEqualTo("host@example.com");
            assertThat(item.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(item.joinedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 12, 0));
            assertThat(item.suspendReason()).isEqualTo("허위 행사 등록");
            assertThat(item.suspendedAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 0));
        });
    }

    @Test
    void 검색어가_비어있으면_검색_조건_없이_조회한다() {
        Pageable pageable = PageRequest.of(0, 10);
        given(userRepository.searchForAdmin(null, Role.HOST, null, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        var response = adminHostService.listHosts("ADMIN", "   ", null, pageable);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void 운영자가_아니면_HOST_계정_목록을_조회할_수_없다() {
        assertThatThrownBy(() -> adminHostService.listHosts("HOST", null, null, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(UserErrorCode.FORBIDDEN_ADMIN_ROLE));
    }
}