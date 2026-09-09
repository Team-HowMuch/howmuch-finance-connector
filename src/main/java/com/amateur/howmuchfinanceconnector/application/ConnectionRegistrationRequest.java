package com.amateur.howmuchfinanceconnector.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ConnectionRegistrationRequest(
        @Min(1) int connectionVersion,
        @NotBlank String provider,
        @NotBlank String institutionType,
        @NotBlank String organizationCode,
        @NotBlank String connectedId,
        @NotEmpty List<@Valid AccountRegistration> accounts
) {
    public record AccountRegistration(@NotNull UUID accountRef, @NotBlank String providerAccountRef) {
    }
}
