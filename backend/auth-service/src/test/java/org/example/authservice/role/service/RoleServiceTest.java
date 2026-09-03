package org.example.authservice.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.role.dto.GrantRoleRequest;
import org.example.authservice.role.entity.RoleGrant;
import org.example.authservice.role.repository.RoleGrantRepository;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleGrantRepository roleGrantRepository;

    private RoleService roleService;

    @Test
    void grantsHostRoleToUserOnFirstRequest() {
        roleService = new RoleService(userRepository, roleGrantRepository);
        User user = new User();
        user.setRole(Role.USER);
        when(roleGrantRepository.findByApplicationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        roleService.grantRole(new GrantRoleRequest(100L, 1L, "HOST"));

        assertThat(user.getRole()).isEqualTo(Role.HOST);
        verify(userRepository).save(user);
        verify(roleGrantRepository).save(any(RoleGrant.class));
    }

    @Test
    void sameApplicationIdIsIdempotentAndSkipsRoleChange() {
        roleService = new RoleService(userRepository, roleGrantRepository);
        when(roleGrantRepository.findByApplicationId(1L))
                .thenReturn(Optional.of(new RoleGrant(1L, 100L, "HOST")));

        roleService.grantRole(new GrantRoleRequest(100L, 1L, "HOST"));

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(roleGrantRepository, never()).save(any(RoleGrant.class));
    }

    @Test
    void invalidRoleValueIsRejected() {
        roleService = new RoleService(userRepository, roleGrantRepository);
        when(roleGrantRepository.findByApplicationId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.grantRole(new GrantRoleRequest(100L, 1L, "NOT_A_ROLE")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unknownUserIsRejected() {
        roleService = new RoleService(userRepository, roleGrantRepository);
        when(roleGrantRepository.findByApplicationId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.grantRole(new GrantRoleRequest(100L, 1L, "HOST")))
                .isInstanceOf(ApiException.class);
    }
}
