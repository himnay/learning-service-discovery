package com.learning.discovery.order.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Demo Quartz job, visible (and triggerable) in Spring Boot Admin under Quartz. */
@Slf4j
class HeartbeatJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info("Heartbeat from order-service, next fire at {}", context.getNextFireTime());
    }
}
