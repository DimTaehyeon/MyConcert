# 버전 관리

- **1.0.0 / 태그 `1.0.0v`**: 3차 QA 및 추가 승인 2건까지 완료한 기준 버전. 기존 내부 QA 표기 1.3.1을 공식 버전 1.0.0으로 정리했습니다. 설치 업데이트 호환성을 위해 Android versionCode 5는 유지합니다.
- 이후 프로필 사진 변경은 `feature/artist-profile-photos` 브랜치에서 진행합니다.
- API 키와 PC 설정(`local.properties`), 빌드 결과·APK는 저장소에서 제외합니다. APK는 로컬 `output` 폴더에 보관합니다.
- 원격 저장소는 연결하지 않았습니다. 현재 PC의 로컬 Git에서 이력을 관리합니다.

기준 버전 확인: `git show 1.0.0v:app/build.gradle.kts`
