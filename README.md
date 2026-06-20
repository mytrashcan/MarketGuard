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
② 탐지 엔진 (detection)        DetectionRule 전략 패턴 — 가격 급변동·가격제한폭·호가불균형·거래량
        ▼
③ 알림 · 조회 (alert/dashboard)  WebSocket 실시간 푸시 · 조회 API · 관제 대시보드 화면
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
├── alert         이상 신호 전파 (AnomalyNotifier → WebSocket)
├── domain        영속 엔티티·리포지토리 (marketdata / anomaly)
└── dashboard     조회 API
```

## 기술 스택

- Java 17 (Gradle toolchain, foojay 자동 프로비저닝) · Spring Boot 4.1
- Spring Web(RestClient) · Spring Data JPA · Validation · Actuator · WebSocket(STOMP)
- H2 (로컬) / PostgreSQL (운영 프로파일) · Lombok
- JUnit 5 · AssertJ

## 실행 방법

```bash
# 1) 그대로 실행 (API 키 없이도 기동 — 수집은 비활성)
./gradlew bootRun

# 2) 실제 수집 활성화 (토스 client id/secret 발급 후) — PowerShell 기준
$env:TOSS_CLIENT_ID="..."; $env:TOSS_CLIENT_SECRET="..."
$env:COLLECTOR_ENABLED="true"
./gradlew bootRun
# account-id는 시세 조회엔 불필요(계좌/주문 API 전용)
```

- **관제 대시보드(실시간): `http://localhost:5050/`** — WebSocket으로 탐지 즉시 표시 (포트는 `SERVER_PORT`로 변경 가능)
- 같은 네트워크/외부에서 접속: `http://<내-IP>:5050/` (공유기 포트포워딩 + Windows 방화벽에서 5050 인바운드 허용 필요)
- H2 콘솔: `http://localhost:5050/h2-console` (JDBC URL `jdbc:h2:mem:marketguard`) — 외부 공개 시에는 끄는 것을 권장
- 조회 API:
  - `GET /api/anomalies` — 최근 탐지된 이상거래
  - `GET /api/stocks/{code}/snapshots` — 특정 종목 최근 시세
- 헬스체크: `GET /actuator/health`

### 전 종목 감시 (시장 전반)
토스 Open API에는 "전 종목 목록" 엔드포인트가 없어, 스캔 대상 종목코드를 직접 공급해야 합니다.
KRX 정보데이터시스템(data.krx.co.kr)에서 **전종목 CSV**를 받아 경로만 지정하면 그 안의 6자리 코드 전부를 스캔합니다(파일 인코딩·CSV 형식 무관, 코드만 추출).
```powershell
$env:SCAN_SYMBOLS_FILE="C:\krx\krx_codes.csv"   # 비우면 classpath:symbols.txt(시연 시드) 사용
```
스캔은 가격 기반 룰(가격 급변동)로 전 종목을 넓게 보고, 호가·캔들 등 무거운 룰은 `watch-list`(포커스) 종목에만 적용하는 2단계 구조입니다. 유니버스가 크면 `collector.poll-interval-ms`를 늘리세요.

> 연동된 엔드포인트: 토큰 `POST /oauth2/token`, 시세 `GET /api/v1/prices`, 가격제한폭 `GET /api/v1/price-limits`,
> 호가 `GET /api/v1/orderbook`, 캔들 `GET /api/v1/candles`. 투자경고(종목정보) 등은 해당 스펙에 맞춰 확장하세요
> (스펙: `https://openapi.tossinvest.com/openapi-docs/latest/openapi.json`).

## 탐지 룰

| 룰 | 설명 | 상태 |
|---|---|---|
| 단기 가격 급변동 | 현재가가 직전 평균 대비 ±N% 이상 변동 | ✅ 구현 |
| 가격제한폭 도달 | 상·하한가 도달/근접 (`/api/v1/price-limits`) | ✅ 구현 |
| 호가 불균형 | 매수/매도 총잔량 비율 임계치 (`/api/v1/orderbook`) | ✅ 구현 |
| 거래량 급증 | 최근 봉 거래량이 직전 평균의 N배 (`/api/v1/candles`) | ✅ 구현 |
| 투자경고 종목 | 종목 경고 알림 연동 (종목정보 API) | ⏳ 예정 |

새 룰은 `DetectionRule` 인터페이스만 구현해 빈으로 등록하면 `RuleEngine`이 자동 인식합니다(OCP).

## 로드맵

- [x] **Phase 1** — API 연동 골격 + 토큰 관리 + 시세 수집·저장 + 룰 엔진 + 예시 룰 1개
- [x] **Phase 2** — 룰 추가(가격제한폭·호가불균형·거래량) · 투자경고는 종목정보 API 연동으로 추후
- [x] **Phase 3** — 관제 대시보드 화면 + WebSocket(STOMP) 실시간 알림
- [ ] **Phase 4** — Resilience4j(재시도·서킷브레이커) + 감사 로그(AOP) + 통합 테스트(Testcontainers)
- [ ] **Phase 5** — Flyway + Docker Compose + 문서화
