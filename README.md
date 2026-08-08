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

**P0 — iskelet.** Henüz hiçbir oyun-içi özellik yok: mod ve eklenti açılır
ama sessizdir. Bkz. `DESIGN.md` §14 için faz planı.

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
./gradlew :core:test                # protokol + durum modeli birim testleri
./gradlew :mc:1.21.11:build          # birincil hedef: Fabric 1.21.11
./gradlew :paper:build               # Paper/Leaf eklentisi
```

İlk `:mc:*` derlemesi Minecraft istemci/sunucu jar'larını ve Yarn
mapping'lerini indirip decompile eder; ilk çalıştırma uzun sürebilir.

Sürüm matrisi (`mc`, `yarn`, `fabric-api`, render kovası, loom sürümü)
`versions.json`'da makine-okunur biçimde tutulur; 12 build dosyası elle
yazılmaz, `settings.gradle.kts` bunları programatik olarak üretir.

## Lisans

MIT — bkz. `LICENSE`. Gerekçe için `DESIGN.md` §13.
