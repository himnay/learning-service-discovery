package com.learning.discovery.order.job;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Boot's Quartz auto-config registers these with the scheduler (in-memory job store).
@Configuration
class QuartzJobConfig {

    @Bean
    JobDetail heartbeatJobDetail() {
        return JobBuilder.newJob(HeartbeatJob.class)
                .withIdentity("heartbeatJob", "demo")
                .withDescription("Logs a heartbeat line every minute")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger heartbeatTrigger(JobDetail heartbeatJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(heartbeatJobDetail)
                .withIdentity("heartbeatTrigger", "demo")
                .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever())
                .build();
    }
}
