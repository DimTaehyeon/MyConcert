# 화면 XML 구조

Android Studio에서 `app/src/main/res/layout`의 파일을 열고 Code / Split / Design으로 화면 구성을 확인합니다. 데이터가 들어가는 텍스트에는 `tools:text` 미리보기 예시가 있습니다. 실행할 때는 실제 조회 결과가 들어갑니다.

## 화면별 파일

| 파일 | 화면·역할 | Kotlin에서 연결하는 동작 |
|---|---|---|
| `activity_main.xml` | 공통 스크롤·화면 삽입 영역·하단 크레딧·맨 위로·선택 완료·스플래시 덮개 | 스크롤, 시스템 영역·키보드 여백, GitHub 링크 |
| `screen_splash.xml` | 시작 로고 | 초기 로딩 후 숨김 |
| `screen_home.xml` | 내 아티스트 홈 | 선호 목록·예정/이전 공연 데이터 연결 |
| `screen_preferences.xml` | 처음 선택·선호 아티스트 편집 | 필터, 체크 상태, 최대 8팀 저장, 완료 |
| `screen_search.xml` | 아티스트 검색 | 이름 입력·직접 검색·최근 검색·등록 목록 |
| `screen_artist.xml` | 특정 아티스트 공연 목록 | 프로필·하트·삭제·검색명·기간·축제 조회 |
| `screen_list.xml` | 전체 예정 공연·이전 공연 목록 | 조회 기간·더 보기·새로고침 |
| `screen_region.xml` | 지역별 공연 | 지도 선택·위치 권한·지역·기간 |
| `screen_detail.xml` | 공연 상세 화면 틀·공유 버튼 | 상세 로딩·오류·결과 연결 |
| `view_concert_detail.xml` | 상세 본문·장르·타이틀·기간·메타정보·캘린더·예매처 | 상세 데이터·저장 안내 표시·외부 앱 연결 |

화면의 배치·텍스트 스타일·고정 간격은 XML에 둡니다. `MainActivity.kt`는 `setContentView(R.layout.activity_main)`을 한 번 호출하고 각 화면을 공통 화면 틀의 `screen_host`에 불러옵니다. 화면 전환마다 공통 틀을 교체하지 않아 시스템 버튼/상태바 여백이 유지됩니다.

## 재사용 요소

- `view_toolbar.xml`, `view_top_header.xml`, `view_tabs.xml`: 상단·탭
- `view_search_input.xml`, `view_search_entry.xml`: 검색 입력·검색 화면 진입
- `view_search_landing.xml`, `view_search_matches.xml`: 검색 전·검색 결과 구성
- `item_artist_tile.xml`, `item_artist_row.xml`, `item_favorite_artist.xml`, `item_recent_artist.xml`: 아티스트 표시
- `view_artist_avatar.xml`: 원격 사진·배경·대체 이니셜의 층 구성
- `item_concert.xml`, `view_concert_list.xml`: 공연 카드·더 보기 결과가 들어가는 내부 목록
- `view_loading.xml`, `view_info.xml`, `view_result_meta.xml`, `view_result_footer.xml`: 로딩·안내·건수·조회 출처·새로고침
- `view_project_credits.xml`: 공통 크레딧과 GitHub 링크
- `dialog_search_aliases.xml`: 검색명 편집 입력
- `item_region_pin.xml`: 지도 지역 버튼

`values/styles.xml`은 공통 글꼴·버튼 스타일, `values/colors.xml`과 `values-night/colors.xml`은 라이트/다크 색상, `drawable/bg_mc_*.xml`과 `color/`는 배경·선택 상태를 관리합니다. 정적인 UI 문구는 `values/ui_strings.xml`, 기존 크레딧은 `values/strings.xml`에 있습니다.

지도 좌표 계산·연결선 그리기, 타일의 폭·글꼴 크기에 따른 열 수·높이 계산은 커스텀 View의 Kotlin 코드에 남습니다. 실제 내용과 화면 크기에 따라 계산하는 동작이며, 버튼·이름·사진 등의 모양과 구성은 XML에서 편집합니다.

## 상세 수정 위치

- `view_concert_detail.xml`의 `stale_notice`: 아래 여백 `12dp`. 공연 타이틀과 기간 사이의 간격과 같습니다. 최신 상세일 때는 `GONE`이므로 빈 여백도 생기지 않습니다.
- 아티스트 매칭 결과 안내박스는 상세 XML에서 제거했습니다. 공연 정보 판별에 사용하는 데이터·매칭 규칙은 유지합니다.

## 검증

검증 범위·기기·실행 결과는 [1.3.0 QA 기록](QA_1.3.0.md)에 정리했습니다. API·검색·저장 계층은 수정하지 않았습니다.
