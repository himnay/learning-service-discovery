package com.learning.discovery.user.job;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Demo job so the SBA Quartz view has something to show
@Configuration(proxyBeanMethods = false)
class QuartzConfig {

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
                .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(1))
                .build();
    }
}
