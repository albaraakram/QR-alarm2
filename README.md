# QR-alarm

منبّه أندرويد لا يتوقف إلا بمسح أي رمز QR عبر الكاميرا.

- يحدد المستخدم وقت المنبّه ويختار ملف صوت من الجهاز.
- عند الرنين: شاشة ملء الشاشة + ماسح QR تلقائي.
- أي QR صالح يوقف الجرس فورًا (لا حاجة لتسجيل رمز مسبقًا).

## البناء السريع (Android Studio)
- افتح المشروع ثم Build > Build APK(s).
- ملف الـAPK: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions
كل دفع إلى الفرع `main` يبني APK تلقائيًا ويرفعه كـ Artifact.
- الملف: `.github/workflows/build-apk.yml`.

## الأذونات
- الكاميرا لمسح QR.
- التنبيهات الدقيقة (Android 12+).
- الإشعارات (Android 13+).

