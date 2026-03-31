# 🛡️ Veri-Q Gateway & Security Server (BE1)

<p align="center">
  <img src="https://img.shields.io/badge/SpringBoot-6DB33F?style=flat-square&logo=SpringBoot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=Redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=Docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/GCP-4285F4?style=flat-square&logo=GoogleCloud&logoColor=white"/>
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=GitHubActions&logoColor=white"/>
</p>

## 📌 Project Overview
[cite_start]**Veri-Q** 서비스의 최전방에서 모든 외부 요청을 수집하고, **Redis 기반 트래픽 제어** 및 **보안 필터링**을 수행하는 핵심 게이트웨이 서버입니다. 

---

## 🏗️ System Architecture
[cite_start]서비스는 마이크로서비스(MSA) 형태로 분산되어 있으며, 본 서버는 **Security & Traffic Control**을 전담합니다. [cite: 14]



```text
[ Client (React) ] 
       │
       ▼ (HTTP/HTTPS)
┌──────────────────────────────────────────┐
│  Veri-Q Gateway (Spring Boot / Port 8081)│ ◀─── [ Redis (Rate Limit) ]
└──────────────────────────────────────────┘
       │
       ├────▶ [ Platform Server (BE3 / Port 8083) ] ──▶ [ MySQL ]
       │
       └────▶ [ Analysis Engine (FastAPI / Port 8000) ]
## 📋 Git Commit Convention
우리 팀은 협업 효율성과 코드 히스토리 관리를 위해 아래의 커밋 컨벤션을 엄격히 준수합니다.

| 타입 | 의미 | 상세 내용 |
| :--- | :--- | :--- |
| **feat** | 새로운 기능 추가 | 새로운 기능 구현 및 로직 추가 시 사용 |
| **fix** | 버그 수정 | 기능 결함 및 오류 수정 시 사용 |
| **docs** | 문서 수정 | README, API 명세 등 문서 작성 및 수정 시 사용 |
| **style** | 코드 포맷팅 | 세미콜론 누락, 코드 줄 바꿈 등 로직 변경 없는 수정 시 사용 |
| **refactor** | 코드 리팩토링 | 코드 가독성 개선 및 구조 변경 시 사용 |
| **test** | 테스트 코드 | 테스트 코드 추가, 수정 및 삭제 시 사용 |
| **chore** | 빌드 및 패키지 관리 | build.gradle 설정, 의존성 라이브러리 관리 시 사용 |

---

## 🛠 Tech Stack
Veri-Q 게이트웨이 서버에 사용된 핵심 기술 스택입니다.

* **Language**: Java 17
* **Framework**: Spring Boot 3.x
* **Security**: 
    * [cite_start]Google reCAPTCHA v2 (사용자 인증 및 봇 차단) 
    * [cite_start]Redis Rate Limiter (UUID 기반 트래픽 제어) 
* **Infrastructure**: 
    * [cite_start]Docker (인프라 컨테이너화) 
    * Google Cloud Platform (GCP VM 인스턴스 호스팅)
* **DevOps**: GitHub Actions (CI/CD 자동 배포 환경 구축)

---

## 🔗 API Documentation
프론트엔드 및 타 서버와의 원활한 협업을 위해 Swagger를 통한 자동화된 API 문서를 제공합니다.

* **Swagger UI**: [http://[GCP_EXTERNAL_IP]:8081/swagger-ui/index.html](http://[GCP_EXTERNAL_IP]:8081/swagger-ui/index.html)
> **Note**: `[GCP_EXTERNAL_IP]` 부분은 아직 서버 구축이 안되어서 GCP서버 구축이 되면 업데이트 하겠습니다

## 🚧 현재 진행 상황: BE1 (Gateway & Security)
현재 **Veri-Q** 게이트웨이 서버의 핵심 보안 인프라를 구축하고 있으며, 단계별 구현 상황은 다음과 같습니다.

### **1. 진행 중 (In Progress) 🏗️**
* **Redis 기반 Rate Limiting 구현**
    * Guest UUID별로 API 요청 횟수를 제한하여 서비스 가용성 확보
    * 현재 로컬 테스트  및 GCP 운영 환경 적용 중
* **Google reCAPTCHA v2 인증 연동**
    * 임계치 초과 요청 시 사용자 인증 절차를 통한 봇(Bot) 접근 원천 차단
    * 프론트엔드와 인증 토큰 검증 로직 연동 준비중

* **CI/CD 자동 배포 환경 구축**
    * **GitHub Actions**를 활용하여 코드 Push 시 GCP VM으로 자동 빌드 및 배포
    * Docker 이미지 빌드 및 컨테이너 원격 재시작 스크립트 작성 예정

---
### **📍 상세 작업 히스토리**
> **Note**: `analysis-engine` 파트와 발맞추어 API 및 DB 조회 로직의 정규화 작업을 병행하고 있습니다.

