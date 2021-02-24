package com.javi.autoapp.graphql;

import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobStatus;
import com.javi.autoapp.graphql.type.Currency;
import com.javi.autoapp.service.ProductsService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobStatusQuery implements GraphQLQueryResolver {
    private final ProductsService productsService;
    private final AutoAppDao autoAppDao;

    public double getTotalGainsLosses() {
        return autoAppDao.getJobStatuses().stream().mapToDouble(JobStatus::getGainsLosses).sum();
    }

    public JobStatus getJob(String id) {
        return autoAppDao.getJobStatus(id);
    }

    public List<JobStatus> getJobs() {
        return autoAppDao.getJobStatuses();
    }

    public List<Currency> getActiveFeeds() {
        return productsService.getActiveFeeds().stream()
                .map(Currency::getByLabel)
                .collect(Collectors.toList());
    }
}