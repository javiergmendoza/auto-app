package com.javi.autoapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.javi.autoapp.client.CoinbaseTraderClient;
import com.javi.autoapp.client.model.CoinbaseOrderRequest;
import com.javi.autoapp.client.model.CoinbaseOrderResponse;
import com.javi.autoapp.client.model.CoinbaseWebSocketSubscribe;
import com.javi.autoapp.client.model.CoinbaseTicker;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobSettings;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.graphql.type.Status;
import com.javi.autoapp.util.CacheHelper;
import com.javi.autoapp.util.SignatureTool;
import io.netty.handler.codec.http.HttpMethod;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AutoTradingService implements Runnable, MessageHandler.Whole<CoinbaseTicker> {
    private static final double BASE_PRECISION = 0.0; // Round to nearest dollar
    private static final double COINBASE_PERCENTAGE = 0.0149;
    private static final double WARNING_DELTA = 1.1;
    private static final double MINIMUM_FINAL_PERCENT_YIELD = 0.75;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CacheManager cacheManager;
    private final Session userSession;
    private final CoinbaseTraderClient coinbaseTraderClient;
    private final AutoAppDao autoAppDao;
    private final Set<String> activeFeeds;
    private final Map<String, String> pricesMap;

    private ScheduledFuture<?> scheduledFuture;
    private boolean bustCache = true;

    public AutoTradingService(
            CacheManager cacheManager,
            Session session,
            CoinbaseTraderClient coinbaseTraderClient,
            AutoAppDao autoAppDao
    ) {
        this.cacheManager = cacheManager;
        this.coinbaseTraderClient = coinbaseTraderClient;
        this.autoAppDao = autoAppDao;
        this.activeFeeds = Collections.synchronizedSet(new HashSet<>());
        activeFeeds.add(Currency.BTC.getLabel());
        this.pricesMap = new ConcurrentHashMap<>();

        userSession = session;
        userSession.addMessageHandler(this);
    }

    public Set<String> getActiveFeeds() {
        return activeFeeds;
    }

    @PostConstruct
    public void postConstruct() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = executor.scheduleAtFixedRate(this, 0, 30, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(CoinbaseTicker message) {
        if (message.getProductId() == null || message.getPrice() == null) {
            return;
        }
        activeFeeds.add(message.getProductId());
        pricesMap.put(message.getProductId(), message.getPrice());
    }

    @SneakyThrows
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

        try {
            updateSubscribedCurrencies(jobs);
        } catch (JsonProcessingException error) {
            log.error("Failed to update subcription tickers. Error: {}", error.getMessage());
        }

        deactivateCompletedJobs(jobs);
        checkPendingOrders(jobs);
        autoTrade(jobs);
        cleanupCompletedJobs(jobs);
    }

    private void updateSubscribedCurrencies(List<JobSettings> jobs) throws JsonProcessingException {
        // Get all product IDs for jobs
        Set<String> productIds = jobs.stream()
                .filter(JobSettings::isActive)
                .map(JobSettings::getProductId)
                .collect(Collectors.toSet());

        if (productIds.equals(activeFeeds)) {
            return; // Nothing to update
        }

        // Only unsubscribe if there is anything to unsubscribe to
        if (!activeFeeds.isEmpty()) {
            log.info("Clearing currency ticker feeds.");
            CoinbaseWebSocketSubscribe unsubscribe = new CoinbaseWebSocketSubscribe();
            unsubscribe.setType(CoinbaseWebSocketSubscribe.UNSUBSCRIBE);
            unsubscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
            unsubscribe.setProductIds(new ArrayList<>(activeFeeds));
            userSession.getAsyncRemote().sendText(mapper.writeValueAsString(unsubscribe));
        }

        // Subscribe to required product IDs
        if (!productIds.isEmpty()) {
            log.info("Subscribing to the following currency feeds: {}", productIds.toString());
            CoinbaseWebSocketSubscribe subscribe = new CoinbaseWebSocketSubscribe();
            subscribe.setType(CoinbaseWebSocketSubscribe.SUBSCRIBE);
            subscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
            subscribe.setProductIds(new ArrayList<>(productIds));
            userSession.getAsyncRemote().sendText(mapper.writeValueAsString(subscribe));
        }

        // Clear to prevent storing stale data
        pricesMap.clear();
        activeFeeds.clear();
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
                        SignatureTool.getRequestPath(job.getJobId())
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
                job.setInit(false);
                job.setSell(true);
                job.setSize(size); // 3.0
                jobStatus.setCurrentFundsUsd(0.0);
                jobStatus.setSize(size);
            }
            else {
                job.setInit(false);
                job.setSell(false);
                job.setFunds(value); // 0.96384
                jobStatus.setCurrentFundsUsd(value);
                jobStatus.setSize(0.0);
            }

            job.setPending(false);
            job.setCrossedYieldThreshold(false);
            job.setMaxPercentageYield(0.0);
            autoAppDao.startOrUpdateJob(job);

            // Update job status
            if (!job.isActive() && !job.isSell()) {
                jobStatus.setStatus(Status.FINISHED);
            } else {
                jobStatus.setStatus(Status.RUNNING);
            }
            jobStatus.setCurrentValueUsd(value);
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
                    String priceString = pricesMap.get(job.getProductId());
                    if (priceString == null) {
                        return;
                    }
                    double absolutePrice = Double.parseDouble(priceString);
                    double price = roundPrice(absolutePrice, job.getPrecision());

                    if (job.isInit()) {
                        try {
                            init(job, price);
                        } catch (Exception e) {
                            log.error("Failed to initialize job. Exception: {}", e.getMessage());
                        }
                    }
                    else if (job.isSell()) {
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
        log.info("Checking initialized jobId: {} - ProductId: {}, Floor: {}, CurrentPrice: {}",
                job.getJobId(),
                job.getProductId(),
                job.getFloor(),
                price);
        if (job.isCrossedFloor() && price > job.getMinValue()) {
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
        if (price < job.getFloor()) {
            log.info("Job ID: {} - Crossed {} floor threshold.", job.getJobId(), job.getProductId());
            job.setCrossedFloor(true);
            job.setMinValue(price);
            autoAppDao.startOrUpdateJob(job);
            bustCache = true;
        }
    }

    private void crest(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        // starting sale = 91.2710425552 / 0.31 = 294.42271792 - (294.42271792 * 0.0149) = 290.035819423
        double expectedSale = price * job.getSize(); // 0.35 * 290.035819423 = 101.512536798
        double expectedFees = expectedSale * COINBASE_PERCENTAGE; // 101.512536798 * 0.0149 = 1.51253679829
        double expectedFunds = expectedSale - expectedFees; // 101.512536798 - 1.51253679829 = 100
        double percentYield = expectedFunds / job.getFunds(); // 100 / 91.2710425552 (bought at 0.31) = 1.09563775323

        if (job.isActive()) {
            // (1.1 * 91.2710425552) / (290.035819423 - (290.035819423 * 0.0149))
            double priceWantedAbsolute = (job.getPercentageYieldThreshold() * job.getFunds()) / (job.getSize() - (job.getSize() * COINBASE_PERCENTAGE));
            double priceWanted = roundPrice(priceWantedAbsolute, job.getPrecision());
            handlePercentYieldCheck(job, percentYield, true, false, price, priceWanted);
        } else {
            double finalPercentYield = expectedFunds / job.getStartingFundsUsd();
            double finalPriceWantedAbsolute = (MINIMUM_FINAL_PERCENT_YIELD * job.getStartingFundsUsd()) / job.getSize();
            double finalPriceWanted = roundPrice(finalPriceWantedAbsolute, job.getPrecision());
            handlePercentYieldCheck(job, finalPercentYield, true, true, price, finalPriceWanted);
        }
    }

    private void trough(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        double expectedBuy = job.getFunds() / price; // 100 / 0.30 = 333.333333333
        double expectedFees = expectedBuy * COINBASE_PERCENTAGE; // 333.333333333 * 0.0149 = 4.96666666666
        double expectedSize = expectedBuy - expectedFees; // 333.333333333 - 4.96666666666 = 328.366666666
        double percentYield = expectedSize / job.getSize(); // 328.366666666 / 290.035819423 (size that was initally sold) = 1.09563775323
        // 100 / ((1.1 * 290.035819423) / (1 - 0.0149))
        double priceWantedAbsolute = job.getFunds() / ((job.getPercentageYieldThreshold() * job.getSize()) / (1 - COINBASE_PERCENTAGE));
        double priceWanted = roundPrice(priceWantedAbsolute, job.getPrecision());

        handlePercentYieldCheck(job, percentYield, false, false, price, priceWanted);
    }

    private void handlePercentYieldCheck(
            JobSettings job,
            double percentYield,
            boolean isSell,
            boolean finalize,
            double price,
            double priceWanted) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        log.info("Checking running jobId: {} - ProductId: {}, PercentYield: {}, PercentYieldWant: {}, CurrentPrice: {}, PriceWanted: {}",
                job.getJobId(),
                job.getProductId(),
                percentYield,
                job.getPercentageYieldThreshold(),
                price,
                priceWanted);
        if ((job.isCrossedYieldThreshold() && percentYield < job.getMaxPercentageYield())
                || (finalize && percentYield > MINIMUM_FINAL_PERCENT_YIELD)) {
            if (!finalize && percentYield < 1.0) {
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
            if (isSell) {
                request.setSide(CoinbaseOrderRequest.SELL);
                request.setSize(String.valueOf(job.getSize()));
            } else {
                request.setSide(CoinbaseOrderRequest.BUY);
                request.setFunds(String.valueOf(job.getFunds()));
            }
            request.setJobId(job.getJobId());
            request.setProductId(job.getProductId());
            trade(request);
            return;
        } else if (finalize && percentYield < MINIMUM_FINAL_PERCENT_YIELD) {
            log.warn("WARNING!!! Job ID: {} has lost more than 25% of initial funds. Current yield: {}", job.getJobId(), percentYield);
        }

        // Set top value
        if (percentYield > job.getPercentageYieldThreshold()) {
            log.info("Job ID: {} - Crossed {} percentage yield threshold, with percent yield: {}",
                    job.getJobId(),
                    job.getProductId(),
                    percentYield);
            job.setCrossedYieldThreshold(true);
            job.setMaxPercentageYield(percentYield);
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
                SignatureTool.getRequestPath(),
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
        double precisionPlace = Math.pow(10, BASE_PRECISION - precision);
        return Math.round(absolutePrice * precisionPlace) / precisionPlace;
    }
}
