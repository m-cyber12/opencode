import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.opencode.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.opencode.android"
        // minSdk 26: Android 8.0+. arm64-v8a only (see versions.lock / docs/RUNTIME.md).
        minSdk = 26
        // targetSdk 28 is a deliberate, security-reviewed choice required by the
        // embedded execution layer: SELinux grants the untrusted_app_27 domain
        // execute_no_trans on app_data_file, which lets PRoot exec guest binaries
        // extracted into app-private storage. Raising targetSdk >= 29 removes that
        // grant and breaks the runtime (documented in docs/SECURITY.md and
        // docs/RUNTIME.md). Distribution channel for v1 is direct APK install.
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Executables shipped as jniLibs MUST be extracted to disk as real files,
    // otherwise nativeLibraryDir does not contain executable binaries.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes.add("META-INF/LICENSE*")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(composeBom)
}

/**
 * Runtime bundle wiring.
 *
 * The Linux userspace (Alpine rootfs + pinned OpenCode binary) is produced by
 * scripts/build-runtime.sh and consumed here:
 *
 *   -Popencode.runtime.file=/abs/path/rootfs.tar.gz
 *       Copies the given bundle into src/main/assets/runtime/rootfs.tar.gz before build.
 *
 *   The task `verifyRuntimeAsset` fails the build unless a real, non-empty bundle with
 *   a matching .sha256 sidecar exists. This prevents shipping an APK that silently
 *   lacks its runtime (spec: no green-build lies).
 */
val runtimeSrc = layout.projectDirectory.dir("src/main/assets/runtime")
val providedRuntime = providers.gradleProperty("opencode.runtime.file").orNull

tasks.register<Copy>("importRuntimeBundle") {
    onlyIf { providedRuntime != null }
    doFirst {
        val f = File(providedRuntime!!)
        require(f.isFile && f.length() > 0) { "opencode.runtime.file does not exist or is empty: $f" }
    }
    from(providedRuntime ?: "")
    into(runtimeSrc)
    rename { "rootfs.tar.gz" }
}

tasks.register("verifyRuntimeAsset") {
    group = "verification"
    description = "Fails unless the bundled runtime archive and checksum are present."
    doLast {
        val dir = runtimeSrc.asFile
        val tar = File(dir, "rootfs.tar.gz")
        val sha = File(dir, "rootfs.sha256")
        if (!tar.isFile || tar.length() < 1_000_000) {
            throw GradleException(
                "Runtime bundle missing or implausibly small (${if (tar.exists()) tar.length() else 0} bytes) at $tar.\n" +
                "Build it with scripts/build-runtime.sh (CI job 'runtime') and pass:\n" +
                "  ./gradlew assembleDebug -Popencode.runtime.file=<path>/rootfs.tar.gz\n" +
                "A placeholder is NOT allowed: this app must embed a real OpenCode runtime."
            )
        }
        if (!sha.isFile || sha.length() == 0L) {
            throw GradleException("Missing $sha sidecar produced alongside rootfs.tar.gz.")
        }
        val md = MessageDigest.getInstance("SHA-256")
        tar.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            var n: Int
            while (input.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        val expected = sha.readText().trim().split(Regex("\\s+")).first()
        if (!actual.equals(expected, ignoreCase = true)) {
            throw GradleException(
                "Runtime bundle checksum mismatch!\n expected=$expected\n actual  =$actual"
            )
        }
        logger.lifecycle("verifyRuntimeAsset OK: ${tar.name} ${tar.length()} bytes sha256=$actual")
    }
}

tasks.named("preBuild") {
    dependsOn("importRuntimeBundle", "verifyRuntimeAsset")
}
