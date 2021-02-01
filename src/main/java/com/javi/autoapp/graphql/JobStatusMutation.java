package com.javi.autoapp.graphql;

import static com.javi.autoapp.graphql.type.Status.FINISHED;

import com.coxautodev.graphql.tools.GraphQLMutationResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.service.AutoTradingService;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobStatusMutation implements GraphQLMutationResolver {
    private final CacheManager cacheManager;
    private final AutoTradingService tradingService;
    private final AutoAppDao autoAppDao;

    public JobStatus createJob(
            Currency currency,
            Double max,
            Double min,
            Double funds,
            String expires)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        // Subscribe to currency feed
        tradingService.subscribe(currency);

        // Create initial purchase
        CoinbaseOrderRequest init = new CoinbaseOrderRequest();
        init.setSide(CoinbaseOrderRequest.BUY);
        init.setFunds(String.valueOf(funds));
        init.setProductId(currency.getLabel());

        // Purchase funds
        tradingService.initBuy(init);

        // Create init job settings
        JobSettings settings = new JobSettings();
        settings.setCurrency(currency.getLabel());
        settings.setMax(max);
        settings.setMin(min);
        settings.setFunds(funds);
        settings.setExpires(expires);
        autoAppDao.startOrUpdateJob(settings);

        // Create init job status
        JobStatus status = new JobStatus();
        status.setCurrentFundsUsd(funds);
        status.setStartingFundsUsd(funds);
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
        autoAppDao.updateJobStatus(status);
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