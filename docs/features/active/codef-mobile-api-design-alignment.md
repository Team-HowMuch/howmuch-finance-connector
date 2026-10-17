# CODEF 계좌 등록 화면과 API 매핑

- Status: 화면별 계약 검토 초안. 모바일 구현·Figma 수정 미수행.
- Updated: 2026-10-15
- 기준: 사용자가 전달한 계좌 등록 화면, backend `origin/develop` 470c73a8, dev OpenAPI 실응답.
- 범위: 기존 API 재사용 및 화면 상태 정리. 새 endpoint/DB 변경 없음, migration 불필요.

## 화면별 호출

모바일 인증 API는 로그인 accessToken을 사용한다. PC capability와 인증서·암호는 모바일로 전달하지 않는다. 모바일은 provider `connectedId` 대신 서버 `connectionId`로 계좌를 조회한다.

| 화면 | 호출 및 전환 조건 |
|---|---|
| 기존 금융기관 목록 | `GET /api/v2/accounts/connections`. 기관명·아이콘·연결 계좌 수 표시. 기존 기관 선택 시 해당 connectionId로 계좌 후보 조회. |
| 다른 기관에서 가져오기 / 금융기관 선택 | `GET /api/v3/banks`. organization·name·iconUrl·authenticationLevel을 사용. 실제 목록에 있는 금융사만 표시. |
| 연결 방법 선택 | level 1은 공동인증서만, 2는 공동인증서 및 ID/PW, 3은 두 방법과 추가 계좌정보 입력. |
| PC 앱 설치 및 사용 방법 | 로컬 안내 bottom sheet. PC 앱 실행 → 휴대폰으로 PC QR 스캔 → PC에서 고정된 금융사의 인증서 인증 → 휴대폰 최종 연결 승인 순서로 안내. API 호출 없음. |
| QR 스캔 | PC가 `POST /api/v2/certificate-pairings`로 만든 QR의 pairingId와 qrToken을 읽는다. 모바일 `POST /api/v2/certificate-pairings/{id}/claim`에 `{qrToken, organization}` 전달. organization은 앞에서 선택한 은행이며 필수. |
| PC 인증 대기 | 모바일 `GET /api/v2/certificate-pairings/{id}` polling. QR 스캔은 기기 연결이며 인증서 검증 완료가 아니다. PC가 인증서 선택/암호 입력/금융사 인증을 수행한다. |
| 은행 확인 및 6자리 번호 | `READY_FOR_CLAIM` 확인 후 `POST /api/v2/certificate-pairings/{id}/approval`. 반환 confirmationCode를 PC와 대조하고 선택한 기관명을 표시한다. sessionId는 pairing 조회 결과, claimId는 approval 응답에서 취득. |
| 네, 연결할게요 | `POST /api/v2/certificate-claims/{sessionId}/{claimId}/decision`에 `{approved:true}`. 결과가 즉시 BOUND가 아니면 아래 조회로 완료 대기. 거절은 approved:false. |
| 연결 처리 중 | `GET /api/v2/certificate-claims/{sessionId}/{claimId}`. `BOUND`와 유효한 connectionId 확인 후 계좌 후보 조회. `APPROVED_BINDING`은 완료가 아니다. |
| ID/PW 입력 | `POST /api/v2/accounts/connections/banks`에 organization·loginId·password, 필요 시 accountNumber·accountPassword·birthDate 전달. 성공 응답의 connectionId와 candidates로 계좌 선택 표시. |
| 등록할 계좌 선택 | `GET /api/v2/accounts/connections/{connectionId}/accounts?organization={organization}`. 기관이 요구하면 birthDate 전달. name·maskedNumber·balance·currency 표시, alreadyLinked는 선택 불가. |
| 등록 | `POST /api/v2/accounts/external`에 `{connectionId, organization, candidateKeys:[...]}` 및 필요한 birthDate. 1~50개 선택 가능, 응답 성공 후만 완료 화면. raw 계좌번호로 후보를 식별하지 않는다. |

## 디자인에서 보완할 내용

1. PC 안내 3단계에 휴대폰 최종 승인까지 포함한다. QR 스캔 직후 6자리 번호를 표시하지 않고, PC 금융사 인증 대기 상태를 둔다.
2. ID/PW 화면에 level 3의 계좌번호·계좌 비밀번호 필드를 추가한다. birthDate는 별도 기관 정책과 서버 오류 계약을 확인해야 하며 level만으로 요구 여부를 추측하지 않는다.
3. QR 만료·PC 종료·금융사 인증 실패·최종 승인 실패·계좌 조회 실패 화면을 나눈다. 실패 사유는 공통 코드 메시지로 안내하고 재시도 가능한 위치로 돌아간다. 계좌 조회 추가인증 요구를 연결 성공으로 처리하지 않는다.
4. 연결 진행 중 버튼 중복 요청을 막고, 최종 승인 완료와 계좌 등록 완료를 구분한다. 취소/뒤로가기는 `DELETE /api/v2/certificate-pairings/{id}`를 사용한다. 앱 강제 종료는 요청 전달이 보장되지 않으므로 서버 만료가 최종 안전장치다.
5. 지원 여부는 v3 은행 목록을 기준으로 한다. 화면 예시의 특정 은행을 임의로 고정하거나 지원 금융사로 가정하지 않는다.

## dev 배포 차이 — 2026-10-15 10:15 KST 확인

- develop 코드에는 `/api/v3/banks`, authenticationLevel, 필수 organization claim, 모바일 취소 API가 존재한다.
- 현재 dev `/v3/api-docs`는 `/api/v2/accounts/banks`와 requiresAccountCredentials를 반환하고 v3 은행 API가 없다. 캐시 방지 query를 붙여도 동일하다. 비로그인 `/api/v3/banks` 호출은 401이다.
- 401만으로 특정 배포 버전을 확정할 수는 없지만, OpenAPI와 코드 계약은 일치하지 않는다. GitHub deploy 성공 기록만으로 현재 외부 라우팅의 최신 버전 제공을 보장하지 않는다.
- rc.5는 v3 계약을 유지한다. legacy boolean으로 자동 fallback하지 않는다. dev 서버 배포 이미지·라우팅을 확인한 뒤 실제 모바일→PC→BOUND→계좌 등록 종단 테스트가 필요하다.

## 검증 범위

Controller/DTO를 기준으로 경로·필드·전환 조건 확인, dev OpenAPI와 공개 endpoint 조회 수행. 실제 모바일 클라이언트 구현, 사용자 인증정보를 이용한 계좌 연결·등록, Figma 편집은 수행하지 않았다.
