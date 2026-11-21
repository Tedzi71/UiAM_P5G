# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# osmdroid rules (keep org.osmdroid classes for map rendering)
-keep class org.osmdroid.** { *; }

# Keep data models used in JSON/CSV export and database
-keep class uiam.p5g.data.model.** { *; }
