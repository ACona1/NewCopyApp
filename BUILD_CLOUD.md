# البناء في السحابة بدون Android SDK محلي

لا تحتاج تثبّت Android Studio ولا Gradle على جهازك. البناء كله بيحصل في GitHub Actions.

## الخطوات
1. المستودع: `ACona1/NewCopyApp` (أو استبدله بمستودعك الخاص).
2. افتح تبويب **Actions**، واختار **Build NewCopyApp APK**، ثم اضغط **Run workflow** (أو ادفع أي commit على `main`).
3. الـ workflow يثبّت JDK 17 + Android 35 + Gradle 8.7 عبر الـ wrapper، ويفكّ الـ keystore من Secrets ويبني `assembleRelease`.
4. بعد ما الـ build يخلص، نزّل artifact `NewCopyApp-release` واستخرج `app-release.apk`.
5. ثبّت على الجوال:
   ```bat
   adb install -r "C:\Users\DESKTOP\Documents\Default Project\NewCopyApp-release.apk"
   ```
6. فعّل الصلاحيات من داخل التطبيق: Accessibility، Usage Access، الإشعارات، تجاهل تحسين البطارية.

## الأسرار المطلوبة في GitHub Secrets
| الاسم | الوصف |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | Keystore بصيغة base64 |
| `SIGNING_STORE_PASSWORD` | كلمة مرور المتجر |
| `SIGNING_KEY_ALIAS` | الاسم المستعار للمفتاح |
| `SIGNING_KEY_PASSWORD` | كلمة مرور المفتاح |

> لو غيّرت المستودع، انسخ الأسرار أو أنشئ keystore جديدة واحفظ نسخة منها برّه الريبو.

## ملاحظات
- الـ wrapper موجود في الريبو (`gradlew`, `gradlew.bat`, `gradle/wrapper/`)، فمفيش حاجة لتحديد إصدار Gradle في الـ workflow.
- التوقيع ثابت، فالتحديث فوق نسخة سابقة بنفس المفتاح بيحفظ البيانات.
- لو جاي من الإصدار 1.2 (توقيع متغيّر كل بناء)، اعمل `adb uninstall com.assem.copyapp` قبل التثبيت.
- أول build بياخد حوالي 5–10 دقائق؛ اللي بعده أسرع بسبب الـ cache.
