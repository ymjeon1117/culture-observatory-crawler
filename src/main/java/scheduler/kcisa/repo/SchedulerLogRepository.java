package scheduler.kcisa.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import scheduler.kcisa.model.collection.SchedulerLog;

import java.util.List;

@Repository
public interface SchedulerLogRepository extends JpaRepository<SchedulerLog, Long> {
    List<SchedulerLog> findTop50ByOrderByCreatedAtDesc();

    List<SchedulerLog> findTop150ByOrderByIdDesc();

    SchedulerLog findByGroupAndName(String group, String name);

    List<SchedulerLog> findTop1000ByOrderByCreatedAtDesc();
}
