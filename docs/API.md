# API 계약

기본 경로는 `/api`입니다. 로그인 세션으로 인증하고, 변경 요청은 CSRF 토큰이 필요합니다. `centerId`는 클라이언트 입력을 받지 않습니다. 목록은 기본 페이지 0·크기 20이며 최대 크기 100, 최대 페이지 번호 10000입니다.

## 인증

1. `GET /api/csrf`로 `{token, headerName}`을 가져옵니다.
2. 응답의 토큰을 해당 이름의 HTTP 헤더로 보내며 `POST /api/login`에 form-urlencoded `username`, `password`를 전달합니다.
3. 로그인 후 토큰이 회전하므로 `/api/csrf`를 다시 조회합니다.
4. 쿠키를 유지하며 API를 호출합니다.
5. `POST /api/logout`에도 CSRF 헤더를 넣습니다.

프론트엔드는 같은 origin의 `/api`를 호출합니다. Vite 또는 Nginx가 API로 프록시하므로 브라우저 CORS 설정을 별도로 열지 않습니다.

## 엔드포인트

| 메서드 | 경로 | 용도 | 권한 |
|---|---|---|---|
| GET | `/csrf` | CSRF 토큰 | 공개 |
| POST | `/login` | 세션 로그인 | 공개, CSRF 필요 |
| POST | `/logout` | 로그아웃 | 인증·CSRF |
| GET | `/session` | 계정·센터·변경 권한 | 인증 |
| GET | `/dashboard?month=2026-11` | 오늘 운영·월 청구 집계 | 인증 |
| GET | `/recipients?name=김&page=0&size=20` | 수급자 검색 | 인증 |
| POST | `/recipients` | 수급자 등록 | MANAGER |
| GET | `/visits?month=2026-11&status=PLANNED&name=김` | 일정 검색 | 인증 |
| POST | `/visits` | 일정 등록 | MANAGER |
| PATCH | `/visits/{id}` | 이용 완료·취소 | MANAGER |
| GET | `/invoices?month=2026-11&unpaidOnly=true` | 청구서 조회 | 인증 |
| POST | `/invoices` | 월 청구서 발행 | MANAGER |
| GET | `/invoices/{id}` | 항목·수납 상세 | 인증 |
| POST | `/invoices/{id}/payments` | 수납 기록 | MANAGER |
| GET | `/audit-events?page=0&size=20` | 변경 이력 | 인증 |

청구서·일정 목록도 `page`, `size`를 지원합니다. 잘못된 날짜·상태·본문의 알 수 없는 속성은 400입니다.

## 요청 예시

### 수급자

```json
{"name":"가상 수급자","careGrade":3,"dailyFee":60000,"copayBasisPoints":1500}
```

이름은 공백만으로 등록할 수 없고 최대 40자, 등급은 1~5, 일 이용료는 1,000~1,000,000원, 부담률은 0~10000 basis point입니다. 요금은 학습용입니다.

### 일정

```json
{"recipientId":1,"serviceDate":"2026-11-11","startTime":"09:00","endTime":"17:00"}
```

날짜는 2000~2100년이고 종료 시각은 시작 시각 이후입니다. 동일 수급자의 하루 일정은 한 건입니다.

```json
{"status":"COMPLETED"}
```

`PLANNED`에서 `COMPLETED` 또는 `CANCELLED`로만 전이합니다. 같은 상태의 반복 요청은 상태를 다시 바꾸거나 감사 이력을 추가하지 않습니다. 미래 일정은 완료할 수 없습니다.

### 월 청구서

```json
{"recipientId":1,"month":"2026-11"}
```

첫 발행은 **201**, 동일 센터·수급자·월 재요청은 **200**입니다.

```json
{"data":{"id":1,"recipientId":1,"recipientName":"가상 수급자","billingMonth":"2026-11","totalAmount":60000,"copayAmount":9000,"paidAmount":0,"outstandingAmount":9000},"replayed":false}
```

### 수납

`Idempotency-Key` 헤더에 8~64자의 영문·숫자·밑줄·하이픈으로 구성한 키를 지정합니다. UUID를 권장합니다.

```json
{"amount":1000,"memo":"계좌 입금 확인"}
```

수납은 이미 받은 돈을 시스템에 기록하는 기능입니다. 실제 결제나 이체가 발생하지 않습니다. 처음 기록은 201, 동일 키·내용 재요청은 200입니다. 메모는 앞뒤 공백을 정규화해 비교합니다. 같은 센터의 다른 청구서에 같은 키를 재사용하면 충돌합니다.

### 목록 공통 형식

```json
{"items":[],"total":0,"page":0,"size":20}
```

## 오류 계약

```json
{"code":"OVERPAYMENT","message":"수납액은 0원보다 크고 미수금 이하여야 합니다.","requestId":"cbbafde5-c052-4405-b201-d159c85a664b","fieldErrors":{}}
```

| 코드 | HTTP | 의미·조치 |
|---|---:|---|
| `UNAUTHORIZED` | 401 | 로그인 필요 |
| `LOGIN_FAILED` | 401 | 계정·비밀번호 확인 |
| `FORBIDDEN` | 403 | 변경 권한 또는 CSRF 토큰 확인 |
| `NOT_FOUND` | 404 | 데이터 없음 또는 다른 센터 데이터 |
| `VALIDATION_ERROR` | 400 | 필드별 값 확인 |
| `INVALID_REQUEST` | 400 | 날짜·본문·페이지 형식 확인 |
| `INVALID_TIME_RANGE` | 400 | 이용 시간 역전 |
| `INVALID_IDEMPOTENCY_KEY` | 400 | 요청 키 누락·형식 오류 |
| `DATA_CONFLICT` | 409 | 고유 제약·데이터 제약 충돌, 새로고침 후 확인 |
| `PENDING_VISITS` | 409 | 월 예정 일정이 남아 있음 |
| `NO_COMPLETED_VISITS` | 409 | 청구할 완료 일정이 없음 |
| `MONTH_CLOSED` | 409 | 이미 청구된 월의 일정 변경 요청 |
| `INVALID_VISIT_TRANSITION` | 409 | 허용되지 않는 상태 변경 |
| `FUTURE_VISIT` | 409 | 미래 일정 완료 요청 |
| `OVERPAYMENT` | 409 | 미수금 초과 수납 |
| `IDEMPOTENCY_CONFLICT` | 409 | 같은 키에 다른 내용 전달 |
| `INTERNAL_ERROR` | 500 | 요청 ID와 서버 로그로 확인 |

존재 여부와 센터 권한을 구분해 다른 센터 데이터의 존재를 노출하지 않습니다. 실제 HTTP 오류 예시는 통합 테스트에서 확인할 수 있습니다.
