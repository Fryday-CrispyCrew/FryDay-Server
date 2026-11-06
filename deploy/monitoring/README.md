# 모니터링 (Prometheus, Grafana, Loki)

dev 인스턴스의 `~/monitoring` 에서 docker compose 로 돈다. 이 폴더가 그 설정의 원본이다.

| 파일 | 내용 |
|---|---|
| `compose.yml` | Prometheus, Grafana, Loki |
| `prometheus/prometheus.yml` | 수집 설정. 수집 대상 주소는 서버의 `prometheus/targets/` 에만 둔다 |
| `grafana/provisioning/` | 데이터 소스(`fryday-prometheus`), 대시보드 로딩, Discord 연락처, 알림 규칙 |
| `grafana/dashboards/fryday-golden-signals.json` | Golden Signals 대시보드 |

서버에만 두고 레포에 올리지 않는 것: `.env`, `prometheus/targets/*.yml`, `loki-config.yaml`, `grafana-data/`

## 대시보드와 알림 고치기

provisioning 으로 올린 대시보드와 알림은 Grafana UI 에서 저장할 수 없다.
UI 에서 고친 뒤 JSON 을 내보내(Share > Export) 이 폴더의 파일을 바꾸고 PR 로 반영한다.
알림 임계치는 `grafana/provisioning/alerting/rules.yml` 에서 바꾼다.

## 처음 적용하기 (기존 수동 구성에서 옮기기)

기존 `~/monitoring/docker-compose.yml` 구성을 이 폴더 기준으로 바꾼다. Grafana 계정과 기존 대시보드(`grafana-data`), Loki 설정은 그대로 쓴다.

```bash
# 0. 백업 (grafana-data 는 Grafana 컨테이너 사용자 소유라 sudo 가 필요하다)
cd ~ && sudo cp -a monitoring monitoring-backup-$(date +%Y%m%d)

# 1. 레포의 설정을 가져와 복사한다
git clone --depth 1 https://github.com/Fryday-CrispyCrew/FryDay-Server.git /tmp/fryday-server
cp -R /tmp/fryday-server/deploy/monitoring/. ~/monitoring/
cd ~/monitoring

# 2. 수집 대상 주소를 적는다 (기존 prometheus.yml 의 targets 값)
mkdir -p prometheus/targets
cp prometheus/targets.example/*.yml prometheus/targets/
vi prometheus/targets/fryday-prod.yml        # 기존 fryday-prod 의 targets
vi prometheus/targets/fryday-dev.yml         # 기존 fryday-dev 의 targets
vi prometheus/targets/node-exporter-prod.yml # 기존 node-exporter-prod 의 targets

# 3. 환경변수를 적는다 (관리자 비밀번호와 ROOT_URL 은 기존 docker-compose.yml 의 값)
cp .env.example .env && vi .env

# 4. Prometheus 데이터를 이름 있는 볼륨으로 옮긴다 (기존 볼륨은 지우지 않는다)
OLD=$(docker inspect prometheus --format '{{range .Mounts}}{{if eq .Destination "/prometheus"}}{{.Name}}{{end}}{{end}}')
echo "$OLD"
docker stop prometheus
docker volume create fryday-prometheus-data
docker run --rm -v "$OLD":/from:ro -v fryday-prometheus-data:/to alpine sh -c 'cp -a /from/. /to/'

# 5. 기존 compose 파일을 치우고 새로 띄운다
mv docker-compose.yml docker-compose.yml.old
mv prometheus.yml prometheus.yml.old
docker compose up -d
```

### 확인
- `fryday-prod`, `fryday-dev`, `node-exporter-prod` 가 모두 UP 인지 본다.
  Prometheus 는 서버 안에서만 열리므로 서버에서 확인하거나 SSH 터널로 연다.
  ```bash
  # 서버에서
  curl -s localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"'
  # 또는 내 PC 에서 터널을 열고 브라우저로 http://localhost:9090/targets
  ssh -L 9090:localhost:9090 ubuntu@<서버>
  ```
- Grafana 의 Fryday 폴더에 `Fryday Golden Signals` 대시보드가 있는지 본다.
  - 지연 시간 히스토그램, Tomcat, DB 트랜잭션, 비즈니스 에러 패널은 앱이 새 버전으로 배포된 뒤부터 값이 나온다.
- Grafana > Alerting > Contact points > `fryday-discord` > Test 로 Discord 에 알림이 오는지 본다.
- Alerting > Alert rules 의 Fryday 폴더에 규칙 10개가 있는지 본다.

## 되돌리기

```bash
cd ~/monitoring
docker compose down        # -v 를 붙이지 않는다 (볼륨이 지워진다)
mv docker-compose.yml.old docker-compose.yml
mv prometheus.yml.old prometheus.yml
docker compose -f docker-compose.yml up -d
```

이전 compose 는 Prometheus 데이터를 이름 없는 새 볼륨에 쌓는다.
옮기기 전 데이터는 4번에서 확인한 기존 볼륨에, 옮긴 뒤 데이터는 `fryday-prometheus-data` 에 남아 있다.

## 그 밖의 운영 메모
- Prometheus 보관 기간은 기본값(15일)이다.
- 이미지는 `latest` 를 쓴다. `docker compose pull` 은 바뀐 점을 확인하고 실행한다.
