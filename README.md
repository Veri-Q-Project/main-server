# Veri-Q 서버 접속 및 개발 가이드
 구축한 GCP 서버 인프라를 사용하기 위한 가이드입니다.

## 🌐 서버 정보
- **공통 외부 IP**: `34.64.218.236`
- **관리자 계정**: `root` (DB 접속 시 사용)
* **서버 사용자**: `sk38808738` (SSH 접속 시 사용)

### 서비스별 포트
| 서비스 | 포트 | 용도 |
| :--- | :--- | :--- |
| **Spring BE1** | `8081` | Gateway 서버 |
| **Spring BE3** | `8083` | Platform/Data Hub 서버 |
| **FastAPI** | `8000` | 분석 엔진 서버 |
| **MySQL** | `3306` | 데이터베이스 |
| **Redis** | `6379` | 캐시 서버 |

---
## 🛠️ 팀원별 환경 설정 가이드 (로컬 개발용)

> **공통 외부 IP**: `34.64.218.236`  
> 모든 팀원은 로컬에서 테스트할 때 아래 설정값을 참조하여 접속 정보를 수정해 주세요.



### **.🍃 Spring Boot, ⚡ FastAPI **
`src/main/resources/application.yml`,`.env` 파일의 설정을 아래와 같이 수정합니다.
```yaml

spring:
  datasource:
    # GCP MySQL 연결 설정
    url: jdbc:mysql://veriq-db:3306/veriq_db?serverTimezone=Asia/Seoul
    username: root
    password: [카톡방에 공유된 비밀번호]
  data:
    redis:
      # GCP Redis 연결 설정
      host: veriq-redis
      port: 6379

--- env
# Database 연결 정보
DB_URL = "mysql+pymysql://root:[비밀번호]@34.64.218.236:3306/veriq_db"

# Redis 연결 정보
REDIS_HOST = "34.64.218.236"
REDIS_PORT = 6379

```







### **📦 Docker 배포 설정 (최상위 경로에 위치)



### **🍃 Spring Boot Dockerfile
```yaml
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
# 빌드된 JAR를 app.jar로 복사 (파일명에 상관없이 자동 매칭)
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```



### **⚡FastAPI Dockerfile
```yaml
FROM python:3.9-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

```










### **GitHub Actions 자동 배포 (deploy.yml)
위치: .github/workflows/deploy.yml //각자 개발하는 인텔리제이나 fastapi에서 위치함
```yaml
name: Veri-Q Auto Deploy

on:
  push:
    branches: [ "develop" ] # develop 브랜치 푸시 시 자동 배포

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: SSH Remote Commands
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.GCP_IP }}
          username: ${{ secrets.GCP_USER }}
          key: ${{ secrets.GCP_SSH_KEY }}
          script: |
            cd /home/sk38808738
            sudo docker-compose pull # 최신 이미지 다운로드
            sudo docker-compose up -d # 컨테이너 재실행
```

### **docker-compose.yml

```yaml
version: '3.8'

services:
  # 1. MySQL 데이터베이스
  veriq-db:
    image: mysql:8.0
    container_name: veriq-db
    restart: always
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: veriq123
      MYSQL_DATABASE: veriq_db
    volumes:
      - ./mysql_data:/var/lib/mysql

  # 2. Redis 캐시 서버
  veriq-redis:
    image: redis:latest
    container_name: veriq-redis
    restart: always
    ports:
      - "6379:6379"

  # 3. Spring BE1 (Gateway - 박상민 )
  veriq-gateway:
    image: eclipse-temurin:21-jdk
    container_name: veriq-be1
    restart: always
    ports:
      - "8081:8081"
    depends_on:
      - veriq-db
      - veriq-redis

  # 4. Spring BE3 (Platform/Data Hub)
  veriq-platform:
    image: eclipse-temurin:21-jdk
    container_name: veriq-be3
    restart: always
    ports:
      - "8083:8083"
    depends_on:
      - veriq-db
      - veriq-redis

  # 5. FastAPI (분석 엔진)
  veriq-analysis:
    image: python:3.9-slim
    container_name: veriq-analysis
    restart: always
    ports:
      - "8000:8000"
    depends_on:
      - veriq-db
```


### * 배포 프로세스
```yaml
빌드:

Spring: ./gradlew bootJar 실행하여 JAR 생성

FastAPI: 새 라이브러리 추가 시 pip freeze > requirements.txt 갱신

푸시: develop 브랜치로 Push하면 서버에 자동 반영됩니다.

```

