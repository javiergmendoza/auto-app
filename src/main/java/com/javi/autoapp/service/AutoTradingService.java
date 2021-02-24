package com.javi.autoapp.service;

import static com.javi.autoapp.service.ProductsService.MAX_SEGMENTS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.client.model.CoinbaseOrderResponse;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.graphql.type.Status;
import com.javi.autoapp.model.ProductPriceSegment;
import com.javi.autoapp.util.CacheHelper;
import com.javi.autoapp.util.SignatureTool;
import io.netty.handler.codec.http.HttpMethod;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
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
    private static final double COINBASE_PERCENTAGE = 0.0149;
    private static final double RETURN_YIELD = 1.0;
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
            productsService.clearStaleData();
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
        productsService.clearStaleData();
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
                    // Check for latest price
                    Optional<String> priceStringOptional = productsService.getPrice(job.getProductId());
                    if (!priceStringOptional.isPresent()) {
                        return;
                    }
                    double price = Double.parseDouble(priceStringOptional.get());

                    // Check for latest history
                    Optional<Deque<ProductPriceSegment>> priceSegment15Optional = productsService.getPriceSegment15(job.getProductId());
                    if (!priceSegment15Optional.isPresent()) {
                        log.error("Unable to get price data for {}", job.getProductId());
                        return;
                    }

                    Deque<ProductPriceSegment> priceSegment15 = priceSegment15Optional.get();
                    if (priceSegment15.size() < MAX_SEGMENTS) {
                        log.info("Not enough data gathered to begin trading for {}", job.getProductId());
                        return;
                    }

                    OptionalDouble midPriceOptional = priceSegment15.stream().mapToDouble(ProductPriceSegment::getOpenPrice).average();
                    if (!midPriceOptional.isPresent()) {
                        log.error("Unable to get mid market price for mid market buy phase. Currency: {}", job.getProductId());
                        return;
                    }
                    double midPrice = roundPrice(midPriceOptional.getAsDouble(), job.getPrecision());

                    if (job.isInit()) {
                        try {
                            handleMidMarketBuy(job, priceSegment15, midPrice, price);
                        } catch (Exception e) {
                            log.error("Failed to handle init period. Exception: {}", e.getMessage());
                        }
                    } else if (job.isSell()) {
                        try {
                            crest(job, priceSegment15, midPrice, price);
                        } catch (Exception e) {
                            log.error("Failed to handle crest period. Exception: {}", e.getMessage());
                        }
                    } else {
                        try {
                            trough(job, priceSegment15, midPrice, price);
                        } catch (Exception e) {
                            log.error("Failed to handle trough period. Exception: {}", e.getMessage());
                        }
                    }
                });
    }

    private void trough(JobSettings job, Deque<ProductPriceSegment> priceSegment15, double midPrice, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        List<ProductPriceSegment> minPriceSegment = new ArrayList<>(priceSegment15);
        OptionalDouble minPriceOptional = minPriceSegment.stream().mapToDouble(ProductPriceSegment::getLowPrice).min();
        if (!minPriceOptional.isPresent()) {
            log.error("Unable to get min price for trough period.");
            return;
        }
        double minPrice = roundPrice(minPriceOptional.getAsDouble(), job.getPrecision());

        double expectedBuy = job.getFunds() / midPrice;
        double expectedFees = expectedBuy * COINBASE_PERCENTAGE;
        double expectedSize = expectedBuy - expectedFees;
        double percentYield = expectedSize / job.getSize();
        double priceWantedAbsolute = job.getFunds() / ((job.getPercentageYieldThreshold() * job.getSize()) / (1 - COINBASE_PERCENTAGE));
        double priceWanted = roundPrice(priceWantedAbsolute, job.getPrecision());

        log.info("Checking trough jobId: {} - ProductId: {}, CurrentYield: {}, YieldWanted: {}, CurrentPrice: {}, PriceWanted: {}, MidPrice: {}, MinPrice: {}",
                job.getJobId(),
                job.getProductId(),
                percentYield,
                job.getPercentageYieldThreshold(),
                price,
                priceWanted,
                midPrice,
                minPrice);

        if (priceWanted < minPrice) {
            handleMidMarketBuy(job, priceSegment15, midPrice, price);
        } else {
            handlePercentageYieldThreshold(job, percentYield, false);
        }
    }

    private void crest(JobSettings job, Deque<ProductPriceSegment> priceSegment15, double midPrice, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        List<ProductPriceSegment> maxPriceSegment = new ArrayList<>(priceSegment15);
        OptionalDouble maxPriceOptional = maxPriceSegment.stream().mapToDouble(ProductPriceSegment::getHighPrice).max();
        if (!maxPriceOptional.isPresent()) {
            log.error("Unable to get max price for crest period.");
            return;
        }
        double maxPrice = roundPrice(maxPriceOptional.getAsDouble(), job.getPrecision());

        double expectedSale = price * job.getSize();
        double expectedFees = expectedSale * COINBASE_PERCENTAGE;
        double expectedFunds = expectedSale - expectedFees;
        double percentYield = expectedFunds / job.getFunds();
        double priceWantedAbsolute = (job.getPercentageYieldThreshold() * job.getFunds()) / (job.getSize() - (job.getSize() * COINBASE_PERCENTAGE));
        double priceWanted = roundPrice(priceWantedAbsolute, job.getPrecision());
        double absoluteValue = job.getFunds() / job.getSize();
        double value = roundPrice(absoluteValue, job.getPrecision());

        log.info("Checking crest jobId: {} - ProductId: {}, CurrentYield: {}, YieldWanted: {}, CurrentPrice: {}, PriceWanted: {}, MidPrice: {}, MaxPrice: {}, Value: {}",
                job.getJobId(),
                job.getProductId(),
                percentYield,
                job.getPercentageYieldThreshold(),
                price,
                priceWanted,
                midPrice,
                maxPrice,
                value);

        if (maxPrice < value) {
            log.warn("{} - Max price of {} fell below current value of {}", job.getProductId(), maxPrice, value);
            if (job.isProtectUsd() && (percentYield < job.getMaximumLoses())) {
                log.warn("Maximum losses reached for this trade. CurrentYield: {}, MaximumLoses: {}", percentYield, job.getMaximumLoses());
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
                    job.setActive(false);
                    autoAppDao.startOrUpdateJob(job);
                    bustCache = true;
                } else {
                    job.setOrderId(UUID.randomUUID().toString());
                    autoAppDao.startOrUpdateJob(job);
                    bustCache = true;
                }
            }
        } else {
            handlePercentageYieldThreshold(job, percentYield, true);
        }
    }

    private void handleMidMarketBuy(JobSettings job, Deque<ProductPriceSegment> priceSegment15, double midPrice, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        log.info("Checking mid market buy jobId: {} - ProductId: {}, CurrentPrice: {}, MidPrice: {}",
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
            job.setMinValue(midPrice);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }

        if (job.isCrossedLowThreshold() && price < job.getMinValue()) {
            job.setMinValue(midPrice);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }
    }

    private void handlePercentageYieldThreshold(JobSettings job, double percentYield, boolean sell)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        if ((job.isCrossedPercentageYieldThreshold() && percentYield < job.getMaxYieldValue())
                || job.isTradeNow()) {
            if (percentYield < RETURN_YIELD) {
                log.warn("WARNING!! Selling product {} at yield loss of {}", job.getProductId(), percentYield);
            }

            // Send trade request
            CoinbaseOrderRequest request = new CoinbaseOrderRequest();
            request.setOrderId(job.getOrderId());
            request.setProductId(job.getProductId());

            if (sell) {
                request.setSide(CoinbaseOrderRequest.SELL);
                request.setSize(String.valueOf(job.getSize()));
            } else {
                request.setSide(CoinbaseOrderRequest.BUY);
                request.setFunds(String.valueOf(job.getFunds()));
            }

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
