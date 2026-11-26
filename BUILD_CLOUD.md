# البناء في السحابة بدون Android SDK محلي

لا تحتاج تثبّت Android Studio ولا Gradle على جهازك.

## الخطوات
1. أنشئ مستودع جديد على GitHub (مثلاً `NewCopyApp`).
2. ارفع كل ملفات مجلد `NewCopyApp` إليه (أو استخدم `NewCopyApp.zip` واسحبه إلى صفحة الـ repository).
3. افتح تبويب **Actions**، واختار **Build NewCopyApp APK**، ثم اضغط **Run workflow**.
4. بعد ما الـ build يخلص، اضغط على الـ artifact اسمه `NewCopyApp-debug` ونزّل `app-debug.apk`.
5. ثبّت الـ APK على جوالك وفعّل الصلاحيات وكود الخصم `gdNeooYe8`.

## ملاحظات
- الـ workflow بيستخدم JDK 17 + Android platform 34 + Gradle 8.7.
- أول build بياخد حوالي 5–10 دقائق؛ اللي بعده أسرع بسبب الـ cache.
- الـ artifact بيفضل متاح حوالي 90 يوم.
- لو حابب تبني نسخة Release بدل Debug، غيّر السطر:
  `gradle assembleDebug` إلى `gradle assembleRelease`.

## بديل أخف لو حابب تبني محليًا
تقدر تكتفي بـ Android command-line tools بدل Android Studio:
- Android cmdline-tools ~150 MB
- platform android-34 + build-tools ~150 MB
- Gradle ~150 MB
يعني حوالي 500 MB بدل 6–8 GB، بس لسه محتاج JDK 17.
