# Gradle Wrapper 8.10.2

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are from:
https://github.com/gradle/gradle/tree/v8.10.2

The Windows script uses CRLF line endings. Upstream license is included here.
The wrapper JAR SHA-256 was checked against Gradle's official checksum reference:
https://gradle.org/release-checksums/

JAR: 2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046
Binary distribution: 31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26

Distribution checksum is pinned in gradle-wrapper.properties. Gradle downloads
its distribution to the user's Gradle cache on first use. A system Gradle
installation is not needed. JDK and Android SDK are still required to build Android.
