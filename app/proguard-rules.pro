# Keep rules are added only when R8 is proven to break something (T17).

# youtubedl-android unzips its Python runtime with commons-compress, whose ExtraFieldUtils instantiates every
# ZipExtraField by reflection. R8 stripped those constructors: "class mf is not a concrete class" from
# ExtraFieldUtils.<clinit>, so YoutubeDL.init failed and no download could run (release only; API 30 AVD, 2026-10-08).
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField { <init>(); }
