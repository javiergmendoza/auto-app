package com.javi.autoapp.ddb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.ddb.model.JobStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@CacheConfig(cacheNames = {"coinbaseClient"})
@RequiredArgsConstructor
public class AutoAppDao {
    private final DynamoDBMapper mapper;

    public JobSettings getJobSettings(String jobId) {
        JobSettings job = new JobSettings();
        job.setJobId(jobId);
        return mapper.load(job);
    }

    @Cacheable
    public List<JobSettings> getAllJobSettings() {
        log.info("Cache miss: getAllJobSettings()");
        DynamoDBQueryExpression<JobSettings> query = new DynamoDBQueryExpression<>();
        query.withHashKeyValues(new JobSettings());
        return mapper.query(JobSettings.class, query);
    }

    public void startOrUpdateJob(JobSettings job) {
        mapper.save(job);
    }

    public void deleteJob(JobSettings job) {
        mapper.delete(job);
    }

    public List<JobStatus> getJobStatuses() {
        DynamoDBQueryExpression<JobStatus> query = new DynamoDBQueryExpression<>();
        query.withHashKeyValues(new JobStatus());
        return mapper.query(JobStatus.class, query);
    }

    public JobStatus getJobStatus(String jobId) {
        JobStatus status = new JobStatus();
        status.setJobId(jobId);
        return mapper.load(status);
    }

    public void updateJobStatus(JobStatus status) {
        mapper.save(status);
    }


}
