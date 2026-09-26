plugins {
    id("com.android.application") version "9.1.0" apply false
    // Declaring KGP here sets the Kotlin version AGP's built-in Kotlin support uses.
    // LiteRT-LM is compiled with Kotlin 2.4, so the compiler must be at least that.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}
