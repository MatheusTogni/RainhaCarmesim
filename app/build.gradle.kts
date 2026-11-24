plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Versão vinda do CI (derivada da tag vX.Y.Z). Valores padrão para builds locais.
val appVersionName: String = (project.findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode: Int = (project.findProperty("versionCode") as String?)?.toInt() ?: 10000

fun releaseKey(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.rainhacarmesim.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rainhacarmesim.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "UPDATE_REPO", "\"MatheusTogni/RainhaCarmesim\"")
        buildConfigField(
            "String",
            "UPDATE_METADATA_URL",
            "\"https://github.com/MatheusTogni/RainhaCarmesim/releases/latest/download/latest.json\""
        )
    }

    // Assinatura de release: a chave NUNCA fica no repositório.
    // No CI vem dos GitHub Secrets; localmente, de variáveis de ambiente ou ~/.gradle/gradle.properties
    // (RC_KEYSTORE_PATH, RC_KEYSTORE_PASSWORD, RC_KEY_ALIAS, RC_KEY_PASSWORD).
    // Sem elas, o release é assinado com a chave de debug (serve só para teste local).
    val releaseSigning = signingConfigs.create("release") {
        storeFile = releaseKey("RC_KEYSTORE_PATH")?.let { file(it) }
        storePassword = releaseKey("RC_KEYSTORE_PASSWORD")
        keyAlias = releaseKey("RC_KEY_ALIAS")
        keyPassword = releaseKey("RC_KEY_PASSWORD")
    }
    val hasReleaseKey = releaseSigning.storeFile?.exists() == true &&
        releaseSigning.storePassword != null && releaseSigning.keyAlias != null && releaseSigning.keyPassword != null
    if (!hasReleaseKey) {
        logger.warn("Rainha Carmesim: chave de release não configurada; o APK de release usará a chave de DEBUG.")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKey) releaseSigning else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
