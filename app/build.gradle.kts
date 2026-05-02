import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val soundAuraVersionCode = 20
val soundAuraVersionName = "3.2.3"
val privateDir = rootProject.projectDir.parentFile.resolve("SoundAura_private")
val privatePersonalResDir = privateDir.resolve("app/src/personal/res")
val privateArtifactsDir = privateDir.resolve("release_artifacts")
val personalReleaseKeystore = privateDir.resolve("soundaura-personal.jks")
val personalReleasePasswordFile = privateDir.resolve("soundaura-personal-password.txt")
val hasPersonalReleaseSigning =
    personalReleaseKeystore.isFile && personalReleasePasswordFile.isFile

fun personalReleaseSigningValue(label: String): String? =
    personalReleasePasswordFile
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstOrNull { it.startsWith("$label:") }
        ?.substringAfter(':')
        ?.trim()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
    id("com.mikepenz.aboutlibraries.plugin.android")
    id("com.google.devtools.ksp")
}

kotlin.compilerOptions.jvmTarget = JvmTarget.fromTarget("21")

android {
    namespace = "com.cliffracertech.soundaura"
    compileSdk = 36
    flavorDimensions += "edition"
    sourceSets {
        getByName("androidTest")
            .assets.srcDir("$projectDir/schemas")
        maybeCreate("personal").res.srcDir(privatePersonalResDir)
        maybeCreate("public")
    }

    defaultConfig {
        applicationId = "com.cliffracertech.soundaura"
        minSdk = 24
        targetSdk = 36
        versionCode = soundAuraVersionCode
        versionName = soundAuraVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    productFlavors {
        create("personal") {
            dimension = "edition"
        }
        create("public") {
            dimension = "edition"
        }
    }
    signingConfigs {
        create("personalRelease") {
            if (hasPersonalReleaseSigning) {
                storeFile = personalReleaseKeystore
                storePassword = personalReleaseSigningValue("Keystore password")
                keyAlias = personalReleaseSigningValue("Alias")
                keyPassword = personalReleaseSigningValue("Key password")
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            if (hasPersonalReleaseSigning)
                signingConfig = signingConfigs.getByName("personalRelease")
            proguardFiles(getDefaultProguardFile(
                "proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions.kotlinCompilerExtensionVersion = "1.5.15"

    packaging.resources.excludes += "/META-INF/*"

    testOptions.unitTests.isIncludeAndroidResources = true
}

gradle.taskGraph.whenReady {
    val needsPersonalFlavor = allTasks.any { it.name.contains("Personal", ignoreCase = true) }
    if (needsPersonalFlavor && !privatePersonalResDir.isDirectory) {
        throw GradleException(
            "Personal flavor resources are missing. Expected private assets under: " +
                privatePersonalResDir.absolutePath
        )
    }
}

class RoomSchemaArgProvider(
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File
): CommandLineArgumentProvider {
    override fun asArguments() = listOf("room.schemaLocation=${schemaDir.path}")
}
ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

val copyPersonalReleaseArtifacts by tasks.registering(Copy::class) {
    dependsOn("assemblePersonalRelease")
    from(layout.buildDirectory.dir("outputs/apk/personal/release"))
    include("*.apk", "*.idsig")
    into(privateArtifactsDir)
    rename { fileName ->
        when (fileName) {
            "app-personal-release.apk" ->
                "Mistysound-$soundAuraVersionName-personal-release.apk"
            "app-personal-release.apk.idsig" ->
                "Mistysound-$soundAuraVersionName-personal-release.apk.idsig"
            "app-personal-release-unsigned.apk" ->
                "Mistysound-$soundAuraVersionName-personal-release-unsigned.apk"
            else -> fileName
        }
    }
}

val copyPublicReleaseArtifacts by tasks.registering(Copy::class) {
    dependsOn("assemblePublicRelease")
    from(layout.buildDirectory.dir("outputs/apk/public/release"))
    include("*.apk", "*.idsig")
    into(privateArtifactsDir)
    rename { fileName ->
        when (fileName) {
            "app-public-release.apk" ->
                "Mistysound-$soundAuraVersionName-public-release.apk"
            "app-public-release.apk.idsig" ->
                "Mistysound-$soundAuraVersionName-public-release.apk.idsig"
            "app-public-release-unsigned.apk" ->
                "Mistysound-$soundAuraVersionName-public-release-unsigned.apk"
            else -> fileName
        }
    }
}

tasks.register("copyDualReleaseArtifacts") {
    dependsOn(copyPersonalReleaseArtifacts, copyPublicReleaseArtifacts)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.optIn.addAll(
        "androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("androidx.compose.material:material:1.9.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.4")
    implementation("androidx.compose.animation:animation:1.9.4")
    implementation("androidx.compose.animation:animation-graphics:1.9.4")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("com.google.accompanist:accompanist-insets:0.30.1")
    implementation("com.google.accompanist:accompanist-insets-ui:0.36.0")
    implementation("androidx.media:media:1.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.dagger:hilt-android:2.57.2")
    implementation("com.mikepenz:aboutlibraries-core:13.1.0")
    implementation("com.mikepenz:aboutlibraries-compose:13.1.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("sh.calvin.reorderable:reorderable:3.0.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    ksp("androidx.room:room-compiler:2.8.3")
    kapt("com.google.dagger:hilt-compiler:2.57.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:truth:1.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.9.4")
    androidTestImplementation("androidx.test.ext:truth:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.room:room-testing:2.8.3")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.57.2")
}
