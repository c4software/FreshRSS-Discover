# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ML Kit s'initialise par réflexion : `MlKitInitProvider` instancie chaque
# `ComponentRegistrar` par son constructeur sans argument. La règle fournie par
# firebase-components 16.1.0 (tirée par mlkit:common) ne garde que la classe ;
# en mode complet, R8 n'en conserve plus implicitement le constructeur, et
# `CommonComponentRegistrar.<init>()` disparaît du build release. Conséquence :
# `checkStatus()` lève, et le récap se déclare indisponible partout.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
