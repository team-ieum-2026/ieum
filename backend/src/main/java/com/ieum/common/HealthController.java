// 주인: BE-A
package com.ieum.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/health → { "data": { "status": "OK" } } */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.of(Map.of("status", "OK"));
    }
}
