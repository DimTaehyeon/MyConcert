# 아티스트 검색어 확인 현황

2026-11-11 작업 기준. 실제 KOPIS 표본에 앱과 동일한 매칭 규칙을 적용한 결과입니다.

- 사전 등록: 48팀 / 현재 표기 99개
- 표본 일치: 20팀
- 조사 기간: 2025년 11월, 2026년 8월, 2026년 11월
- 원본: rtist-verification.json (288개 요청, 107개 상세 표본)
- 미확인은 공연 부재를 의미하지 않습니다. 조사 기간 밖 공연과 미등록 공연은 확인할 수 없습니다.

| 아티스트 | 검색 표기 | 표본에서 일치 확인한 검색어 |
|---|---|---|
| 아이브 | 아이브, IVE | 표본 미확인 |
| NMIXX | NMIXX, 엔믹스 | NMIXX |
| 아이유 | 아이유, IU, 이지은 | 표본 미확인 |
| DAY6 | DAY6, 데이식스 | 표본 미확인 |
| aespa | aespa, 에스파 | aespa |
| BTS | BTS, 방탄소년단 | 표본 미확인 |
| BLACKPINK | BLACKPINK, 블랙핑크 | 표본 미확인 |
| TWICE | TWICE, 트와이스 | 표본 미확인 |
| Stray Kids | Stray Kids, 스트레이 키즈 | Stray Kids |
| SEVENTEEN | SEVENTEEN, 세븐틴 | 표본 미확인 |
| TOMORROW X TOGETHER | TOMORROW X TOGETHER, 투모로우바이투게더, TXT | TOMORROW X TOGETHER |
| ENHYPEN | ENHYPEN, 엔하이픈 | ENHYPEN |
| LE SSERAFIM | LE SSERAFIM, 르세라핌 | 표본 미확인 |
| NewJeans | NewJeans, 뉴진스 | 표본 미확인 |
| ITZY | ITZY, 있지 | 표본 미확인 |
| i-dle | (여자)아이들, 여자아이들, 아이들, (G)I-DLE, G I-DLE, i-dle | 표본 미확인 |
| Red Velvet | Red Velvet, 레드벨벳 | Red Velvet |
| NCT DREAM | NCT DREAM, 엔시티 드림 | 표본 미확인 |
| NCT 127 | NCT 127, 엔시티 127 | 표본 미확인 |
| SHINee | SHINee, 샤이니 | 표본 미확인 |
| EXO | EXO, 엑소 | EXO |
| SUPER JUNIOR | SUPER JUNIOR, 슈퍼주니어 | 표본 미확인 |
| RIIZE | RIIZE, 라이즈 | 표본 미확인 |
| BOYNEXTDOOR | BOYNEXTDOOR, 보이넥스트도어 | BOYNEXTDOOR |
| TWS | TWS, 투어스 | 표본 미확인 |
| IZ*ONE | IZ*ONE, 아이즈원 | 표본 미확인 |
| AKMU | AKMU, 악뮤, 악동뮤지션 | AKMU |
| 볼빨간사춘기 | 볼빨간사춘기, BOL4 | BOL4 |
| 윤하 | 윤하, YOUNHA | 표본 미확인 |
| 태연 | 태연, TAEYEON | 표본 미확인 |
| 태민 | 태민, TAEMIN | 표본 미확인 |
| 제이홉 | 제이홉, j-hope | 표본 미확인 |
| 진 | JIN, 진, 김석진 | JIN |
| G-DRAGON | G-DRAGON, 지드래곤 | 표본 미확인 |
| 백현 | 백현, BAEKHYUN | 표본 미확인 |
| 임영웅 | 임영웅 | 임영웅 |
| 영탁 | 영탁, YOUNGTAK | 영탁 |
| 이찬원 | 이찬원 | 표본 미확인 |
| 잔나비 | 잔나비, JANNABI | 잔나비 |
| LUCY | LUCY, 루시 | LUCY |
| Xdinary Heroes | Xdinary Heroes, 엑스디너리 히어로즈 | Xdinary Heroes |
| NELL | NELL, 넬 | NELL |
| 10CM | 10CM, 십센치 | 10CM |
| 성시경 | 성시경 | 표본 미확인 |
| 박효신 | 박효신 | 박효신 |
| 김동률 | 김동률 | 김동률 |
| Crush | Crush, 크러쉬 | 표본 미확인 |
| 헤이즈 | 헤이즈, Heize | 표본 미확인 |


## 확인한 오탐과 처리

- IVE 검색에 LIVE 공연 다수 반환: 단어 경계에서 불일치하면 상세 조회 전에 제외.
- JIN 검색에 KWON JIN AH 공연 반환: 출연진에서 김석진 등 아티스트 신원이 확인되어야 확정.
- 아이들 검색에 연극·어린이 공연 반환: 장르와 공식 표기·출연진을 확인하고 모호하면 제외.
- NMIXX 출연진이 박진·오해원·설윤아·배진솔·김지우·장규진으로 기재: 예명과 실명의 동일인 묶음을 등록.
- 그룹 멤버 한 명의 개인 팬미팅: 그룹명이나 소속 표기가 있어도 그룹 출연으로 확정하지 않음.

## 기기 검증 상태

앱 빌드와 23개 테스트, Lint 오류 0건을 확인했습니다. 가상 기기 부팅 문제로 화면 테스트 실행 및 스크린샷 비교는 미완료입니다.
