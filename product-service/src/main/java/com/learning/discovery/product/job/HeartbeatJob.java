package com.learning.discovery.product.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
class HeartbeatJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info("Heartbeat from product-service (fire time {})", context.getFireTime());
    }
}
