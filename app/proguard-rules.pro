# Proguard rules for Yamaha MX Editor (MX Control)

# Preservar classes de modelo MIDI, repositórios e serialização
-keep class com.yamahamx.editor.data.** { *; }
-keepclassmembers class com.yamahamx.editor.data.** { *; }

# Preservar atributos essenciais para reflexão, tipos genéricos e anotações
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Preservar callbacks e listeners do Android MIDI
-keepclassmembers class * implements android.media.midi.MidiManager$OnDeviceOpenedListener { *; }
-keepclassmembers class * extends android.media.midi.MidiReceiver { *; }

# Preservar classes de ViewModels e modelos de UI
-keep class com.yamahamx.editor.ui.viewmodel.** { *; }

# Preservar Google Play Billing & In-App Updates
-keep class com.android.billingclient.api.** { *; }
-keep class com.google.android.play.core.** { *; }

# Preservar Kotlin Coroutines e Flow
-keepnames class kotlinx.coroutines.** { *; }
