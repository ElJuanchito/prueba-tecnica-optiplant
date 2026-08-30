package com.optiplant.inventory.logistics.infrastructure.adapter.in.scheduler;

import com.optiplant.inventory.logistics.application.port.in.DetectTransferDelaysUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled delay detector (R-28, CU-ALE-01, design §6.5). Invoked on a configurable cron
 * (default every 15 minutes); the whole call is wrapped in a {@code try/catch (RuntimeException)}
 * that logs and returns (RNF-OBS-01) — a scheduled trigger has no caller to propagate a failure
 * to, and one missed detection cycle must not stop the next one.
 */
@Component
public class TransferDelayScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(TransferDelayScheduler.class);

	private final DetectTransferDelaysUseCase detectTransferDelaysUseCase;

	public TransferDelayScheduler(DetectTransferDelaysUseCase detectTransferDelaysUseCase) {
		this.detectTransferDelaysUseCase = detectTransferDelaysUseCase;
	}

	@Scheduled(cron = "${optiplant.logistics.delay-detection.cron:0 */15 * * * *}")
	public void detectDelays() {
		try {
			detectTransferDelaysUseCase.detect();
		} catch (RuntimeException ex) {
			LOG.error("Failed to run the transfer delay detection cycle: {}", ex.getMessage(), ex);
		}
	}
}
