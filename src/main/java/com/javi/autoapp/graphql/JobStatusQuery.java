package com.javi.autoapp.graphql;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import com.javi.autoapp.ddb.AutoAppDao;
import com.javi.autoapp.ddb.model.JobStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobStatusQuery implements GraphQLQueryResolver {
    private final AutoAppDao autoAppDao;

    public JobStatus getJob(String id) {
        return autoAppDao.getJobStatus(id);
    }

    public List<JobStatus> getJobs() {
        return autoAppDao.getJobStatuses();
    }
}