# 해커톤 MVP 단계라 minify 미사용. release 시 활성화 대비 기본 규칙만 둠.
# kotlinx.serialization 클래스 보호 (실제로는 plugin 이 자동 처리하지만 안전망)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
