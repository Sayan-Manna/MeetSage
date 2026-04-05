package com.ai.meetsage;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies that the Spring Modulith module structure is respected.
 * This test will fail if any module accesses another module's internal packages.
 */
class ModularityTests {

    @Test
    void verifyModularStructure() {
        ApplicationModules.of(MeetsageApplication.class).verify();
    }
}
