# Preserve instrumentation-facing app APIs while optimizing app and dependency code.
-keep,allowoptimization class app.stak.nativeapp.** { *; }
