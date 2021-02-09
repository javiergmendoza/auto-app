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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobStatusMutation implements GraphQLMutationResolver {
    private static final double MINIMUM_PERCENTAGE_YIELD = 1.01;
    private final CacheManager cacheManager;
    private final AutoAppDao autoAppDao;

    public JobStatus createJob(
            Currency currency,
            int precision,
            double percentageYieldThreshold,
            double totalPercentageYieldThreshold,
            double funds,
            String expires,
            DataFetchingEnvironment environment) {
        if (percentageYieldThreshold < MINIMUM_PERCENTAGE_YIELD) {
            environment.getExecutionContext().addError(new GenericGraphQLError("Will not trade less than " + MINIMUM_PERCENTAGE_YIELD + " yield. Anything less will result in losses."));
            return null;
        }

        // Create init job settings
        JobSettings settings = new JobSettings();
        settings.setProductId(currency.getLabel());
        settings.setSell(false);
        settings.setPrecision(precision);
        settings.setPercentageYieldThreshold(percentageYieldThreshold);
        settings.setTotalPercentageYieldThreshold(totalPercentageYieldThreshold);
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
            Optional<Integer> precision,
            Optional<Double> increaseFundsBy,
            Optional<Double> percentageYieldThreshold,
            Optional<Double> totalPercentageYieldThreshold,
            Optional<String> expires,
            DataFetchingEnvironment environment) {
        // Job settings
        JobSettings settings = autoAppDao.getJobSettings(jobID);
        if (settings == null) {
            environment.getExecutionContext().addError(new GenericGraphQLError("Unable to find specified job."));
            return null;
        }

        if (percentageYieldThreshold.isPresent() && percentageYieldThreshold.get() < MINIMUM_PERCENTAGE_YIELD) {
            environment.getExecutionContext().addError(new GenericGraphQLError("Will not trade less than " + MINIMUM_PERCENTAGE_YIELD + " yield. Anything less will result in losses."));
            return null;
        }

        JobStatus status = autoAppDao.getJobStatus(jobID);
        increaseFundsBy.ifPresent(funds -> {
            if (settings.isActive() && !settings.isPending() && !settings.isSell()) {
                settings.setStartingFundsUsd(settings.getStartingFundsUsd() + funds);
                settings.setFunds(settings.getFunds() + funds);
                status.setStartingFundsUsd(settings.getStartingFundsUsd());
                status.setCurrentFundsUsd(settings.getFunds());
                status.setCurrentValueUsd(settings.getFunds());
            } else {
                settings.setIncreaseFundsBy(settings.getIncreaseFundsBy() + funds);
            }
        });
        percentageYieldThreshold.ifPresent(settings::setPercentageYieldThreshold);
        precision.ifPresent(settings::setPrecision);
        totalPercentageYieldThreshold.ifPresent(settings::setTotalPercentageYieldThreshold);
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

    public List<JobStatus> stopAllJobs() {
        // Stop job
        List<JobSettings> jobs = autoAppDao.getAllJobSettings();
        jobs.forEach(job -> {
            job.setActive(false);
            autoAppDao.startOrUpdateJob(job);
        });

        // Bust the cache
        CacheHelper.bustCache(cacheManager);

        List<JobStatus> statuses = autoAppDao.getJobStatuses();
        return statuses.stream().peek(status -> {
            status.setStatus(STOPPED);
            autoAppDao.updateJobStatus(status);
        }).collect(Collectors.toList());
    }
}