package com.optiplant.inventory.logistics.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Carries {@code @EnableScheduling} for {@code TransferDelayScheduler} (design §6.5) — deliberately
 * never on {@code InventoryApplication}: scheduling configuration belongs to the module that uses
 * it, not to the application root.
 */
@Configuration
@EnableScheduling
class LogisticsSchedulingConfig {
}
