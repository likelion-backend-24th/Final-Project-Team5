package org.example.festivalservice.domain.hostapplication;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class HostApplicationSetHostRequestDto {
    @NotNull
    private HostApplicationStatus status;
}
