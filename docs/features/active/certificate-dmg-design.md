# Feature Plan: 인증서 앱 DMG 설치 화면

- Status: Implemented — 로컬 검증 완료, native Intel 배포 검증 대기
- Last updated: 2026-10-13
- Related issue / ADR: 없음 (로컬 디자인 작업, 게시 요청 없음)

## 1. 목적
기본 DMG 화면을 얼마 색상과 간단한 설치 안내로 개선한다. Discord 첨부 화면과 Firefox의 앱 → Applications 드래그 패턴을 참고한다. 실제 앱과 Applications 링크는 Finder 항목으로 유지한다.

## 2. 도메인 분석
기존 certificate desktop 배포 리소스만 사용한다. 서버·인증서·금융 연결 로직과 무관하며 Domain/UseCase/Port 추가는 N/A다.

## 3. 시나리오
DMG를 열면 브랜드와 설치 안내, 앱 아이콘, 화살표, Applications 폴더가 보인다. 앱을 폴더로 드래그하여 설치한다. 배경의 아이콘 모양을 클릭 가능한 항목으로 위장하지 않는다.

## 4. 계약
- Flyway migration required: No. DB/API/환경 변수 변경 없음.
- 기존 npm/Release의 electron-builder 경로에서 동일한 DMG 설정을 사용한다.
- 기본/Retina 배경 PNG를 커밋하므로 CI에 Swift·폰트 설치를 추가하지 않는다.

## 5. 설계 결정
- 흰색과 기존 `#0092ff` 브랜드 색, 앱에 포함된 Pretendard 정적 폰트 사용.
- 720×460 배경과 1440×920 Retina 배경. 아이콘 좌표와 화살표 정렬을 고정한다.
- 배경 재생성 스크립트는 macOS AppKit 사용. 런타임 의존성 추가 없음.
- 이전 Intel 실패는 alias.py:635의 cnid_path 직렬화다. `Alias.for_file`은 vol_path만 NFC로 정규화하고 path는 그대로 relpath를 구한다. 한글 NFD mount 이름이면 상대경로에 상위 호스트 경로가 포함될 수 있다. ASCII volume title로 경로 불일치를 피하고 실제 alias.cnid_path가 빈 배열인지 확인한다. 로컬 성공을 Intel native 성공으로 보고하지 않는다.

## 6. 영향 범위
`apps/windows-certificate/package.json`, `installer/` 배포 이미지, `scripts/` 이미지 생성 도구, `test/release.test.cjs`, 앱 README. 서명·sandbox·ASAR fuse·앱 ID·서버 주소는 유지한다.

## 7. 구현 계획
- [x] 기본/Retina 배경 및 재생성 도구
- [x] DMG 배경·아이콘 배치
- [x] 실제 ARM DMG 생성 및 내부 리소스 확인
- [x] 문서 및 검증 결과 기록
- N/A: migration, UseCase, Ports, Domain, Policy, Service, HTTP, 저장소 변경.
- 예상 커밋 경계: `build(certificate): 브랜드 DMG 설치 화면 구성` (현재 커밋·푸시·PR 없음).

## 8. 검증
`npm test`, ARM DMG 실제 생성, 마운트 후 `.DS_Store`/배경/Applications 링크 확인, 서명 검증, `git diff --check` 및 status. Java 변경이 없어 Gradle 테스트는 N/A. Intel native 및 다운로드 후 Gatekeeper 검증은 별도다.

## 9. 진행 기록
2026-10-13: 최신 origin/develop에서 별도 branch 생성. 기존 빌드 경로와 Intel 배경 오류 이력 확인.

## 10. 결정 기록
- Discord: 사용자 제공 이미지.
- Firefox: https://support.mozilla.org/en-US/kb/how-download-and-install-firefox-mac
- Builder: https://www.electron.build/v26/docs/dmg/

