# OCI(Oracle Cloud)에서 검증 실행하기

20거래일 동안 사람이 지켜보지 않아도 돌아가도록, Oracle Cloud의 ARM 인스턴스 한 대에서 Compose로 실행하는 방법입니다. 데이터 수집 절차는 [실행 체크리스트](run-checklist.md)를 그대로 따르고, 이 문서는 서버를 준비하는 부분만 다룹니다.

**왜 서버인가.** 토스는 등록한 IP에서 온 요청만 받고(아니면 토큰 발급이 HTTP 403), 수집기는 매 거래일 08:50–16:40에 깨어 있어야 합니다. 노트북은 자리를 옮기면 IP가 바뀌고 잠자기·업데이트 재시작이 끼어들기 쉽습니다. OCI 인스턴스에 예약 공인 IP를 붙이면 4주 내내 주소가 같습니다.

## 1. 계정과 인스턴스 (콘솔에서 직접)

- [ ] **유료 계정(Pay As You Go)으로 전환을 권합니다.** Always Free 인스턴스는 일주일 동안 CPU·네트워크·메모리 사용률이 낮으면 Oracle이 회수할 수 있는데, 이 앱은 사용률이 낮아 대상이 되기 쉽습니다. 유료 계정이어도 무료 한도 안의 자원은 요금이 나오지 않습니다. 전환 전 콘솔의 요금·한도 안내를 확인하세요.
- [ ] **인스턴스 만들기**: Compute → Instances → Create
  - 리전: 서울(ap-seoul-1) 또는 춘천(ap-chuncheon-1)
  - 이미지: Canonical Ubuntu 24.04 (aarch64)
  - Shape: `VM.Standard.A1.Flex`, **2 OCPU / 12 GB**면 충분합니다(무료 한도 4 OCPU / 24 GB 안).
  - 부트 볼륨: 50 GB
  - SSH 키: 내 Mac의 공개 키를 넣습니다.
  - "Out of capacity"가 나오면 다른 가용 도메인이나 리전으로 다시 시도합니다.
- [ ] **예약 공인 IP**: Networking → IP management → Reserved public IPs에서 하나 만들고, 인스턴스 VNIC의 기본 사설 IP에 연결합니다(임시 공인 IP는 해제). 인스턴스를 다시 만들어도 이 주소는 유지됩니다.
- [ ] **토스 개발자 콘솔에 이 예약 IP를 등록**합니다. 등록 직후에도 403이면 반영 시간, 앱 키의 시세 조회 권한, 키 만료 여부를 확인합니다.
- [ ] **포트는 SSH(22)만** 내 IP에서 열어 둡니다. 5050은 열지 않습니다. 대시보드는 아래처럼 SSH 터널로 봅니다(Compose도 앱 포트를 서버의 127.0.0.1에만 엽니다).

## 2. 서버 준비 (SSH로 접속해서)

```bash
ssh ubuntu@<예약 IP>
```

```bash
sudo timedatectl set-timezone Asia/Seoul
```

Docker 공식 저장소에서 설치합니다([Docker 문서](https://docs.docker.com/engine/install/ubuntu/)와 같은 절차).

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

다시 접속해 그룹을 적용하고, 레포를 받습니다.

```bash
exit
```

```bash
ssh ubuntu@<예약 IP>
git clone https://github.com/mytrashcan/MarketGuard.git && cd MarketGuard
```

수집 기간 중 서버가 스스로 재부팅하지 않게 합니다(보안 업데이트 설치는 그대로, 재부팅만 끔).

```bash
echo 'Unattended-Upgrade::Automatic-Reboot "false";' | sudo tee /etc/apt/apt.conf.d/52marketguard-no-reboot
```

## 3. 설정과 데이터 옮기기 (내 Mac에서)

`.env`와 KRX 파일은 커밋되지 않으므로 직접 복사합니다. 비밀값이 들어 있으니 SSH(scp)로만 옮깁니다.

```bash
scp .env ubuntu@<예약 IP>:~/MarketGuard/.env
```

```bash
scp data/symbols.csv data/sectors.csv ubuntu@<예약 IP>:~/MarketGuard/data/
```

서버에서 권한을 좁힙니다.

```bash
chmod 600 ~/MarketGuard/.env
```

## 4. 점검과 기동 (서버에서)

```bash
cd ~/MarketGuard
python3 scripts/run_preflight.py
```

`READY`가 나오면(Linux에서는 잠자기 점검을 건너뜁니다) 올립니다.

```bash
docker compose up --build --detach --wait
```

토큰 발급이 되는지 확인합니다. `outcome="200"`이 보이면 정상이고, `403`만 쌓이면 IP 등록이나 키를 다시 확인합니다. 발급이 실패하면 앱은 30초부터 최대 10분까지 기다렸다 다시 시도하므로 토스를 계속 두드리지 않습니다.

```bash
curl -s http://127.0.0.1:5050/actuator/prometheus | grep 'client="auth"'
```

로그인을 켜 둔 경우(`MARKETGUARD_SECURITY_ENABLED=true`)에는 `curl -u <관리자 아이디>`를 붙입니다.

## 5. 수집 중

**대시보드 보기**: 내 Mac에서 터널을 열고 브라우저로 `http://127.0.0.1:5050`에 접속합니다.

```bash
ssh -N -L 5050:127.0.0.1:5050 ubuntu@<예약 IP>
```

**매일 점검**(15:35–18:00 KST): [체크리스트 3단계](run-checklist.md#3-매일-장-마감-뒤-몇-분)를 서버에서 실행합니다.

```bash
ssh ubuntu@<예약 IP> 'cd MarketGuard && ./scripts/run_status.sh <등록한 종목 수>'
```

**백업**: 주 1회 이상 서버에서 백업을 만들고([운영 문서](operations.md#backup-and-restore)의 명령, DB 계정은 컨테이너 안에서 읽음) `backup ok`가 나온 파일만 Mac으로 가져옵니다. 인스턴스가 사라져도 결과를 잃지 않기 위해서입니다.

```bash
scp ubuntu@<예약 IP>:'~/MarketGuard/marketguard-*.dump' ~/marketguard-backups/
```

**실행 기록 커밋**: 서버에는 Git 계정이나 쓰기 권한을 두지 않습니다. 탐지 설정 파일을 `scp`로 Mac에 가져와 Mac의 레포에서 커밋·푸시합니다([체크리스트 2단계](run-checklist.md#2-시작-전-첫-거래일-전날)).

**하지 말 것**: 체크리스트의 금지 사항과 같습니다. 특히 서버에서 `git pull` 뒤 다시 빌드하지 않습니다.

## 6. 끝난 뒤

- 체크리스트 4단계대로 분석합니다. CSV는 서버에서 받아 Mac으로 가져옵니다. 로그인을 켜 둔 경우 `-u <관리자 아이디>`를 붙이면 curl이 비밀번호를 묻습니다.
  ```bash
  ssh ubuntu@<예약 IP> 'cd MarketGuard && curl --fail -o price-paths.csv "http://127.0.0.1:5050/api/analytics/price-paths.csv?days=<일수>"'
  ```
  ```bash
  scp ubuntu@<예약 IP>:~/MarketGuard/price-paths.csv .
  ```
- 수집을 멈추려면 `docker compose stop`을 씁니다. `docker compose down -v`는 데이터베이스까지 지우므로 백업을 확인한 뒤에만 씁니다.
- 인스턴스와 예약 IP를 지우기 전에, 토스 개발자 콘솔에서 그 IP 등록도 정리합니다.
