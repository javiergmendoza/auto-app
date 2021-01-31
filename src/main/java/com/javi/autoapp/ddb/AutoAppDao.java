package com.javi.autoapp.ddb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.javi.autoapp.ddb.model.AutoAppSettings;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.ddb.model.JobStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@CacheConfig(cacheNames = {"autoAppConfigDao"})
@RequiredArgsConstructor
public class AutoAppDao {
    private final DynamoDBMapper mapper;

    @Cacheable
    public AutoAppSettings getAutoAppSettings() {
        AutoAppSettings settings = new AutoAppSettings();
        return mapper.load(settings);
    }

    @Cacheable
    public JobSettings getJobSettings(String jobId) {
        JobSettings settings = new JobSettings();
        settings.setJobId(jobId);
        return mapper.load(settings);
    }

    @Cacheable
    public List<JobSettings> getAllJobSettings() {
        DynamoDBQueryExpression<JobSettings> query = new DynamoDBQueryExpression<>();
        query.withHashKeyValues(new JobSettings());
        return mapper.query(JobSettings.class, query);
    }

    public void createJob(JobSettings job) {
        mapper.save(job);
    }

    public void deleteJob(JobSettings job) {
        mapper.delete(job);
    }

    public void stopJob(String jobId) {
        JobSettings settings = getJobSettings(jobId);
        settings.setExpires(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        mapper.save(settings);
    }

    @Cacheable
    public List<JobStatus> getJobStatuses() {
        DynamoDBQueryExpression<JobStatus> query = new DynamoDBQueryExpression<>();
        query.withHashKeyValues(new JobStatus());
        return mapper.query(JobStatus.class, query);
    }

    @Cacheable
    public JobStatus getJobStatus(String jobId) {
        JobStatus status = new JobStatus();
        status.setJobId(jobId);
        return mapper.load(status);
    }

    public void updateJobStatus(JobStatus status) {
        mapper.save(status);
    }


}
