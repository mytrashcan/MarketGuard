package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.Severity;
import java.time.Instant;
import org.springframework.data.repository.Repository;

/** 화면 요약용 신호 개수. 행을 불러오지 않고 데이터베이스에서 센다. */
public interface AnomalyCountRepository extends Repository<AnomalyRecord, Long> {

    long countByDetectedAtGreaterThanEqual(Instant since);

    long countByDetectedAtGreaterThanEqualAndSeverity(Instant since, Severity severity);
}
