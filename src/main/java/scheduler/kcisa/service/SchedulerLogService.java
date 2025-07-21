package scheduler.kcisa.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import scheduler.kcisa.model.collection.SchedulerLog;
import scheduler.kcisa.repo.SchedulerLogRepository;

import java.util.List;

@Service
public class SchedulerLogService implements LogService<SchedulerLog> {
    private final SchedulerLogRepository schedulerLogRepository;

    @Autowired
    public SchedulerLogService(SchedulerLogRepository schedulerLogRepository) {
        this.schedulerLogRepository = schedulerLogRepository;
    }

    public SchedulerLog create(SchedulerLog log) {
        return schedulerLogRepository.save(log);
    }

    public List<SchedulerLog> findAll() {
        return schedulerLogRepository.findAll();
    }

    public List<SchedulerLog> findTop50ByOrderByCreatedAtDesc() {
        return schedulerLogRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public List<SchedulerLog> findTop150ByOrderByIdDesc() {
        return schedulerLogRepository.findTop150ByOrderByIdDesc();
    }

    public SchedulerLog findByGroupAndName(String group, String name) {
        return schedulerLogRepository.findByGroupAndName(group, name);
    }

    public List<SchedulerLog> findTop1000ByOrderByCreatedAtDesc() {
        return schedulerLogRepository.findTop1000ByOrderByCreatedAtDesc();
    }
}
