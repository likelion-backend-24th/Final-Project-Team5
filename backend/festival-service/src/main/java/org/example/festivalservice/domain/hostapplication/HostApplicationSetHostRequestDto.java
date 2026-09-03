package org.example.festivalservice.domain.hostapplication;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class HostApplicationSetHostRequestDto {
    @NotNull
    private HostApplicationStatus status;

    @Size(max = 500)
    private String rejectReason;
}
