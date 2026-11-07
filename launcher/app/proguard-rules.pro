# The launcher is a single Activity plus one custom View: no reflection, no serialization.
# Keep the entry point that the firmware starts, everything else can be shrunk.
-keep class com.gathin.tvhome.MainActivity { *; }
-keep class com.gathin.tvhome.CarouselView { *; }
