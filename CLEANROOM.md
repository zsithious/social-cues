# Temiz Oda Beyanı

Social Cues, WATUT'un **gözlemlenebilir davranışını** yeniden üretmeyi hedefler —
kodunu değil. WATUT (`github.com/Corosauce/WATUT`, `github.com/SlidePowered/WatutPlugin`)
"All Rights Reserved" lisansı altındadır ve bu projenin geliştirilmesi sırasında:

- Bu iki depo hiçbir şekilde klonlanmadı, dosyaları açılmadı,
  `raw.githubusercontent.com` üzerinden çekilmedi.
- WATUT/CoroUtil jar'ı hiçbir şekilde decompile edilmedi.
- WATUT'un sınıf adları, paket yapısı (`com.corosus.*`), protokol kanal adı,
  mod_id'si (`watut`) veya yapılandırma anahtar adları hiçbir yerde kopyalanmadı,
  hatta referans olarak dahi kullanılmadı.

## Tasarım nereden geldi

Bu projenin tasarımı tamamen şu kaynaklardan türetildi:

1. **Genel, herkese açık ürün tarifi:** Modrinth/CurseForge'daki WATUT açıklama
   metni ve ekran görüntüleri/GIF'leri — "oyuncunun yazdığını, bir ekranda
   olduğunu veya boşta olduğunu diğer oyunculara gösterir" seviyesinde bir
   davranış tarifi. Bu, bir ürünü kullanmadan/tanımadan da bilinebilecek genel
   bir kavramdır (örn. Discord'un "yazıyor..." göstergesi, Slack'in durum
   ikonları ile aynı kategoride).
2. **Minecraft/Fabric/Bukkit'in kendi genel API imkânları:** `ScreenHandlerType`
   registry'si, `ClientTickEvents`, `PlayerListHud`, Bukkit `Messenger` API'si
   gibi herkese açık, resmi dokümantasyonu olan platform API'leri — bunlar
   WATUT'a değil, Minecraft'a ait.
3. **Bağımsız mühendislik kararları:** Protokolün bayt düzeyi tasarımı
   (VarInt kodlama, mesaj tipleri, alan sıralaması), durum modeli (`Activity`,
   `ScreenKind`, `CueFlags`), gizlilik/güvenlik kuralları (vanish filtresi,
   hız sınırı, sunucu politikası üst sınırı) ve render katmanlaması bu
   projenin kendi tasarım sürecinde, WATUT'un iç yapısına bakılmaksızın
   üretildi. Nitekim WATUT'un mesaj formatını, alan adlarını veya bit
   düzenini bilmiyoruz — bu yüzden protokolümüz WATUT'unkiyle **aynı
   olamaz bile**, sadece aynı amaca hizmet eder.

## Neden bu beyan var

Bir "alternatif, açık kaynak" projenin en büyük riski, orijinal projenin
telif hakkı sahibinin "kodumuzu/tasarımımızı kopyaladılar" iddiasıdır. Bu
belge, olası bir şikâyette şu savunmayı somutlaştırır: tasarım kararlarının
her biri ya (a) herkese açık, ürünü kullanmadan da bilinebilecek genel bir
davranış tarifinden, (b) Minecraft/Fabric/Bukkit'in resmi API'lerinden ya da
(c) bu projeye özgü bağımsız mühendislik kararlarından geliyor — WATUT'un
kaynağından değil.

Kural, derleme sırasında `checkCleanRoom` Gradle görevi tarafından
denetleniyor.
