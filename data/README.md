# data/

Compose가 이 폴더를 컨테이너의 `/data`에 읽기 전용으로 연결합니다. 여기 둔 파일은 커밋되지 않습니다.

| 파일 | 설정 | 내용 |
|---|---|---|
| `symbols.csv` | `SCAN_SYMBOLS_FILE=/data/symbols.csv` | KRX 정보데이터시스템 전종목 기본정보 CSV(또는 6자리 종목코드 목록) |
| `sectors.csv` | `OUTCOME_SECTORS_FILE=/data/sectors.csv` | KRX 업종분류 현황 CSV(또는 "종목코드,업종" 두 열) |

자세한 순서는 [operations.md](../docs/operations.md#signal-price-paths-and-full-universe-runs)를 참고하세요.
