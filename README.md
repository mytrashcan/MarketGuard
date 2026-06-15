# MarketGuard — 시장감시 · 이상거래 탐지 시스템

토스증권 Open API로 시세를 수집해 **룰 기반으로 이상거래를 탐지**하고 실시간으로 알리는 미니 시장감시 시스템입니다.
한국거래소 시장감시본부 / 금융감독원 투자자보호 업무를 작게 재현한 **금융공기업 IT개발직 포트폴리오** 프로젝트입니다.

> ⚠️ 매매(주문) 기능은 사용하지 않습니다. **read-only 수집·분석 중심**으로, 실제 자금이 움직이지 않습니다.

## 아키텍처

```
토스증권 Open API
        │  OAuth2 · REST 폴링
        ▼
① 수집 계층 (collector)        API 클라이언트 · 토큰 관리(자동 갱신) · 폴링 스케줄러
        ▼
② 탐지 엔진 (detection)        DetectionRule 전략 패턴 — 거래량 급증 / (예정) 상하한가·호가불균형·투자경고
        ▼
③ 알림 · 조회 (dashboard)      조회 API · (예정) WebSocket 실시간 푸시
        ▼
   관제 대시보드               리스크 스코어 · 알림 이력

공통 관심사: 데이터 저장(JPA) · 감사 로그 · (예정) 회복탄력성(Resilience4j) · 보안(토큰/시크릿)
```

패키지 구조:

```
com.marketguard
├── config        설정·프로퍼티 (RestClient, ConfigurationProperties)
├── collector     외부 API 연동 (auth 토큰 관리 / client 시세 조회 / scheduler 폴링)
├── detection     탐지 도메인 (model 값객체 / rule 전략 / engine 평가)
├── domain        영속 엔티티·리포지토리 (marketdata / anomaly)
└── dashboard     조회 API
```

## 기술 스택

- Java 17 (Gradle toolchain, foojay 자동 프로비저닝) · Spring Boot 4.1
- Spring Web(RestClient) · Spring Data JPA · Validation · Actuator
- H2 (로컬) / PostgreSQL (운영 프로파일) · Lombok
- JUnit 5 · AssertJ

## 실행 방법

```bash
# 1) 그대로 실행 (API 키 없이도 기동 — 수집은 비활성)
./gradlew bootRun

# 2) 실제 수집 활성화 (토스 API 키 발급 후)
export TOSS_CLIENT_ID=...        # Windows PowerShell: $env:TOSS_CLIENT_ID="..."
export TOSS_CLIENT_SECRET=...
export TOSS_ACCOUNT_ID=...
export COLLECTOR_ENABLED=true
./gradlew bootRun
```

- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:marketguard`)
- 조회 API:
  - `GET /api/anomalies` — 최근 탐지된 이상거래
  - `GET /api/stocks/{code}/snapshots` — 특정 종목 최근 시세
- 헬스체크: `GET /actuator/health`

> 토스 API의 실제 엔드포인트 경로와 응답 스키마는 공식 OpenAPI 스펙
> (`https://openapi.tossinvest.com/openapi-docs/latest/openapi.json`)에 맞춰
> `TossMarketDataClient` / `PriceResponse` / `TossTokenManager`를 교체하세요.

## 탐지 룰

| 룰 | 설명 | 상태 |
|---|---|---|
| 거래량 급증 | 현재 거래량이 직전 평균의 N배 이상 | ✅ 구현 |
| 가격제한폭 도달 | 상·하한가 도달 | ⏳ 예정 |
| 호가 불균형 | 매수/매도 잔량 비율 임계치 | ⏳ 예정 |
| 투자경고 종목 | 종목 경고 알림 연동 | ⏳ 예정 |

새 룰은 `DetectionRule` 인터페이스만 구현해 빈으로 등록하면 `RuleEngine`이 자동 인식합니다(OCP).

## 로드맵

- [x] **Phase 1** — API 연동 골격 + 토큰 관리 + 시세 수집·저장 + 룰 엔진 + 예시 룰 1개
- [ ] **Phase 2** — 룰 추가(상하한가·호가불균형·투자경고)
- [ ] **Phase 3** — 대시보드 화면 + WebSocket 실시간 알림
- [ ] **Phase 4** — Resilience4j(재시도·서킷브레이커) + 감사 로그(AOP) + 통합 테스트(Testcontainers)
- [ ] **Phase 5** — Flyway + Docker Compose + 문서화
