package com.learning.discovery.gateway.job;

import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

class HeartbeatJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info("Heartbeat from gateway-service (fire time {})", context.getFireTime());
    }
}
