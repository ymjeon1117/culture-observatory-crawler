package scheduler.kcisa.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import scheduler.kcisa.model.collection.SchedulerLog;
import scheduler.kcisa.service.SchedulerLogService;

import java.util.List;

@RestController
@RequestMapping("/api/scheduler/logs")  // 로그 관련 API 경로
public class SchedulerLogController {

    private final SchedulerLogService schedulerLogService;

    @Autowired
    public SchedulerLogController(SchedulerLogService schedulerLogService) {
        this.schedulerLogService = schedulerLogService;
    }

    // 로그 생성 (POST)
    @PostMapping
    public SchedulerLog createLog(@RequestBody SchedulerLog log) {
        return schedulerLogService.create(log);
    }

    // 모든 로그 조회 (GET)
    @GetMapping
    public List<SchedulerLog> getAllLogs() {
        return schedulerLogService.findAll();
    }

    // 최근 50개 로그 조회 (GET)
    @GetMapping("/latest-50")
    public List<SchedulerLog> getLatest50Logs() {
        return schedulerLogService.findTop50ByOrderByCreatedAtDesc();
    }

    // 특정 그룹과 이름으로 로그 조회 (GET)
    @GetMapping("/{group}/{name}")
    public SchedulerLog getLogByGroupAndName(@PathVariable String group, @PathVariable String name) {
        return schedulerLogService.findByGroupAndName(group, name);
    }

    // 최근 1000개 로그 조회 (GET)
    @GetMapping("/latest-1000")
    public List<SchedulerLog> getLatest1000Logs() {
        return schedulerLogService.findTop1000ByOrderByCreatedAtDesc();
    }
}
