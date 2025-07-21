package scheduler.kcisa.model.collection;

import lombok.*;
import scheduler.kcisa.model.SchedulerStatus;

import javax.persistence.*;
import java.util.Date;

@Getter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "COLCT_SCHD_LOG")
public class SchedulerLog {
    @Id
    @Column(name = "COLCT_SCHD_SEQ_NO")
    @GeneratedValue(strategy = javax.persistence.GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, name = "COLCT_SCHD_GROUP_NM", nullable = false)
    private String group;

    @Column(length = 200, name = "COLCT_SCHD_JOB_NM", nullable = false)
    private String name;

    @Column(length = 200, name = "COLCT_SCHD_TABLE_NM", nullable = false)
    private String table_name;

    @Enumerated(EnumType.STRING)
    @Column(length = 200, name = "COLCT_STATE_CD")
    private SchedulerStatus status;

    @Column(name = "COLCT_CO", columnDefinition = "DECIMAL(28,5)")
    private Integer count;

    @Column(name = "CRTN_CO", columnDefinition = "DECIMAL(28,5)")
    private Integer crt_count;

    @Column(name = "UPDT_CO", columnDefinition = "DECIMAL(28,5)")
    private Integer updt_count;

    @Column(name = "ERROR_MSG", length = 4000)
    private String message;

    @Column(name = "COLCT_DT", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();

    public SchedulerLog(String group, String name, String table_name, SchedulerStatus status) {
        this.group = group;
        this.name = name;
        this.table_name = table_name;
        this.status = status;
    }

    public SchedulerLog(String group, String name, String table_name, SchedulerStatus status, String message) {
        this.group = group;
        this.name = name;
        this.table_name = table_name;
        this.status = status;
        this.message = message;
    }

    public SchedulerLog(String group, String name, String table_name, SchedulerStatus status, Integer count,
            Integer crt_count, Integer updt_count) {
        this.group = group;
        this.name = name;
        this.table_name = table_name;
        this.status = status;
        this.count = count;
        this.crt_count = crt_count;
        this.updt_count = updt_count;
    }

    public SchedulerLog(String group, String name, String table_name, SchedulerStatus status, Integer count) {
        this.group = group;
        this.name = name;
        this.table_name = table_name;
        this.status = status;
        this.count = count;
    }
}
