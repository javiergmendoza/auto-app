package com.javi.autoapp.graphql;

import static com.javi.autoapp.graphql.type.Status.STOPPED;

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.util.CacheHelper;
import graphql.schema.DataFetchingEnvironment;
import graphql.servlet.GenericGraphQLError;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobStatusMutation implements GraphQLMutationResolver {
    private final CacheManager cacheManager;
    private final AutoAppDao autoAppDao;

    public JobStatus createJob(
            Currency currency,
            int precisionFromCent,
            double percentageYieldThreshold,
            double totalPercentageYieldThreshold,
            double floor,
            double funds,
            String expires) {
        // Create init job settings
        JobSettings settings = new JobSettings();
        settings.setProductId(currency.getLabel());
        settings.setPrecisionFromCent(precisionFromCent);
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

    public JobStatus updateJob(
            String jobID,
            Optional<Integer> precisionFromCent,
            Optional<Double> percentageYieldThreshold,
            Optional<Double> totalPercentageYieldThreshold,
            Optional<Double> floor,
            Optional<String> expires,
            DataFetchingEnvironment environment) {
        // Job settings
        JobSettings settings = autoAppDao.getJobSettings(jobID);
        if (settings == null) {
            environment.getExecutionContext().addError(new GenericGraphQLError("Unable to find specified job."));
            return null;
        }

        precisionFromCent.ifPresent(settings::setPrecisionFromCent);
        percentageYieldThreshold.ifPresent(settings::setPercentageYieldThreshold);
        totalPercentageYieldThreshold.ifPresent(settings::setTotalPercentageYieldThreshold);
        floor.ifPresent(settings::setFloor);
        expires.ifPresent(settings::setExpires);
        autoAppDao.startOrUpdateJob(settings);

        // Bust the cache
        CacheHelper.bustCache(cacheManager);

        return autoAppDao.getJobStatus(jobID);
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