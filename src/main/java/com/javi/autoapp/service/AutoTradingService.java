package com.javi.autoapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.client.model.CoinbaseOrderResponse;
import com.javi.autoapp.client.model.CoinbaseStatsResponse;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.graphql.type.Status;
import com.javi.autoapp.util.CacheHelper;
import com.javi.autoapp.util.SignatureTool;
import io.netty.handler.codec.http.HttpMethod;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTradingService implements Runnable {
    private static final double WINDOW_BUFFER = 1.01;
    private static final double COINBASE_PERCENTAGE = 0.0149;
    private static final double WARNING_DELTA = 1.1;
    private final ObjectMapper mapper = new ObjectMapper();

    private final CacheManager cacheManager;
    private final CoinbaseTraderClient coinbaseTraderClient;
    private final AutoAppDao autoAppDao;
    private final ProductsService productsService;

    private ScheduledFuture<?> scheduledFuture;
    private boolean bustCache = true;

    @PostConstruct
    public void postConstruct() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = executor.scheduleAtFixedRate(this, 0, 30, TimeUnit.SECONDS);
    }

    @Override
    public void run(){
        if (bustCache) {
            CacheHelper.bustCache(cacheManager);
            bustCache = false;
        }
        List<JobSettings> jobs = autoAppDao.getAllJobSettings();

        if (jobs.isEmpty()) {
            return;
        } else {
            log.info("Processing {} jobs.", jobs.size());
        }

        Set<String> productIds = jobs.stream()
                .filter(JobSettings::isActive)
                .map(JobSettings::getProductId)
                .collect(Collectors.toSet());

        try {
            productsService.updateSubscribedCurrencies(productIds);
        } catch (JsonProcessingException error) {
            log.error("Failed to update subcription tickers. Error: {}", error.getMessage());
        }

        deactivateCompletedJobs(jobs);
        cleanupCompletedJobs(jobs);
        checkPendingOrders(jobs);
        autoTrade(jobs);
    }

    private void deactivateCompletedJobs(List<JobSettings> jobs) {
        jobs.stream()
                .filter(job -> {
                    boolean expired = Instant.now().isAfter(Instant.parse(job.getExpires()));
                    boolean reachTotalYieldThreshold = (job.getFunds() / job.getStartingFundsUsd()) > job.getTotalPercentageYieldThreshold();
                    return expired || reachTotalYieldThreshold;
                })
                .forEach(job -> {
                    log.info("Deactivating expired job: {}", job.getJobId());
                    job.setActive(false);
                    autoAppDao.startOrUpdateJob(job);
                    bustCache = true;
                });
    }

    private void checkPendingOrders(List<JobSettings> jobs) {
        jobs.stream().filter(JobSettings::isPending).forEach(job -> {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature;
            try {
                signature = SignatureTool.getSignature(
                        timestamp,
                        HttpMethod.GET.name(),
                        SignatureTool.getOrdersRequestPath(job.getOrderId())
                );
            } catch (Exception e) {
                log.error("Exception occurred in order polling thread. Error: {}", e.getMessage());
                return;
            }
            coinbaseTraderClient.getOrderStatus(
                    timestamp,
                    signature,
                    job.getOrderId()
            ).subscribe(resp -> {
                if (resp.statusCode().isError()) {
                    resp.bodyToMono(String.class).subscribe(error -> log.error("Failed to get order status. Error: {}", error));
                } else {
                    resp.bodyToMono(CoinbaseOrderResponse.class).subscribe(order -> {
                        try {
                            log.info("Finalized transaction for job ID: {}", job.getJobId());
                            updatePendingJob(order, job);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    });
                }
            });
        });
    }

    private void updatePendingJob(
            CoinbaseOrderResponse response,
            JobSettings job) throws JsonProcessingException {
        if (response.isSettled()) {
            double value;
            double size;
            try {
                value = Double.parseDouble(response.getExecutedValue());
                size = Double.parseDouble(response.getFilledSize());
            } catch (Exception e) {
                log.error("Exception occurred trying to parse trade response. Error: {}", e.getMessage());
                return;
            }

            JobStatus jobStatus = autoAppDao.getJobStatus(job.getJobId());
            if (!job.isSell()) {
                job.setSell(true);
                job.setSize(size);
                jobStatus.setCurrentFundsUsd(0.0);
                jobStatus.setSize(size);
                jobStatus.setCurrentValueUsd(value);
            }
            else {
                job.setSell(false);
                job.setFunds(value);

                if (job.isActive() && job.getIncreaseFundsBy() > 0.0) {
                    job.setStartingFundsUsd(job.getStartingFundsUsd() + job.getIncreaseFundsBy());
                    job.setFunds(job.getFunds() + job.getIncreaseFundsBy());
                    job.setIncreaseFundsBy(0.0);
                    jobStatus.setStartingFundsUsd(job.getStartingFundsUsd());
                }

                // Update status
                jobStatus.setCurrentFundsUsd(job.getFunds());
                jobStatus.setSize(0.0);
                jobStatus.setCurrentValueUsd(job.getFunds());
            }

            job.setOrderId(UUID.randomUUID().toString());
            job.setInit(false);
            job.setPending(false);
            job.setCrossedLowThreshold(false);
            job.setCrossedPercentageYieldThreshold(false);
            job.setMaxYieldValue(0.0);
            job.setTradeNow(false);
            autoAppDao.startOrUpdateJob(job);

            // Update job status
            if (!job.isActive() && !job.isSell()) {
                jobStatus.setStatus(Status.FINISHED);
            } else {
                jobStatus.setStatus(Status.RUNNING);
            }
            jobStatus.setPrice(value / size);
            jobStatus.setGainsLosses(value - jobStatus.getStartingFundsUsd());
            autoAppDao.updateJobStatus(jobStatus);

            bustCache = true;
        }
    }

    private void autoTrade(List<JobSettings> jobs) {
        jobs.stream()
                .filter(job -> job.isActive() || job.isSell())
                .filter(job -> !job.isPending())
                .forEach(job -> {
                    // Check for latest stored price
                    Optional<String> priceString = productsService.getPrice(job.getProductId());
                    if (!priceString.isPresent()) {
                        return;
                    }

                    double absolutePrice;
                    try {
                        absolutePrice = Double.parseDouble(priceString.get());
                    } catch (Exception e) {
                        log.error("Exception occurred trying to parse price for {}. Error: {}", job.getProductId(), e.getMessage());
                        return;
                    }
                    double price = roundPrice(absolutePrice, job.getPrecision());

                    if (job.isInit()) {
                        try {
                            init(job, price);
                        } catch (Exception e) {
                            log.error("Failed to handle init period. Exception: {}", e.getMessage());
                        }
                    } else if (job.isSell()) {
                        try {
                            crest(job, price);
                        } catch (Exception e) {
                            log.error("Failed to handle crest period. Exception: {}", e.getMessage());
                        }
                    } else {
                        try {
                            trough(job, price);
                        } catch (Exception e) {
                            log.error("Failed to handle trough period. Exception: {}", e.getMessage());
                        }
                    }
                });
    }

    private void init(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        CoinbaseStatsResponse stats = productsService.getProductStats(job.getProductId());
        if (stats == null) {
            log.error("Stats for {} are null.", job.getProductId());
            return;
        }

        double midPrice;
        try {
            midPrice = roundPrice(Double.parseDouble(stats.getOpen()), job.getPrecision());
        } catch (Exception e) {
            log.error("Exception parsing stats occurred. Error: {}", e.getMessage());
            return;
        }

        log.info("Checking pending jobId: {} - ProductId: {}, CurrentPrice: {}, MidPrice: {}",
                job.getJobId(),
                job.getProductId(),
                price,
                midPrice);

        if ((job.isCrossedLowThreshold() && price > job.getMinValue())
                || job.isTradeNow()) {

            // Send trade request
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setOrderId(job.getOrderId());
            request.setSide(CoinbaseOrderRequest.BUY);
            request.setFunds(String.valueOf(job.getFunds()));
            request.setProductId(job.getProductId());
            boolean success = trade(request);

            // Update job to hold until sell is complete
            if (success) {
                job.setPending(true);
                autoAppDao.startOrUpdateJob(job);
                bustCache = true;
            } else {
                job.setOrderId(UUID.randomUUID().toString());
                autoAppDao.startOrUpdateJob(job);
                bustCache = true;
            }

            return;
        }

        if (!job.isCrossedLowThreshold() && price < midPrice) {
            log.info("Job ID: {} - Crossed {} below low threshold.", job.getJobId(), job.getProductId());
            job.setCrossedLowThreshold(true);
            job.setMinValue(price);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }

        if (job.isCrossedLowThreshold() && price < job.getMinValue()) {
            job.setMinValue(price);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }
    }

    private void trough(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        CoinbaseStatsResponse stats = productsService.getProductStats(job.getProductId());
        if (stats == null) {
            log.error("Stats for {} are null.", job.getProductId());
            return;
        }

        double minPrice;
        double midPrice;
        try {
            minPrice = roundPrice(Double.parseDouble(stats.getLow()), job.getPrecision());
            midPrice = roundPrice(Double.parseDouble(stats.getOpen()), job.getPrecision());
        } catch (Exception e) {
            log.error("Exception parsing stats occurred. Error: {}", e.getMessage());
            return;
        }

        double expectedBuy = job.getFunds() / price;
        double expectedFees = expectedBuy * COINBASE_PERCENTAGE;
        double expectedSize = expectedBuy - expectedFees;
        double percentYield = expectedSize / job.getSize();
        double priceWantedAbsolute = job.getFunds() / ((job.getPercentageYieldThreshold() * job.getSize()) / (1 - COINBASE_PERCENTAGE));
        double priceWanted = roundPrice(priceWantedAbsolute, job.getPrecision());

        //Looking for price of 0.376 which is below the 24 hour low of 0.388
        if (priceWanted < minPrice) {
            log.warn("Looking for price of {} which is below the 24 hour low of {}. Current mid price is: {}. Current price is: {}",
                    priceWanted,
                    minPrice,
                    midPrice,
                    price);
        }

        log.info("Checking crest jobId: {} - ProductId: {}, CurrentYield: {}, YieldWanted: {}, CurrentPrice: {}, PriceWanted: {}, MidPrice: {}",
                job.getJobId(),
                job.getProductId(),
                percentYield,
                job.getPercentageYieldThreshold(),
                price,
                priceWanted,
                midPrice);

        if ((job.isCrossedPercentageYieldThreshold() && percentYield < job.getMaxYieldValue())
                || job.isTradeNow()) {
            if (price > midPrice && !job.isTradeNow()) {
                log.warn("WARNING!!! This trade will buy product {} above 24 hour mid market value of {}. Must force trade to proceed.",
                        job.getProductId(),
                        midPrice);
                return;
            } else if (percentYield < 1.0 && job.isTradeNow()) {
                log.warn("WARNING!!! This sale is going to cost more than would gain. Net loss percentage will be: {}", percentYield);
            }else if (percentYield < 1.0) {
                log.error("ERROR!!! This sale would cost more than would gain. Net loss percentage would be: {}", percentYield);
                return;
            } else if (percentYield < WARNING_DELTA) {
                log.warn("WARNING!!! This sale is below the {}% gain warning threshold. Net gain percent will be: {}%", WARNING_DELTA * 100, percentYield * 100);
            }

            // Send trade request
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setOrderId(job.getOrderId());
            request.setSide(CoinbaseOrderRequest.BUY);
            request.setFunds(String.valueOf(job.getFunds()));
            request.setProductId(job.getProductId());
            boolean success = trade(request);

            // Update job to hold until sell is complete
            if (success) {
                job.setPending(true);
                autoAppDao.startOrUpdateJob(job);
                bustCache = true;
            } else {
                job.setOrderId(UUID.randomUUID().toString());
                autoAppDao.startOrUpdateJob(job);
                bustCache = true;
            }

            return;
        }

        if (!job.isCrossedPercentageYieldThreshold() && percentYield > job.getPercentageYieldThreshold()) {
            log.info("Job ID: {} - Crossed {} yield threshold of {}",
                    job.getJobId(),
                    job.getProductId(),
                    job.getPercentageYieldThreshold()
            );
            job.setCrossedPercentageYieldThreshold(true);
            job.setMaxYieldValue(percentYield);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }

        if (job.isCrossedPercentageYieldThreshold() && percentYield > job.getMaxYieldValue()) {
            job.setMaxYieldValue(percentYield);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }
    }

    private void crest(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        CoinbaseStatsResponse stats = productsService.getProductStats(job.getProductId());
        if (stats == null) {
            log.error("Stats for {} are null.", job.getProductId());
            return;
        }

        double maxPrice;
        double midPrice;
        try {
            maxPrice = roundPrice(Double.parseDouble(stats.getHigh()), job.getPrecision());
            midPrice = roundPrice(Double.parseDouble(stats.getOpen()), job.getPrecision());
        } catch (Exception e) {
            log.error("Exception parsing stats occurred. Error: {}", e.getMessage());
            return;
        }

        double expectedSale = price * job.getSize();
        double expectedFees = expectedSale * COINBASE_PERCENTAGE;
        double expectedFunds = expectedSale - expectedFees;
        double percentYield = expectedFunds / job.getFunds();
        double priceWantedAbsolute = (job.getPercentageYieldThreshold() * job.getFunds()) / (job.getSize() - (job.getSize() * COINBASE_PERCENTAGE));
        double priceWanted = roundPrice(priceWantedAbsolute, job.getPrecision());

        if (priceWanted > maxPrice) {
            log.warn("Looking for price of {} which is above the 24 hour high of {}. Current mid price is: {}. Current price is: {}",
                    priceWanted,
                    maxPrice,
                    midPrice,
                    price);
        }

        log.info("Checking crest jobId: {} - ProductId: {}, CurrentYield: {}, YieldWanted: {}, CurrentPrice: {}, PriceWanted: {}, MidPrice: {}",
                job.getJobId(),
                job.getProductId(),
                percentYield,
                job.getPercentageYieldThreshold(),
                price,
                priceWanted,
                midPrice);

        if ((job.isCrossedPercentageYieldThreshold() && percentYield < job.getMaxYieldValue())
                || job.isTradeNow()) {
            if (price < midPrice && !job.isTradeNow()) {
                log.warn("WARNING!!! This trade will sell product {} above 24 hour mid market value of {}. Must force trade to proceed.",
                        job.getProductId(),
                        midPrice);
                return;
            } else if (percentYield < 1.0 && job.isTradeNow()) {
                log.warn("WARNING!!! This sale is going to cost more than would gain. Net loss percentage will be: {}", percentYield);
            }else if (percentYield < 1.0) {
                log.error("ERROR!!! This sale would cost more than would gain. Net loss percentage would be: {}", percentYield);
                return;
            } else if (percentYield < WARNING_DELTA) {
                log.warn("WARNING!!! This sale is below the {}% gain warning threshold. Net gain percent will be: {}%", WARNING_DELTA * 100, percentYield * 100);
            }

            // Send trade request
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setOrderId(job.getOrderId());
            request.setSide(CoinbaseOrderRequest.SELL);
            request.setSize(String.valueOf(job.getSize()));
            request.setProductId(job.getProductId());
            boolean success = trade(request);

            // Update job to hold until sell is complete
            if (success) {
                job.setPending(true);
                autoAppDao.startOrUpdateJob(job);
                bustCache = true;
            } else {
                job.setOrderId(UUID.randomUUID().toString());
                autoAppDao.startOrUpdateJob(job);
                bustCache = true;
            }

            return;
        }

        if (!job.isCrossedPercentageYieldThreshold() && percentYield > job.getPercentageYieldThreshold()) {
            log.info("Job ID: {} - Crossed {} yield threshold of {}",
                    job.getJobId(),
                    job.getProductId(),
                    job.getPercentageYieldThreshold()
            );
            job.setCrossedPercentageYieldThreshold(true);
            job.setMaxYieldValue(percentYield);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }

        if (job.isCrossedPercentageYieldThreshold() && percentYield > job.getMaxYieldValue()) {
            job.setMaxYieldValue(percentYield);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }
    }

    private void cleanupCompletedJobs(List<JobSettings> jobs) {
        jobs.stream()
                .filter(job -> !job.isActive())
                .filter(job -> !job.isPending())
                .forEach(job -> {
                    log.info("Removing completed job: {}", job.getJobId());
                    autoAppDao.deleteJob(job);
                    bustCache = true;
                });
    }

    private boolean trade(CoinbaseOrderRequest order)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = SignatureTool.getSignature(
                timestamp,
                HttpMethod.POST.name(),
                SignatureTool.getOrdersRequestPath(),
                mapper.writeValueAsString(order)
        );

        log.info("Sending trade request: {}", mapper.writeValueAsString(order));


        ClientResponse response = coinbaseTraderClient.trade(timestamp, signature, mapper.writeValueAsString(order)).block();

        if (response.statusCode().isError()) {
            log.error("Failed to place order.");
            response.bodyToMono(String.class).subscribe(error -> log.error("Failed to place order. Error: {}", error));
            return false;
        }

        return true;
    }

    private double roundPrice(double absolutePrice, double precision) {
        double precisionPlace = Math.pow(10, -precision);
        return Math.round(absolutePrice * precisionPlace) / precisionPlace;
    }
}
