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
> 
> > 🚨 **중요: 보안을 위해 DB 비밀번호가 코드에서 제거되었습니다.**
> 프로젝트를 처음 클론받거나 Pull 한 후, 반드시 프로젝트 최상단(루트) 경로에 **`.env`** 파일을 생성해 주세요.

### 1️⃣ `.env` 파일 생성 (필수)
프로젝트 루트에 `.env` 파일을 만들고 아래 내용을 입력합니다. (비밀번호는 카톡방 공지 확인)
```text
MYSQL_ROOT_PASSWORD=여기에_공유된_비밀번호

```

### **.🍃 Spring Boot
`src/main/resources/application-be1.yml` 아래와 같이 수정합니다.
```yaml

server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://veriq-db:3306/veriq_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: root
    password: ${MYSQL_ROOT_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    # DB 연결을 최대 30초까지 기다려주는 인내심 설정
    hikari:
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        # MySQL 8.0 버전에 맞게 Dialect를 조금 더 구체적으로 지정
        dialect: org.hibernate.dialect.MySQL8Dialect

  data:
    redis:
      host: veriq-redis
      port: 6379


  be3:
    api:
      url: "http://localhost:8083/api/v1/scan/upload"
```
`src/main/resources/application-be3.yml` 아래와 같이 수정합니다.
```yaml
server:
  port: 8083

spring:
  datasource:
    url: jdbc:mysql://veriq-db:3306/veriq_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: root
    password: ${MYSQL_ROOT_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    # DB 연결을 최대 30초까지 기다려주는 인내심 설정
    hikari:
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        # MySQL 8.0 버전에 맞게 Dialect를 조금 더 구체적으로 지정
        dialect: org.hibernate.dialect.MySQL8Dialect

  data:
    redis:
      host: veriq-redis
      port: 6379

```

### **⚡FastAPI
--env파일을 다음과 같이 수정합니다
```yaml
# Database 연결 정보
DB_URL = "mysql+pymysql://root:[비밀번호]@34.64.218.236:3306/veriq_db"

# Redis 연결 정보
REDIS_HOST = "34.64.218.236"
REDIS_PORT = 6379

```







### **📦 인프라 및 배포 설정 파일 안내(Dockerfile)



### **🍃 Spring Boot Dockerfile

```yaml
FROM eclipse-temurin:21-jdk

# 애플리케이션 전용 비루트 사용자 생성
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app

# JAR 파일 복사 및 권한 부여
ARG JAR_FILE=build/libs/*.jar
COPY --chown=appuser:appgroup ${JAR_FILE} /app/app.jar

# 사용자 전환 및 실행
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
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
### **🐳 docker-compose.yml(공통) (최상위 경로)
```yaml
services:
  # 1. MySQL 데이터베이스
  veriq-db:
    image: mysql:8.0
    container_name: veriq-db
    restart: always
    ports:
      - "127.0.0.1:3306:3306" # 외부 IP 접속 차단, 로컬 호스트만 허용
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: veriq_db
    volumes:
      - ./mysql_data:/var/lib/mysql

  # 2. Redis 캐시 서버
  veriq-redis:
    image: redis:latest
    container_name: veriq-redis
    restart: always
    ports:
      - "127.0.0.1:6379:6379"

  # 3. Spring BE1 (Gateway)
  veriq-gateway:
    build: 
      context: .
      dockerfile: Dockerfile
    container_name: veriq-be1
    restart: always
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: be1
      BE3_API_URL: "http://veriq-platform:8083/api/v1/scan/upload" # 도커 내부망 주소로 덮어쓰기
    depends_on:
      - veriq-db
      - veriq-redis

  # 4. Spring BE3 (Platform/Data Hub)
  veriq-platform:
    build: 
      context: .
      dockerfile: Dockerfile
    container_name: veriq-be3
    restart: always
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: be3
    depends_on:
      - veriq-db
      - veriq-redis

  # 5. FastAPI (분석 엔진)
  veriq-analysis:
    build:
      context: ../analysis-engine
      dockerfile: Dockerfile
    container_name: veriq-analysis
    restart: always
    ports:
        - "8000:8000"
    depends_on:
        - veriq-db

```










### **GitHub Actions 자동 배포(🍃 Spring Boot) (.github/workflows/deploy.yml)
develop 브랜치에 코드가 푸시되면 자동으로 GCP 서버에 배포됩니다.

```yaml
name: Veri-Q Develop Deploy

on:
  push:
    branches: [ "develop" ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: write
      packages: write
    steps:
      - name: SSH Remote Commands
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.GCP_IP }}
          username: ${{ secrets.GCP_USER }}
          key: ${{ secrets.GCP_SSH_KEY }}
          script: |
            cd /home/sk38808738/main-server
            git pull origin develop
            chmod +x gradlew
            sudo ./gradlew bootJar
            
            # GitHub Secrets에서 안전하게 비밀번호를 가져와 서버에 .env 파일 즉석 생성
            echo "MYSQL_ROOT_PASSWORD=${{ secrets.MYSQL_ROOT_PASSWORD }}" > .env
            
            # 컨테이너 빌드 및 재실행
            sudo docker-compose up --build -d
```
### **GitHub Actions 자동 배포(⚡FastAPI) (.github/workflows/deploy.yml)
develop 브랜치에 코드가 푸시되면 자동으로 GCP 서버에 배포됩니다.

```yaml
name: FastAPI Auto Deploy

on:
  push:
    branches: [ "develop" ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: SSH Remote Commands for FastAPI
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.GCP_IP }}
          username: ${{ secrets.GCP_USER }}
          key: ${{ secrets.GCP_SSH_KEY }}
          script: |
            # 1. FastAPI 폴더로 이동해서 최신 코드 pull
            cd /home/sk38808738/analysis-engine
            git pull origin develop
            # 2. 도커 설계도가 있는 메인 서버 폴더로 이동
            cd /home/sk38808738/main-server
            sudo docker-compose up --build -d veriq-analysis



```



