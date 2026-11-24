# 검증 실행 체크리스트 (이 Mac에서 20거래일)

[분석 계획](analysis-plan.md)대로 20거래일 이상 수집하기 위한 순서입니다. **수집 중에는 코드·설정·이미지를 바꾸지 않는 것**이 가장 중요합니다.

## 1. 한 번만 준비

### 토스 API
- [ ] `.env`의 `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET`이 채워져 있다.
- [ ] 토스 개발자 콘솔에 등록한 **outbound IP가 이 Mac의 지금 공인 IP와 같다.** 공인 IP는 `curl -s https://api.ipify.org`로 확인합니다. 집 인터넷은 공유기 재시작 등으로 IP가 바뀔 수 있으니, 수집 중 IP가 바뀌면 그날을 수집 중단으로 기록합니다.

### KRX 파일 (`data/`, 커밋되지 않음)
[KRX 정보데이터시스템](https://data.krx.co.kr) → 기본 통계 → 주식 → 종목정보. 메뉴 번호는 사이트 개편으로 바뀔 수 있습니다.

- [ ] **[12005] 전종목 기본정보**: 시장구분 "전체" → CSV 다운로드 → `data/symbols.csv`로 저장
- [ ] **[12025] 업종분류 현황**: KOSPI와 KOSDAQ을 각각 CSV로 받아 하나로 합칩니다. 머리글 줄이 두 번 들어가도 괜찮습니다.
  ```bash
  cat ~/Downloads/<KOSPI 파일>.csv ~/Downloads/<KOSDAQ 파일>.csv > data/sectors.csv
  ```
- 인코딩(CP949)은 그대로 둡니다. 2024년 이후 상장 일부 종목의 영문·숫자 혼합 단축코드는 스캔 대상에서 빠집니다(6자리 숫자 코드만 읽음).

### `.env`
- [ ] 아래 줄이 있다(`.env.example` 참고).
  ```bash
  COLLECTOR_ENABLED=true
  MARKETGUARD_DATA_DIR=./data
  SCAN_SYMBOLS_FILE=/data/symbols.csv
  OUTCOME_SECTORS_FILE=/data/sectors.csv
  SNAPSHOT_RETENTION=9h   # 장 마감 뒤 점검이 그날 09:00부터의 수집 공백을 볼 수 있게
  ```
  사전 점검은 `docker compose config`로 Compose가 실제로 앱에 넘길 값을 확인합니다. 셸에서 `export`한 변수도 반영되니, 수집 기간에는 이 변수들을 셸에 따로 export하지 않습니다.

### Mac
- [ ] **OrbStack**을 켜고, 설정에서 "로그인 시 시작"을 켭니다. 컨테이너는 `restart: unless-stopped`라 Mac이 재부팅돼도 다시 올라옵니다.
- [ ] **전원에 연결**해 둡니다.
- [ ] **잠자기 방지**: 이 Mac은 1분 뒤 잠들도록 설정돼 있고, 지금은 Amphetamine 같은 앱이 막고 있습니다. 거래일 08:50–16:40에 반드시 깨어 있도록 Amphetamine에 평일 일정 트리거를 걸거나, 수집 기간 동안 전원 연결 시 잠자기를 끕니다(`sudo pmset -c sleep 0`, 끝나면 원래 값으로). 노트북 덮개를 닫을 거라면 Amphetamine의 "디스플레이를 닫아도 깨어 있기"를 켭니다.
- [ ] macOS 자동 업데이트 재시작이 거래 시간에 일어나지 않도록 해 둡니다.

## 2. 시작 전 (첫 거래일 전날)

- [ ] `main`을 최신으로 받고 깨끗한 상태에서 시작합니다.
  ```bash
  git checkout main && git pull
  ```
- [ ] 사전 점검이 `READY`로 끝난다.
  ```bash
  python3 scripts/run_preflight.py
  ```
- [ ] 앱을 올립니다. **수집 기간 중 다시 빌드하지 않습니다.**
  ```bash
  docker compose up --build --detach --wait
  ```
- [ ] 로그에 유니버스가 제대로 잡혔는지 봅니다("이상거래 스캔 유니버스 N종목 로드됨", "외부 종목 파일 사용").
  ```bash
  docker compose logs app | grep -E "유니버스|종목 파일|업종"
  ```
- [ ] 탐지 설정을 저장합니다(`docs/runs/`는 커밋합니다).
  ```bash
  mkdir -p docs/runs
  # 관리자 아이디만 주면 curl이 비밀번호를 물어봅니다(셸 기록에 남지 않음)
  curl -su <관리자 아이디> http://127.0.0.1:5050/api/settings/detection \
    > docs/runs/<시작일>-detection-settings.json
  ```
- [ ] [분석 계획의 실행 기록](analysis-plan.md#실행-기록)을 채웁니다. 시작일, 예정 종료일(시작일부터 KRX 휴장일을 뺀 20번째 거래일), 커밋 SHA, 탐지 설정 파일, 종목 수와 업종 매핑 비율(사전 점검 출력)을 적습니다. **첫 거래일 개장 전에** 커밋하고 푸시합니다. 이 커밋 시각이 "결과를 보기 전에 정했다"는 증거입니다.

## 3. 매일 (장 마감 뒤 몇 분)

- [ ] **15:35–18:00 사이에** 상태를 확인합니다. 18:00이 지나면 그날 아침 스냅샷이 지워져 오전 공백을 확인할 수 없습니다.
  ```bash
  ./scripts/run_status.sh <실행 기록의 스캔 유니버스 종목 수>
  ```
  - **정상 수집일**: 첫 표에서 `buckets_so_far`가 78이고 `with_data`도 78(`empty_buckets`가 `-`)이며, 실행 기록에 적은 종목 수의 90% 이상이 수집된 구간(`near_full_universe`)이 74개 이상(95%)입니다. 그날 가장 많이 모인 구간이 아니라 **시작 전에 기록한 종목 수**와 비교하므로, 토스가 일부 배치를 하루 종일 빠뜨린 날도 걸러집니다.
  - 70분 지난 신호는 거의 모두 `all_offsets_recorded`여야 하고, 14:57 이전 신호(`before_1457`)는 대부분 `usable_30m`이어야 합니다. `none_recorded`가 늘면 기록기가 멈춘 것입니다.
- [ ] 정상 수집일이 아니면 실행 기록의 "수집 중단"에 날짜와 이유를 적습니다. **그날은 아침에 모은 신호까지 통째로 분석에서 뺍니다**(`--exclude-day`). 20거래일에도 세지 않고, 하루 더 수집합니다.

**하지 말 것**
- 임계값·`outcome.*`·스캔 주기·watch-list 변경, `git pull` 뒤 다시 빌드
- 통계 화면이나 CSV를 보고 기간을 늘리거나 줄이는 결정
- 수집 중인 데이터베이스 볼륨 삭제(`docker compose down -v`)

## 4. 끝난 뒤

- [ ] 마지막 거래일 다음 날 [운영 문서 6단계](operations.md#signal-price-paths-and-full-universe-runs)대로 CSV를 받고 분석 스크립트를 돌립니다. `--sessions`에는 정상 수집일 수를, 수집 중단 날짜마다 `--exclude-day`를 넣습니다.
- [ ] CSV의 SHA-256(`shasum -a 256 price-paths.csv`)과 결과를 `docs/results/`에 싣고, README의 검증 현황 표를 고칩니다. 결과가 나빠도 그대로 싣습니다.
- [ ] 데이터베이스를 백업합니다([운영 문서](operations.md#backup-and-restore)).
