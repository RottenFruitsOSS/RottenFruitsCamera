# ML Kit Vision on-device 체리 부패 인식 안드로이드 앱 

RottenCherryS 프로젝트는 체리의 부패 정도를 딥러닝의 이미지 분류를 이용하여 문제를 해결하고자 합니다. 데이터 수집, 모델 빌드 및 학습 그리고 실생활에 활용의 단계의 3 단계 중, 이 repository 에서는 마지막 단계인 활용 즉, 안드로이드 모바일 앱 작성에 대한 것입니다.

딥러닝 모델이 생성된 후에 이를 실생활에 적용하는 많은 방법들이 있습니다. RottenCherryS 에서는  on-device 머신러닝을 지원하는 Google ML Kit APIs 를 활용하여 체리 분류 커스텀 모델을 라이브 카메라 스트림으로 실행되고 사용자에 친숙한 직관적인 상태 판별 정보를 UI 형태로 결과를 제공합니다.

## 개발언어
- Kotlin : 안드로이드 모바일 앱 개발 언어
- Tensorflow Lite : 모바일 및 임베디드 기기에 모델을 배포하기 위한 라이브러리

## 개발 환경 및 기술 
Android 플랫폼 : minSdkVersion 19, targetSdkVersion 29 
ML Kit APIs : on-device 머신러닝을 위한 모바일 SDK

## 주요 기능
- 빠른 오브젝트 탐지 및 추적 : 카메라 라이브 스트림에서 오브젝트를 탐지하고 연속적으로 추적
- on-device 모델 : 딥러닝 모델이 앱의 Asset에 포함되어 클라우드가 아닌 offline inference 
- 커스텀 모델의 적용 : 보다 전문적인 분류를 위해 사용자 정의 모델을 사용
  - TensorFlow Lite Model Maker 로 학습
  - TensorFlow 로 학습 후, TensorFlow Lite 로 Convert 후 Metadata 추가
- 분류 결과를 사용자 편의성 UI제공 : 양호, 주의, 경고와 같이 3단계의 부패 단계를 AlertDialog 를 통해 제공

## 커스텀 모델 적용 Flow
### Metadata 가 포함된 tflite 학습 모델을 생성
  - Model Maker 로 학습할 경우 metadata 가 포함된 tflite 모델이 생성됨
  - TensorFlow Lite converter로 생성한 tflite의 경우, MetaData를 포함시켜야 함 
    - Adding MetaData 참조 :   https://github.com/peltraw/tflite-add-metadata

### app/build.gradle 에 ML Kit Android library 를 포함 
```
dependencies {
  // ...
  // Object detection and tracking feature with custom classifier
  implementation 'com.google.mlkit:object-detection-custom:16.0.0'
}
```

### Assets/custom_models/ 하위 폴더에 custom model file 포함
 - 빌드 시에 모델 파일은 압축되지 않도록 gradle 수정
```
android {
    // ...
    aaptOptions {
        noCompress "tflite"  // Your model's file extension: "tflite", "lite", etc.
    }
}
```
### 로컬 모델의 정의 : ProminentObjectProcessor.kt
```
val localModel = LocalModel.Builder()
   .setAssetFilePath(customModelPath) // custom model path 지정
   .build()
options = CustomObjectDetectorOptions.Builder(localModel)
   .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
   .enableClassification() // Always enable classification for custom models
   .build()
```

### DetectedObject : 레이블 정보에 대한 정보
```
Bounding box
Tracking ID
Labels
  Label description
  Label index
  Label confidence
```
- label.text : TensorFlow Lite 모델의 label description 값으로 normal, spoiled_early, spoiled_advanced 로 구성됨
- label.confidence : 객체 분류의 신뢰도 값 (~ 1.00)

### 탐지된 객체의 label 정보를 CustomDialog 로 정의

