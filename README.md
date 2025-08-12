# TMR (Transcranial Magnetic Resonance) Android Application

## 개요
이 Android 애플리케이션은 TMR 연구를 위한 2D 필기 데이터 수집 도구입니다. 사용자가 화면에 글자를 쓰는 동안 100Hz로 위치 데이터를 수집하고, CSV 파일로 저장합니다.

## 주요 기능

### 1. 세션 관리
- **Practice Session**: 3분간 연습 세션 (데이터는 `practice_` 접두사로 저장)
- **Experiment Session**: 5분 또는 100회 시도까지 실험 세션
- **Subject ID**: 각 실험 참가자별 고유 식별자

### 2. 필기 인식 및 데이터 수집
- **100Hz 샘플링**: 10ms마다 위치 데이터 수집
- **중앙 사각형 영역**: 화면 중앙에 최대한 큰 필기 영역 제공
- **터치 기반 트리거**: 화면 터치로 필기 시작/종료

### 3. 시도(Trial) 감지 시스템
- **상태 기계**: IDLE → WRITING → COOLDOWN → IDLE
- **0.5초 쿨다운**: 필기 중단 후 0.5초 대기
- **시도 번호 관리**: 
  - 쿨다운 완료 시 trial=0으로 기록
  - 쿨다운 중 재개 시 기존 trial 번호 유지

## 프로젝트 구조

### 핵심 파일들

#### 1. MainActivity.kt
- 애플리케이션의 진입점
- AppViewModel을 ViewModel로 관리
- TMRTheme 적용

#### 2. AppViewModel.kt
- 전체 애플리케이션 상태 관리
- 세션 제어 및 네비게이션
- 100Hz 데이터 샘플링
- CSV 로깅 및 파일 저장
- 시도 감지 로직

#### 3. AppScreens.kt
- **StartScreen**: Subject ID 입력 및 세션 선택
- **PracticeScreen**: 3분 연습 세션 (자동 종료 후 오버레이)
- **ExperimentScreen**: 실험 세션 (자동 종료 조건 감지)
- **EndScreen**: 실험 결과 요약
- **WritingCanvas**: 필기 캔버스 및 터치 처리

### 데이터 구조

#### CSV 파일 형식
```
timestamp,subject_id,writing,trial,x,y
0,S012,0,0,,
10,S012,1,1,150.5,200.3
20,S012,1,1,155.2,205.1
...
```

- **timestamp**: 세션 시작부터의 밀리초
- **subject_id**: 실험 참가자 ID
- **writing**: 필기 상태 (1: 필기 중, 0: 대기)
- **trial**: 시도 번호 (0: IDLE, 1~: 시도 번호)
- **x, y**: 터치 좌표 (필기 중일 때만)

#### 파일 저장 위치
- **경로**: `Android/data/com.example.tmr/files/TMR_watch/`
- **파일명**: `TMR_watch_<SubjectID>_<yyyyMMdd-HHmm>.csv`
- **연습 파일**: `practice_TMR_watch_<SubjectID>_<yyyyMMdd-HHmm>.csv`

## 사용법

### 1. 실험 시작
1. Subject ID 입력
2. "Practice" 또는 "Start Experiment" 버튼 선택

### 2. Practice Session
- 3분간 자유롭게 필기
- 시간 종료 시 오버레이 표시
- "Start Experiment" 버튼으로 실험 세션 진입

### 3. Experiment Session
- 5분 또는 100회 시도까지 자동 진행
- 조건 달성 시 자동 종료 및 데이터 저장

### 4. 데이터 확인
- Toast 메시지로 저장 경로 표시
- Android Studio Device File Explorer에서 파일 확인

## 기술적 특징

### 1. 상태 관리
- **Compose State**: MutableState를 활용한 반응형 UI
- **ViewModel**: 화면 회전 등에도 안정적인 상태 유지
- **Coroutines**: 백그라운드에서 100Hz 샘플링

### 2. 터치 처리
- **Gesture Detection**: Compose의 pointerInput 활용
- **Path Drawing**: Android Path와 Compose Path 연동
- **영역 제한**: 중앙 사각형 내에서만 필기 인식

### 3. 성능 최적화
- **100Hz 샘플링**: 10ms 간격으로 효율적인 데이터 수집
- **Path 버전 관리**: 불필요한 재그리기 방지
- **쿨다운 버퍼링**: 메모리 효율적인 데이터 처리

## 개발 환경

- **언어**: Kotlin
- **UI 프레임워크**: Jetpack Compose
- **아키텍처**: MVVM (Model-View-ViewModel)
- **최소 SDK**: API 24 (Android 7.0)
- **타겟 SDK**: API 35 (Android 15)

## 의존성

```kotlin
implementation(libs.androidx.core.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
implementation(libs.androidx.activity.compose)
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.ui)
implementation(libs.androidx.material3)
```

## 빌드 및 실행

1. Android Studio에서 프로젝트 열기
2. Gradle 동기화 완료 대기
3. Android 기기 또는 에뮬레이터 연결
4. Run 버튼으로 앱 실행

## 데이터 분석

수집된 CSV 데이터는 다음과 같은 분석에 활용할 수 있습니다:
- 필기 속도 및 정확도 분석
- 시도별 패턴 분석
- 시간대별 성능 변화 추적
- 참가자별 개인차 분석

## 주의사항

- 외부 저장소 권한 필요
- 100Hz 샘플링으로 인한 배터리 소모
- 대용량 데이터 수집 시 저장 공간 확인 필요
- 실험 중 앱 종료 시 데이터 손실 가능성

## 라이선스

이 프로젝트는 연구 목적으로 개발되었습니다.
