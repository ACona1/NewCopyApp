# نسخه جديد — v2.0

تطبيق أندرويد شخصي (`com.assem.copyapp`) هدفه تقليل «التعفن الدماغي»: السكرولينج، فتح السوشيال بدون وعي، والسهر. يعمل محليًا بالكامل: مفيش سيرفر، مفيش analytics، مفيش إعلانات، ومفيش أي طلب شبكة.

## الحالة الحالية
الإصدار 2.0.0 هو بداية خطة إعادة البناء. في هذه المرحلة (0) اتعمل التنضيف الأساسي:
- إزالة Premium وكود الخصم والحدود نهائيًا من الكود والوثائق.
- كل النصوص الظاهرة في `res/values/strings.xml` (عربي مصري) و`res/values-en/strings.xml` (إنجليزي)، مع دعم RTL.
- توقيع Release ثابت عبر GitHub Secrets (Keystore مش مخزّنة في الريبو).
- Gradle wrapper داخل الريبو + بناء `assembleRelease`.
- `allowBackup="false"` وإزالة Device Admin الفاضي وصلاحيات/flags غير مستخدمة.
- لوحة ألوان Material 3 الاحتياطية: `#0E1116` / `#161B22` / `#2DD4BF` / `#FBBF24` / `#F87171`.

## خطة المراحل
1. **المرحلة 1 — الجوهر**: allowlist ديناميكية، إزالة اعتراض المفاتيح، event-driven، ClockGuard، قواعد مشتقة (بما فيها overnight)، Soft/Hard، قفل الإعدادات أثناء الجلسة، إشعار chronometer.
2. **المرحلة 2 — البيانات**: Room + DataStore + EnforcementEngine + Profiles + Rules + تصدير/استيراد JSON.
3. **المرحلة 3 — الواجهة**: Compose + Material 3 + Bottom navigation + Onboarding + شاشة حظر جديدة + تحميل أيقونات على IO.
4. **المرحلة 4 — مكافحة التعفن**: Pause Gate، ميزانية يومية، حظر جزئي للريلز/شورتس + Inspector، عدّاد سكرول، حظر مواقع، بدائل، XP.
5. **المرحلة 5 — الذكاء والراحة**: Brain Rot Score، Streaks، Insights، Quick Tile/Widget/Shortcuts، Notification Digest، Wind-down/Grayscale، قواعد أول/آخر اليوم، Friend Key.
6. **المرحلة 6 — التلميع**: اختبارات unit، قياس أداء، تنظيف نهائي.

## البناء
البناء سحابي فقط عبر GitHub Actions في `ACona1/NewCopyApp`، بدون تثبيت Android SDK محليًا.

- ملف الـ workflow: `.github/workflows/build.yml`
- الأسرار المطلوبة (Already configured): `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`
- الناتج: artifact باسم `NewCopyApp-release` يحتوي `app-release.apk`

```bat
gh run list -R ACona1/NewCopyApp --workflow=build.yml --limit 1
gh run download <RUN_ID> -R ACona1/NewCopyApp
```

## التثبيت
```bat
adb install -r "C:\Users\DESKTOP\Documents\Default Project\NewCopyApp-release.apk"
```
بعد الفتح: **مراجعة الصلاحيات** → فعّل Accessibility وUsage Access والإشعارات وتجاهل تحسين البطارية.

## ملاحظات
- التوقيع ثابت، فالترقية فوق نسخة سابقة موقّعة بنفس المفتاح تحفظ البيانات. أول تثبيت من الإصدار 1.2 لازم `adb uninstall com.assem.copyapp` لأن مفتاحه كان بيتولّد كل مرة.
- النسخة الاحتياطية من الـ keystore والباسورد محفوظة محليًا برّه الريبو في `NewCopyApp-release.keystore` و`NewCopyApp-signing-info.txt`.
- الحماية محلية/workflow-practical وليست مضادة للتلاعب 100% ضد مستخدم عنده صلاحيات كاملة.
