# S26 Ultra • 5G / 4.5G (LTE) Hızlı Geçiş Widget & Otomasyon Uygulaması

Samsung Galaxy S26 Ultra (One UI 7 / One UI 8, Android 15+) cihazlarda **çift SIM (SIM 1 / SIM 2)** desteğiyle **5G** ve **4.5G (LTE)** şebeke modları arasında ana ekrandaki widget üzerinden hızlıca geçiş yapmayı sağlayan Android uygulaması.

## Özellikler

1. **İki Farklı Ana Ekran Widget'ı**
   - **Geniş Kontrol Paneli Widget'ı (`4x1`)**: Üzerinde `OTO (Aktif Veri)`, `SIM 1`, `SIM 2` seçicisi ile doğrudan `5G`, `4.5G (LTE)`, `⇄ DEĞİŞTİR` ve `⚙ Ayarlar` butonları bulunur.
   - **Kompakt Hızlı Geçiş Widget'ı (`2x1`)**: Sol kısımdan hedef SIM (`OTO` / `SIM 1` / `SIM 2`) seçilir, sağ kısımdan tek dokunuşla `5G ⇄ 4.5G` geçişi yapılır.
2. **Aktif Mobil Veri Hattını Otomatik Algılama (`OTO` Modu)**
   - `SubscriptionManager.getDefaultDataSubscriptionId()` ile o an mobil verinin hangi SIM kartta aktif olduğunu otomatik tespit eder ve şebeke değişimini o SIM kart üzerinde uygular.
3. **Erişilebilirlik Servisi ile PC / Root Gerektirmeden Otomatik Seçim**
   - Widget'taki butona bastığınızda ilgili SIM'in Mobil Ağlar menüsünü açar, `Şebeke modu` satırına ve ardından `5G/LTE/3G/2G` veya `LTE/3G/2G (4.5G)` seçeneğine otomatik tıklayıp ana ekrana geri döner.
   - Otomasyon yalnızca siz widget veya uygulama içi geçiş butonuna bastıktan sonraki **8.5 saniye** boyunca aktiftir; normal zamanda Ayarlar menüsüne girdiğinizde hiçbir müdahalede bulunmaz.

## Kurulum ve S26 Ultra Ayarları

1. Derlenen APK dosyasını (`app/build/outputs/apk/debug/app-debug.apk`) telefonunuza kurun.
2. Uygulamayı açıp **1. Çift SIM & Aktif Veri Algılama İzni** (`READ_PHONE_STATE`) butonuna dokunarak izin verin.
3. **2. Otomatik 5G/4.5G Seçim Servisi** için:
   - Android 15/16 dışarıdan yüklenen APK'larda Erişilebilirlik servisini ilk başta "Kısıtlanmış ayar" olarak kilitleyebilir.
   - Uygulama içindeki **"Uygulama Bilgilerini Aç"** butonuna dokunun -> Sağ üstteki **⋮ (3 nokta)** menüsünden **"Kısıtlanmış ayarlara izin ver"** seçeneğini onaylayın.
   - Ardından **Erişilebilirlik > Yüklü Uygulamalar > S26 5G/4.5G Otomatik Geçiş Servisi**'ni açın.
4. Uygulama içindeki **"Geniş Widget (4x1)"** veya **"Kompakt (2x1)"** butonuna basarak widget'ı doğrudan S26 Ultra ana ekranınıza ekleyin.
