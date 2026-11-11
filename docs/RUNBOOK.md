# 실행·운영 매뉴얼

## 실행 확인

- Docker: `docker compose up --build`, 웹 `http://localhost:4173`.
- 로컬 임시 DB: `./scripts/dev.sh`, 웹 `http://localhost:5173`.
- 직접 PostgreSQL 개발: API `http://localhost:8080`, `/actuator/health`는 로그인 없이 확인 가능.
- 기본 프로필에는 데모 계정·데이터가 없습니다. 시연에는 `demo` 프로필을 명시합니다.

Docker 볼륨의 샘플 데이터는 최초 실행 때 생성됩니다. 이후 실행은 기존 데이터를 사용하므로 이미 마감한 월이나 수납 상태가 그대로 보입니다. 서버 재시작은 업무 데이터를 초기화하지 않습니다.

## 오류별 점검

| 증상 | 확인 순서 |
|---|---|
| 로그인 실패 | `demo` 프로필, `DEMO_PASSWORD` 변경 여부, API 로그 |
| 화면은 열리는데 API 실패 | Docker `api` 로그 또는 8080 포트 기동, Vite/Nginx 프록시 |
| 403 변경 실패 | 조회 전용 계정인지, CSRF 토큰을 로그인 후 새로 받았는지 |
| 청구 발행 불가 | 수급자·월의 예정 일정과 완료 일정 유무 확인 |
| 일정 변경 409 | 해당 월 청구서 발행 여부, 상태 전이 규칙, 하루 중복 여부 |
| 수납 409 | 서버 상세의 미수금과 요청 키·금액·메모 확인 |
| 500 오류 | 응답 `X-Request-ID` / `requestId`로 API 로그 검색 |
| 임시 PostgreSQL 기동 실패 | root 여부, Linux/macOS 지원·라이브러리·임시 폴더 실행 권한 확인 |

### 수납 요청이 타임아웃된 경우

수납을 다시 누르기 전에 청구서 상세의 수납 내역을 조회합니다. 브라우저에 남은 동일 요청은 같은 키로 다시 보냅니다. 성공 응답을 받기 전에 임의로 새 키를 만들어 재전송하지 않습니다. 다른 금액·메모로 변경하면 새로운 요청으로 취급되므로 먼저 기존 처리 여부를 확인해야 합니다.

서버에서 요청 키를 찾고, `payment` 합계와 `invoice.paid_amount`를 대조할 수 있습니다. 예시 SQL의 ID는 실제 시연 데이터로 바꿉니다.

```sql
SELECT i.id, i.center_id, i.copay_amount, i.paid_amount,
       COALESCE(SUM(p.amount), 0) AS payment_sum
FROM invoice i
LEFT JOIN payment p ON p.invoice_id = i.id AND p.center_id = i.center_id
WHERE i.id = 1 AND i.center_id = 1
GROUP BY i.id, i.center_id, i.copay_amount, i.paid_amount;
```

이 프로젝트에는 수납 취소 API가 없습니다. 직접 SQL로 수납액을 덮어써 정합성을 깨뜨리지 말고, 취소·역분개 규칙을 후속 기능으로 설계해야 합니다.

## 쿼리 지연 점검

1. DB 실행 시간과 브라우저/전체 API 시간을 구분합니다.
2. 센터·기간·상태·페이지 크기 등 실제 요청 조건을 재현합니다.
3. PostgreSQL에서 `EXPLAIN (ANALYZE, BUFFERS)`를 확인합니다.
4. 반환 건수와 JOIN, 인덱스, DB 잠금 대기를 점검합니다.
5. 애플리케이션의 커넥션 풀 대기·직렬화·네트워크 시간을 확인합니다.

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT v.id, v.service_date, r.name, v.status
FROM visit v
JOIN recipient r ON r.id = v.recipient_id AND r.center_id = v.center_id
WHERE v.center_id = 1
  AND v.service_date BETWEEN DATE '2026-11-01' AND DATE '2026-11-30'
  AND v.status = 'COMPLETED'
ORDER BY v.service_date DESC, v.start_time ASC, v.id DESC
LIMIT 20;
```

날짜 예시는 현재 데이터에 맞춰 변경합니다. 샘플 데이터에서 순차 스캔이 선택되어도 곧바로 인덱스 문제라고 판단하지 않습니다.

## 운영 배포로 발전시킬 때

지금 제공하는 Compose는 로컬 데모 구성입니다. 실제 운영에는 영속 계정·소속/권한 관리, TLS·Secure 쿠키, 세션 공유, 감사 보존·접근 제어, DB 백업과 복구 실습, 모니터링·알림, 이관 검증, 취소·정정 업무 설계가 추가되어야 합니다. 실제 환자 정보나 금융 데이터를 현재 데모에 넣지 않습니다.

## AI 활용과 검증

AI를 요구사항 정리, 코드 작성, 테스트 시나리오와 문서 초안에 사용했습니다. 판단 근거는 컴파일·타입 검사, 실제 PostgreSQL 테스트, 브라우저 검증과 남겨둔 실행 결과입니다. 면접에서는 AI가 작성한 부분까지 설명하고 수정할 수 있는 범위를 확인한 후 자신의 학습 성과로 제시합니다.
