plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace="ai.drivetalk.pleos"
    compileSdk=36
    defaultConfig { minSdk=28 }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
dependencies {
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
