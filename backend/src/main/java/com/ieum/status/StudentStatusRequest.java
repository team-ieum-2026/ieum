// 주인: BE-B
package com.ieum.status;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public record StudentStatusRequest(
        @NotNull @Pattern(regexp = "EMPLOYED|SEEKING|RESTING")
        @Schema(allowableValues = {"EMPLOYED", "SEEKING", "RESTING"}) String status) {}
