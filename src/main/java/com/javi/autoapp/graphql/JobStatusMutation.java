package com.javi.autoapp.graphql;

import static com.javi.autoapp.graphql.type.Status.FINISHED;
import static com.javi.autoapp.graphql.type.Status.RUNNING;

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.ddb.model.JobStatus;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobStatusMutation implements GraphQLMutationResolver {
    private final CacheManager cacheManager;
    private final AutoAppDao autoAppDao;

    public JobStatus createJob(
            Currency currency,
            Double max,
            Double min,
            Double funds,
            String expires) {
        String jobId = UUID.randomUUID().toString();

        JobSettings settings = new JobSettings();
        settings.setJobId(jobId);
        settings.setCurrency(currency.getLabel());
        settings.setMax(max);
        settings.setMin(min);
        settings.setFunds(funds);
        settings.setExpires(expires);
        autoAppDao.createJob(settings);

        JobStatus status = new JobStatus();
        status.setJobId(jobId);
        status.setCurrentFundsUsd(funds);
        status.setStartingFundsUsd(funds);
        status.setGainsLosses(0);
        status.setStatus(RUNNING);
        autoAppDao.updateJobStatus(status);

        // Bust the cache
        Collection<String> cacheNames = cacheManager.getCacheNames();
        cacheNames.forEach(this::getCacheAndClear);

        return status;
    }

    public JobStatus stopJob(String id) {
        autoAppDao.stopJob(id);

        // Bust the cache
        Collection<String> cacheNames = cacheManager.getCacheNames();
        cacheNames.forEach(this::getCacheAndClear);

        JobStatus status = autoAppDao.getJobStatus(id);
        status.setStatus(FINISHED);
        return status;
    }

    private void getCacheAndClear(final String cacheName) {
        final Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalArgumentException("invalid cache name: " + cacheName);
        }
        cache.clear();
    }
}