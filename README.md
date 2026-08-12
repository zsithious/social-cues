# Social Cues

Oyuncuların ne yaptığını (yazıyor / bir ekranda / boşta / konuşuyor)
diğer oyunculara oyun içi görsellerle gösteren, WATUT'a bağımsız ve açık
kaynaklı bir alternatif. Tek Modrinth projesinde hem Fabric modu (1.21 →
1.21.11, 12 sürüm) hem Paper/Spigot/Purpur/Leaf eklentisi (tek jar) olarak
dağıtılır.

Tasarımın tek doğruluk kaynağı **`DESIGN.md`**. Temiz oda kuralı için
**`CLEANROOM.md`**'ye bakın — bu proje WATUT'un koduna hiçbir zaman
bakılmadan, sadece genel/herkese açık davranış tarifinden ve
Minecraft/Fabric/Bukkit'in resmi API'lerinden geliştirilmiştir.

## Durum

**P0–P8 kodu tamam, yayın bekliyor (sürüm `1.0.0`).** Üç katmanın hepsi
(isim etiketi / sekme listesi / poz + tutulan ekran), yapılandırma ekranı,
gizlilik anahtarları ve on iki sürümün tamamı çalışır durumda; `buildAll`
12 Fabric + 1 Paper jar üretiyor, `:core:test` 435 test geçiyor ve
`tools/verify-mixins.py` on iki satırın hepsinde mixin hedeflerini
doğruluyor.

El testi durumu — **derlenmek çalışmak değildir**, P7 bunu pahalıya öğretti:

| Satır | Durum |
|---|---|
| 1.21.11 | el testi yapıldı (P4/P5/P6 turları) |
| 1.21 | el testi yapıldı (P7 kova A turu) |
| 1.21.1 | kısa görsel doğrulama **bekliyor** |
| 1.21.2 – 1.21.10 | derleme + mixin doğrulaması var, oyunda hiç çalıştırılmadı |

Faz planı için `DESIGN.md` §14, sıradaki iş için `SIRADAKI-IS.md`.

## Opsiyonel entegrasyonlar

Dördü de **yoksa sessizce kapalı**; hiçbiri zorunlu değil ve hiçbirinin kodu
yayınlanan jar'lara girmez.

| Entegrasyon | Ne yapar | Nasıl bağlanır |
|---|---|---|
| Mod Menu | ayarlar ekranını mod listesinden açar | `modmenu` entrypoint |
| Cloth Config | ayarlar ekranının kendisi | jar-in-jar (gömülü) |
| Simple Voice Chat | "konuşuyor" ipucu | `voicechat` entrypoint, `compileOnly` |
| PlaceholderAPI | sunucu tarafı `%socialcues_*%` yer tutucuları | `softdepend`, `compileOnly` |

Son ikisinin lisansı bu projeyle aynı değil (SVC "All Rights Reserved",
PlaceholderAPI GPL-3.0). İkisi de **yalnızca derleme classpath'inde**: hiçbir
baytları dağıtılan jar'lara girmiyor ve her birine yapılan tüm atıflar tek bir
dosyada toplanmış durumda, böylece bu iddia göz kararı değil makine kontrolü
ile doğrulanabiliyor.

## Modül yapısı

```
core/         Saf Java protokol + durum modeli. net.minecraft.* / org.bukkit.* import YOK.
mc-shared/    Tüm 12 MC sürümünde aynı derlenen paylaşılan Fabric kodu.
adapters/     Render kovaları (A/B/C/D) — sürüme özgü, mixin ağırlıklı kod.
mc/           settings.gradle.kts tarafından versions.json'dan üretilen :mc:<sürüm> projeleri.
paper/        Tek jar Bukkit/Paper eklentisi.
```

`core/` hiçbir zaman Minecraft veya Bukkit tiplerine bağımlı olmaz; bu
sayede Fabric modu ile Paper eklentisi aynı protokol/durum kodunu paylaşır
ve iki taraf arasında protokol sapması imkânsız hale gelir. Bu kural
`core/build.gradle.kts` içindeki `checkCleanRoom` Gradle görevi ile de
otomatik denetlenir (`./gradlew :core:check`).

## Derleme

Gereken JDK: `/home/erto/jdk21` (Temurin 21). Sistem java'sı **kullanılmaz**;
her `gradle.properties` bunu `org.gradle.java.home` ile zorunlu kılar.

```
./gradlew :core:test                 # protokol + durum modeli birim testleri
./gradlew :mc:1.21.11:build          # birincil hedef: Fabric 1.21.11
./gradlew :paper:build               # Paper/Leaf eklentisi
./gradlew buildAll                   # on iki Fabric jar'ı + Paper jar'ı
```

Ölçüm araçları (sürüm sınırları tahmin edilmez, ölçülür):

```
tools/seam.sh <sınıf> [desen]        # bir üye hangi satırlarda var?
python3 tools/verify-mixins.py       # mixin'ler gerçekten bağlanacak mı?
python3 tools/gen_icons.py           # ipucu ikon atlası (üretici depoda)
python3 tools/gen_mod_icon.py        # mod/proje ikonu
```

İlk `:mc:*` derlemesi Minecraft istemci/sunucu jar'larını ve Yarn
mapping'lerini indirip decompile eder; ilk çalıştırma uzun sürebilir.

Sürüm matrisi (`mc`, `yarn`, `fabric-api`, render kovası, loom sürümü)
`versions.json`'da makine-okunur biçimde tutulur; 12 build dosyası elle
yazılmaz, `settings.gradle.kts` bunları programatik olarak üretir.

## Lisans

MIT — bkz. `LICENSE`. Gerekçe için `DESIGN.md` §13.