## 11. 완료 기록
- Node 36개 테스트 통과. 실제 DmgTarget으로 리소스 해상도, ASCII volume name, Finder 항목과 배치 경계 검증.
- Apple Silicon에서 실제 arm64 앱 패키징 및 DMG 생성 통과. 기존 `verify-mac.cjs arm64`의 ad-hoc 서명/hardened runtime/API 설정/패키징 앱 실행 검증 통과.
- DMG를 read-only/noautoopen으로 마운트해 `.DS_Store`를 읽음: 720×460, iconSize 128, 앱 (190,260), Applications (530,260), 실제 `/Applications` 심볼릭 링크, `.background.tiff` alias 및 빈 cnid_path 확인. 검사 후 마운트 해제.
- 추가로 Rosetta x86_64 Node + x86_64 dmgbuild 도구로 동일 arm64 앱의 DMG 생성 통과. Intel용 앱/native Intel OS 검증을 대신하지 않는다.
- 일반/Retina 이미지와 합성 디자인 미리보기 생성. 미리보기는 Finder 스크린샷이 아니다.
- Java·서버 변경 없음. Gradle 테스트, native Intel 실행, 인터넷 다운로드 후 Gatekeeper 검증은 수행하지 않았다.
- 로컬 미리보기 설치본: `apps/windows-certificate/dist/HowMuch-Certificate-DMG-Preview-arm64.dmg` 및 SHA-256.
- 공개 Release 파일 교체, 버전 변경, commit/push/PR 없음. 신규 배포 시 이 branch를 통합하고 native Intel에서 검증해야 한다.

## 12. 아이콘 일치 수정 (2026-10-13)
사용자가 미리보기 아이콘이 앱과 다르고 정사각형인 점을 지적했다. 앱 헤더는 둥근 `logo.png`를 사용하지만 미리보기/기존 패키징/Dock은 사각 `icon.png`를 사용함을 확인했다.
- 기존 고해상도 icon.png의 그림을 유지하고 macOS 전용 투명 여백·둥근 모서리·그림자가 포함된 `src/assets/icon-mac.png`를 생성한다.
- Mac builder icon, macOS Dock, DMG 미리보기가 동일 파일을 사용한다. DMG 헤더는 기존 logo.png를 사용한다.
- Windows 아이콘과 앱 내부 헤더는 변경하지 않는다. 기존 API·보안·서명 설정 유지. DB migration 없음.
- 검증: Node 테스트, 실제 Mac 패키징의 ICNS·서명·앱 시작 및 갱신 DMG 확인. 새 설치본으로 미리보기 DMG를 교체한다.

아이콘 수정 검증 결과: Node 36개 통과, arm64 DMG 재생성 성공, 패키징 앱의 ad-hoc 서명/hardened runtime/실제 시작 검증 통과. 실제 `Contents/Resources/icon.icns`를 iconutil로 추출해 1024px 표현의 투명 모서리/외곽 여백 및 불투명 중앙을 확인했다. 디자인 미리보기와 로컬 DMG·SHA-256 갱신 완료. native Intel 재빌드 및 공개 Release 갱신은 수행하지 않았다.

## 13. 사용자 승인 릴리스 게시 (2026-10-13)
사용자가 정식 릴리스와 프리릴리스 생성을 요청했다. 원격 최신 배포가 0.1.8(초기 QR 연결)임을 확인했다. 디자인 변경을 먼저 독립 커밋하고 공개 태그 certificate-v0.1.8을 병합해 초기 QR 기능을 보존한다. 0.1.9-rc.1(dev)과 0.1.9(prod)를 별도 immutable 태그로 게시한다. 이전 태그/파일을 교체하지 않는다.
- 커밋 경계: 브랜드 DMG 및 macOS 아이콘 수정 → 최신 공개 기능 병합 → rc 버전 → 정식 버전.
- 검증: Node/실제 Electron smoke, Gradle clean build(별도 PostgreSQL), Release의 native Windows·ARM·Intel 검증, 6개 Assets/각 SHA-256 확인.
- 환경별 API는 기존 GitHub Environment 사용. 앱 ID 분리는 이번 변경 범위에 포함하지 않는다.
- 최신 QR 흐름의 Backend V50/#517 및 모바일 QR v2 연동 선행 배포 조건은 Release 안내에 유지한다.
