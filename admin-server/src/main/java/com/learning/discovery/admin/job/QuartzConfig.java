package com.learning.discovery.admin.job;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class QuartzConfig {

    @Bean
    JobDetail heartbeatJobDetail() {
        return JobBuilder.newJob(HeartbeatJob.class)
                .withIdentity("heartbeatJob", "demo")
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
