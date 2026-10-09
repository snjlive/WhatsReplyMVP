plugins { id("com.android.application") }
android {
    namespace = "com.snjlive.whatsreply"
    compileSdk = 36
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            if (project.hasProperty("testSdks")) it.systemProperty("robolectric.enabledSdks",project.property("testSdks").toString())
        }
    }
    defaultConfig {
        applicationId = "com.snjlive.whatsreply"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
