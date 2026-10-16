package com.learning.discovery.eureka.config;

import com.learning.discovery.eureka.job.HeartbeatJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Boot auto-registers JobDetail/Trigger beans with the in-memory (RAMJobStore) scheduler. */
@Configuration(proxyBeanMethods = false)
class QuartzConfig {

    @Bean
    JobDetail heartbeatJobDetail() {
        return JobBuilder.newJob(HeartbeatJob.class)
                .withIdentity("heartbeatJob", "eureka")
                .withDescription("Logs a heartbeat line every minute")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger heartbeatTrigger(JobDetail heartbeatJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(heartbeatJobDetail)
                .withIdentity("heartbeatTrigger", "eureka")
                .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever())
                .build();
    }
}
