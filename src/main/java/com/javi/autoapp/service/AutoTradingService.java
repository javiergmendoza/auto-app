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

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTradingService implements Runnable {
    private static final double WINDOW_BUFFER = 1.02;
    private static final double COINBASE_PERCENTAGE = 0.0149;
    private static final double WARNING_DELTA = 1.1;
    private static final double MINIMUM_FINAL_PERCENT_YIELD = 0.75;
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
                        SignatureTool.getOrdersRequestPath(job.getJobId())
                );
            } catch (Exception e) {
                log.error("Exception occurred in order polling thread. Error: {}", e.getMessage());
                return;
            }
            coinbaseTraderClient.getOrderStatus(
                    timestamp,
                    signature,
                    job.getJobId()
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
            double value = Double.parseDouble(response.getExecutedValue());
            double size = Double.parseDouble(response.getFilledSize());

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

            job.setPending(false);
            job.setCrossedLowThreshold(false);
            job.setCrossedHighThreshold(false);
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
                    double absolutePrice = Double.parseDouble(priceString.get());
                    double price = roundPrice(absolutePrice, job.getPrecision());

                    if (job.isSell()) {
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

    private void trough(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        CoinbaseStatsResponse stats = productsService.getProductStats(job.getProductId());
        double absolutePriceWanted = Double.parseDouble(stats.getOpen()) / job.getPercentageYieldThreshold();
        double priceWanted = roundPrice(absolutePriceWanted, job.getPrecision());
        double absoluteMinPrice = Double.parseDouble(stats.getLow()) * WINDOW_BUFFER;
        double minPrice = roundPrice(absoluteMinPrice, job.getPrecision());
        double absoluteMidPrice = Double.parseDouble(stats.getOpen());
        double midPrice = roundPrice(absoluteMidPrice, job.getPrecision());

        //Looking for price of 0.376 which is below the 24 hour low of 0.388
        if (priceWanted < minPrice) {
            log.warn("Looking for price of {} which is below the 24 hour low of {}. Current mid price is: {}. Current price is: {}",
                    priceWanted,
                    minPrice,
                    midPrice,
                    price);
        }

        log.info("Checking trough jobId: {} - ProductId: {}, CurrentPrice: {}, PriceWanted: {}",
                job.getJobId(),
                job.getProductId(),
                price,
                priceWanted);

        if (job.isCrossedLowThreshold() && price > job.getMinValue()) {
            double expectedBuy = job.getFunds() / price;
            double expectedFees = expectedBuy * COINBASE_PERCENTAGE;
            double expectedSize = expectedBuy - expectedFees;
            double percentYield = expectedSize / job.getSize();

            if (percentYield < 1.0) {
                log.error("ERROR!!! This sale would cost more than would gain. Net loss percentage would be: {}", percentYield);
                return;
            } else if (percentYield < WARNING_DELTA) {
                log.warn("WARNING!!! This sale is below the {}% gain warning threshold. Net gain percent will be: {}%", WARNING_DELTA * 100, percentYield * 100);
            }

            // Update job to hold until sell is complete
            job.setPending(true);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;

            // Send trade request
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setJobId(job.getJobId());
            request.setSide(CoinbaseOrderRequest.BUY);
            request.setFunds(String.valueOf(job.getFunds()));
            request.setProductId(job.getProductId());
            trade(request);
            return;
        }

        // Set top value
        if (price < priceWanted) {
            log.info("Job ID: {} - Crossed {} below low threshold.", job.getJobId(), job.getProductId());
            job.setCrossedLowThreshold(true);
            job.setMinValue(price);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }
    }

    private void crest(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        CoinbaseStatsResponse stats = productsService.getProductStats(job.getProductId());
        double absolutePriceWanted = Double.parseDouble(stats.getOpen()) * job.getPercentageYieldThreshold();
        double priceWanted = roundPrice(absolutePriceWanted, job.getPrecision());
        double absoluteMaxPrice = Double.parseDouble(stats.getHigh()) / WINDOW_BUFFER;
        double maxPrice = roundPrice(absoluteMaxPrice, job.getPrecision());
        double absoluteMidPrice = Double.parseDouble(stats.getOpen());
        double midPrice = roundPrice(absoluteMidPrice, job.getPrecision());

        if (priceWanted > maxPrice) {
            log.warn("Looking for price of {} which is above the 24 hour high of {}. Current mid price is: {}. Current price is: {}",
                    priceWanted,
                    maxPrice,
                    midPrice,
                    price);
        }

        log.info("Checking crest jobId: {} - ProductId: {}, CurrentPrice: {}, PriceWanted: {}",
                job.getJobId(),
                job.getProductId(),
                price,
                priceWanted);

        if (job.isCrossedHighThreshold() && price < job.getMaxValue()) {
            double expectedSale = price * job.getSize();
            double expectedFees = expectedSale * COINBASE_PERCENTAGE;
            double expectedFunds = expectedSale - expectedFees;
            double percentYield = expectedFunds / job.getFunds();

            if (percentYield < 1.0) {
                log.error("ERROR!!! This sale would cost more than would gain. Net loss percentage would be: {}", percentYield);
                return;
            } else if (percentYield < WARNING_DELTA) {
                log.warn("WARNING!!! This sale is below the {}% gain warning threshold. Net gain percent will be: {}%", WARNING_DELTA * 100, percentYield * 100);
            }

            // Update job to hold until sell is complete
            job.setPending(true);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;

            // Send trade request
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setJobId(job.getJobId());
            request.setSide(CoinbaseOrderRequest.SELL);
            request.setSide(String.valueOf(job.getSize()));
            request.setProductId(job.getProductId());
            trade(request);
            return;
        }

        // Set top value
        if (price > priceWanted) {
            log.info("Job ID: {} - Crossed {} above high threshold.", job.getJobId(), job.getProductId());
            job.setCrossedHighThreshold(true);
            job.setMaxValue(price);
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

    private void trade(CoinbaseOrderRequest order)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = SignatureTool.getSignature(
                timestamp,
                HttpMethod.POST.name(),
                SignatureTool.getOrdersRequestPath(),
                mapper.writeValueAsString(order)
        );

        log.info("Sending trade request: {}", mapper.writeValueAsString(order));

        coinbaseTraderClient.trade(timestamp, signature, mapper.writeValueAsString(order)).subscribe(resp -> {
            if (resp.statusCode().isError()) {
                log.error("Failed to place order.");
                resp.bodyToMono(String.class).subscribe(error -> log.error("Failed to place order. Error: {}", error));
            }
        });
    }

    private double roundPrice(double absolutePrice, double precision) {
        double precisionPlace = Math.pow(10, -precision);
        return Math.round(absolutePrice * precisionPlace) / precisionPlace;
    }
}
