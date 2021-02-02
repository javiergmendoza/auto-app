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
            CoinbaseWebSocketSubscribe unsubscribe = new CoinbaseWebSocketSubscribe();
            unsubscribe.setType(CoinbaseWebSocketSubscribe.UNSUBSCRIBE);
            unsubscribe.setChannels(CoinbaseWebSocketSubscribe.TICKER_CHANNEL);
            unsubscribe.setProductIds(new ArrayList<>(activeFeeds));
            userSession.getAsyncRemote().sendText(mapper.writeValueAsString(unsubscribe));
        }

        // Subscribe to required product IDs
        if (!productIds.isEmpty()) {
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
                    double price = Double.parseDouble(priceString);

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
        double expectedSale = price * job.getSize();
        double expectedFees = expectedSale * COINBASE_PERCENTAGE;
        double expectedFunds = expectedSale - expectedFees;
        double percentYield = expectedFunds / job.getFunds();

        if (job.isActive()) {
            handlePercentYieldCheck(job, percentYield, true, false);
        } else {
            double finalPercentYield = expectedFunds / job.getStartingFundsUsd();
            handlePercentYieldCheck(job, finalPercentYield, true, true);
        }
    }

    private void trough(JobSettings job, double price)
            throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        double expectedBuy = job.getFunds() / price;
        double expectedFees = expectedBuy * COINBASE_PERCENTAGE;
        double expectedSize = expectedBuy - expectedFees;
        double percentYield = expectedSize / job.getSize();

        handlePercentYieldCheck(job, percentYield, false, false);
    }

    private void handlePercentYieldCheck(
            JobSettings job,
            double percentYield,
            boolean isSell,
            boolean finalize) throws NoSuchAlgorithmException, InvalidKeyException, JsonProcessingException {
        log.info("Checking running jobId: {} - ProductId: {}, PercentYield: {}",
                job.getJobId(),
                job.getProductId(),
                percentYield);
        if (job.isCrossedYieldThreshold() && percentYield < job.getMaxPercentageYield()) {
            if (finalize && percentYield < MINIMUM_FINAL_PERCENT_YIELD) {
                log.error("ERROR!!! This final sale would end up with greater losses than minimum loss threshold {}. Net total loss percentage would be: {}",
                        MINIMUM_FINAL_PERCENT_YIELD, percentYield);
                return;
            }
            else if (percentYield < 1.0) {
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
}
