package com.learning.discovery.user.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

@Slf4j
class HeartbeatJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info("Heartbeat from user-service, next fire at {}", context.getNextFireTime());
    }
}
