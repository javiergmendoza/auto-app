package com.javi.autoapp.graphql;

import static com.javi.autoapp.graphql.type.Status.STOPPED;

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.util.CacheHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobStatusMutation implements GraphQLMutationResolver {
    private final CacheManager cacheManager;
    private final AutoAppDao autoAppDao;

    public JobStatus createJob(
            Currency currency,
            Double percentageYieldThreshold,
            Double totalPercentageYieldThreshold,
            Double floor,
            Double funds,
            String expires) {
        // Create init job settings
        JobSettings settings = new JobSettings();
        settings.setProductId(currency.getLabel());
        settings.setPercentageYieldThreshold(percentageYieldThreshold);
        settings.setTotalPercentageYieldThreshold(totalPercentageYieldThreshold);
        settings.setFloor(floor);
        settings.setFunds(funds);
        settings.setStartingFundsUsd(funds);
        settings.setExpires(expires);
        autoAppDao.startOrUpdateJob(settings);

        // Create init job status
        JobStatus status = new JobStatus();
        status.setJobId(settings.getJobId());
        status.setCurrentValueUsd(funds);
        status.setCurrentFundsUsd(funds);
        status.setStartingFundsUsd(funds);
        status.setCurrency(currency);
        autoAppDao.updateJobStatus(status);

        // Bust the cache
        CacheHelper.bustCache(cacheManager);

        return status;
    }

    public JobStatus stopJob(String id) {
        // Stop job
        JobSettings job = autoAppDao.getJobSettings(id);
        job.setActive(false);
        autoAppDao.startOrUpdateJob(job);

        // Bust the cache
        CacheHelper.bustCache(cacheManager);

        JobStatus status = autoAppDao.getJobStatus(id);
        status.setStatus(STOPPED);
        autoAppDao.updateJobStatus(status);
        return status;
    }
}