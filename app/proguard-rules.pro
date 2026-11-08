# R8 rules for the release build. The app keeps its own models reflective-free
# except for enums read back from stored strings (Sport.valueOf in the service)
# and the routing engine, which GraphHopper wires by class name.

-keepclassmembers enum dev.toufik.trailmark.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
    java.lang.String name();
}

# Routing engine: GraphHopper resolves encoders, flag encoders and the router
# config from strings, so its classes must keep their names.
-keep class com.graphhopper.** { *; }
-dontwarn com.graphhopper.**

# Play services and billing surface classes through the manifest and the
# client libraries' own rules; keep the wearable message listener entry point.
-keep class dev.toufik.trailmark.wear.WearGpxListener { *; }

# MediaStore/Bitmap and camera are platform APIs; nothing to keep.
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# GraphHopper drags in its desktop XML stack (Jackson XML, XMLGraphics,
# StAX, AWT). The Android build never calls it, but R8 still resolves the
# references, so they are declared as optional.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.xml.stream.**
-dontwarn aQute.bnd.annotation.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.xmlgraphics.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.ctc.wstx.**
