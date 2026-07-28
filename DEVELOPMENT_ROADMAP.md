# AI CADDY — GELİŞTİRME YOL HARİTASI VE UYGULAMA REHBERİ

Bu doküman, mevcut AI Caddy mimarisine yönelik önerilen 8 iyileştirmenin **nasıl, hangi sırayla ve hangi dosyalarda** uygulanacağını adım adım açıklar. Her madde; mevcut duruma dokunmadan aşamalı (incremental) olarak eklenebilecek şekilde tasarlanmıştır.

---

## Önerilen Uygulama Sırası (Bağımlılıklara Göre)

1. **Latency Optimizasyonu** *(En yüksek his/kalite etkisi, en düşük risk)*
2. **Dayanıklılık (Resilience)** *(Sistemi kırmadan güvence altına alır)*
3. **Kalıcı Hafıza (Persistent Memory)** *(SQLite tabanlı geçmiş)*
4. **Mood Engine → Sürekli Model** *(Valence-Arousal vektörel ruh hali)*
5. **Multi-Agent Ayrımı** *(Tek çağrıda taktik analiz + kedi kişiliği)*
6. **Çok Oyunculu İzolasyon** *(UUID bazlı eşzamanlı instance yönetimi)*
7. **Gözlemlenebilirlik** *(JSONL loglama ve istatistik komutu)*
8. **Yayın ve Sunum** *(Demo, Modrinth, performans grafikleri)*

---

## 1. Latency: LLM + TTS'i Paralelleştirme

### Neden önce bu?
Kullanıcının "canlılık" algısı en çok tepki hızına bağlı. Kod değişikliği görece izole (`GroqAiProvider` + `TtsManager` arası), diğer modülleri etkilemez.

### Adımlar

#### 1.1 — Groq API Çağrısını Streaming Moduna Al
- `GroqAiProvider.java` içinde HTTP isteğine `"stream": true` ekle.
- Tam cevabı beklemek yerine `HttpResponse.BodyHandlers.ofLines()` veya SSE (Server-Sent Events) satır satır okuma kullan.
- Groq, OpenAI-uyumlu `data: {...}` formatında chunk'lar döner; her chunk `choices[0].delta.content` alanında kısmi metin taşır.

#### 1.2 — Kısmi JSON'dan `final_replik` Alanını Erken Çıkar
- Tam JSON parse etmek yerine, gelen chunk'ları bir `StringBuilder`'da biriktir.
- `"final_replik":` anahtarı belirdiği andan itibaren, kapanan `"` karakterine kadar olan kısmı **regex ile** (`"final_replik"\s*:\s*"([^"]*)"`) çek — JSON tamamlanmasını bekleme.
- Yeni statik yardımcı sınıf önerisi: `PartialJsonExtractor.java`.

#### 1.3 — Cümle Bazlı TTS Chunking
- `TtsManager`'a yeni metot: `speakStreaming(Supplier<String> textChunks)`.
- Gelen metni cümle sonu noktalama işaretlerine (`.`, `!`, `?`) göre böl; ilk cümle tamamlanır tamamlanmaz `edge-tts` sürecini o cümle için başlat, ikinci cümle hâlâ üretilirken ilk cümlenin sesi `mpv`'ye pipe edilsin.
- `ExecutorService` üzerinde sıralı (FIFO) bir ses kuyruğu (`Queue<String> ttsQueue`) tutarak cümlelerin üst üste binmeden ama gecikmesiz çalmasını garanti et.

**Beklenen Kazanım:** Algılanan yanıt süresi ~1.5-2 sn'den ~0.4-0.6 sn'ye düşer (ilk ses çıkışına kadar geçen süre).

---

## 2. Dayanıklılık (Resilience) — Circuit Breaker + Canned Fallback

### Adımlar

#### 2.1 — Basit Circuit Breaker Sınıfı Yaz
- Yeni dosya: `com.example.ai.resilience.CircuitBreaker.java`
- İçerik: `AtomicInteger failureCount`, `AtomicLong openedUntil`.
- `recordFailure()` çağrıldıkça sayaç artar; 3 hataya ulaşınca `openedUntil = now + 60_000` set edilir. `isOpen()` metodu bu süre dolana kadar `true` döner.
- Her sağlayıcı (`GroqAiProvider`, `TtsManager` içindeki Edge/ElevenLabs/StreamElements) için **ayrı ayrı** birer `CircuitBreaker` instance'ı tut.

#### 2.2 — Sağlayıcı Çağrılarını Breaker Kontrolünden Geçir
- Her `callX()` metodunun başına:
  ```java
  if (breaker.isOpen()) {
      fallback();
  } else {
      try { ... } catch (e) { breaker.recordFailure(); fallback(); }
  }
  ```

#### 2.3 — Canned (Önceden Kaydedilmiş) Ses Havuzu
- `assets/canned_responses/` klasörü altında 10-15 adet `.ogg` dosyası (örn. `hmm_dusunuyorum.ogg`, `bir_saniye.ogg`, `seni_duyuyorum.ogg`).
- Tüm sağlayıcılar çöktüğünde `TtsManager.playCannedFallback()` rastgele birini `mpv` ile çalsın — sessizlik yerine minimum "hayattayım" tepkisi.

---

## 3. Kalıcı Hafıza (Persistent Memory)

### Adımlar

#### 3.1 — SQLite Entegrasyonu
- `build.gradle`'a `org.xerial:sqlite-jdbc` bağımlılığı ekle (saf JDBC, ekstra sunucu gerektirmez, tek dosya: `config/ai_caddy_memory.db`).
- Yeni sınıf: `com.example.ai.memory.PlayerMemoryStore.java`
- Tablo Şeması:
  ```sql
  CREATE TABLE player_memory (
    uuid TEXT PRIMARY KEY,
    player_name TEXT,
    favorite_biome TEXT,
    last_seen_at INTEGER,
    notable_events TEXT  -- JSON array, son 10 olay
  );
  ```

#### 3.2 — Yazma Noktası
- `EmotionalEventDetector` bir `rare_item` veya `biome_change` tetiklediğinde, `PlayerMemoryStore.appendEvent(uuid, eventDescription)` çağrısı ekle (async, `CompletableFuture.runAsync`).

#### 3.3 — Okuma / Prompt'a Enjeksiyon
- `AiBrainManager.buildCompanionPrompt()` içine, sistem promptundan önce şu satırı ekle:
  `"Bu oyuncuyla daha önce şunlar yaşandı: {son 2-3 notable_event}."`

#### 3.4 — Duygusal Hafızayı da Kalıcı Yap
- `CompanionMoodEngine.emotionalMemory` (Deque) her değiştiğinde son elemanı aynı SQLite tablosuna (`notable_events` alanına) yaz.

---

## 4. Mood Engine: Enum → Sürekli (Valence-Arousal) Model

### Adımlar

#### 4.1 — Yeni Veri Sınıfı
- `CompanionMoodVector.java`: `record MoodVector(double valence, double arousal) {}` (valence: -1..1, arousal: 0..1).

#### 4.2 — Enum'u Türetilmiş Hale Getir
- `CompanionMoodState.fromVector(MoodVector v)` statik metodu ekle: basit eşik tablosu ile (örn. `arousal > 0.7 && valence < -0.3 -> SCARED`) enum'u üret.
- Mevcut kod tabanının uyumluluğu için enum yapısını tamamen kaldırma, sadece üretim mekanizmasını vektörel hale getir.

#### 4.3 — `CompanionMoodEngine`'i Güncelle
- `AtomicReference<CompanionMoodState>` yerine `AtomicReference<MoodVector>` tut; enum'u ihtiyaç anında `fromVector()` ile türet.
- Her olay tetiklendiğinde vektörü **üstel yumuşatma (exponential smoothing)** ile güncelle:
  ```java
  newValence = oldValence * 0.6 + eventValence * 0.4;
  ```
- Böylece geçişler ani değil kademeli ve doğal olur.

#### 4.4 — Prompt'a İnce Ton Bilgisi Geç
- `buildCompanionPrompt()` içine: `"Ruh hali: valence=%.2f, arousal=%.2f (%s ağırlıklı)"` formatında ek bağlam satırı ekle.

---

## 5. Multi-Agent Ayrımı (Strategist + Personality, Tek Çağrıda)

### Adımlar

#### 5.1 — JSON Şemasını Genişlet
```json
{
  "tactic_analysis": "Kısa, nesnel taktik değerlendirmesi (LLM'in iç muhakemesi, kullanıcıya gösterilmez).",
  "final_replik": "2 cümlelik samimi Türkçe replik."
}
```

#### 5.2 — Sistem Promptunu İki-Aşamalı Düşünmeye Zorla
- Sistem promptuna şu talimatı ekle: *"Önce `tactic_analysis` alanında durumu nesnel şekilde değerlendir (doğruluk önceliklidir), sonra bu değerlendirmeye dayanarak `final_replik` alanında kedi kişiliğiyle konuş."*
- Ek gecikme veya ek API maliyeti yaratmadan chain-of-thought simüle eder.

#### 5.3 — Ayrıştırma
- `GroqAiProvider.extractFinalReplik()` metodunu genişlet: `tactic_analysis` alanını sadece debug loguna yaz (`CompanionDebugLogger`), oyuncuya asla gösterme.

---

## 6. Çok Oyunculu (Multiplayer) İzolasyon

### Adımlar

#### 6.1 — Instance'ları UUID Bazlı Haritaya Taşı
- Statik alanları oyuncuya özel haritalara dönüştür:
  ```java
  ConcurrentHashMap<UUID, AiBrainManager> brainInstances;
  ConcurrentHashMap<UUID, VoskSttManager> sttInstances;
  ```
- Her oyuncu katıldığında (`JOIN`) yeni instance oluştur, ayrıldığında (`DISCONNECT`) haritadan kaldır.

#### 6.2 — `MicrophonePacketEvent` İşleyicisini Güncelle
- `AiCompanionVoicePlugin`'de paket geldiğinde `event.getSenderUuid()` üzerinden doğru `VoskSttManager` instance'ını bul ve oyuncuya özel `Recognizer` kullan.

---

## 7. Gözlemlenebilirlik (Observability)

### Adımlar

#### 7.1 — Yapısal Log Dosyası
- Yeni sınıf: `com.example.ai.metrics.JsonLineLogger.java` — her olayı `logs/ai-caddy-events.jsonl` dosyasına tek satır JSON olarak ekler.
- Loglanacak alanlar: `timestamp, eventType, playerUuid, moodBefore, moodAfter, llmLatencyMs, ttsLatencyMs, providerUsed, wasFallback`.

#### 7.2 — Basit Metrik Özeti Komutu
- `/aikedi istatistik` komutu ekle: son 1 saatlik `.jsonl` dosyasını okuyup ortalama LLM gecikmesi, fallback oranı ve en sık tetiklenen mood geçişini sohbet penceresine özetlesin.

---

## 8. Yayın ve Sunum

### Adımlar
- **8.1 Demo Senaryosu:** Gece olur -> Creeper yaklaşır -> Can düşer -> Nadir eşya bulunur akışını tek çekimde göster.
- **8.2 Modrinth/CurseForge Sayfası:** Mimari diyagram ve kurulum adımları.
- **8.3 Tez/Rapor Ek Materyalleri:** Gecikme dağılım grafiği, draw.io/Mermaid mimari çizimi ve "Öncesi/Sonrası" karşılaştırma tablosu.

---

## Özet Uygulama Sırası ve Tahmini Efor

| # | Konu | Etkilenen Sınıflar | Tahmini Efor |
|---|------|---------------------|--------------|
| 1 | Latency (streaming) | `GroqAiProvider`, `TtsManager` | Orta |
| 2 | Resilience | Yeni `CircuitBreaker`, tüm provider'lar | Düşük |
| 3 | Kalıcı hafıza | Yeni `PlayerMemoryStore`, `AiBrainManager` | Orta |
| 4 | Sürekli mood modeli | `CompanionMoodEngine`, `CompanionMoodState` | Orta |
| 5 | Multi-agent (tek çağrı) | `GroqAiProvider`, prompt şablonu | Düşük |
| 6 | Multiplayer izolasyon | `AiBrainManager`, `VoskSttManager`, `AiCompanionVoicePlugin` | Orta-Yüksek |
| 7 | Gözlemlenebilirlik | Yeni `JsonLineLogger` | Düşük |
| 8 | Yayın/sunum | Repo dışı (dokümantasyon) | Düşük |

---

## 9. Ek Bölüm: 50 Senaryo Testi Analizi ve İnsansılık (Human-Likeness) İyileştirmeleri

50 senaryo testinden elde edilen bulgular ışığında, Kedi'nin insansı arkadaşlık algısını en üst düzeye çıkarmak için 5 kritik mühendislik ve prompt iyileştirmesi uygulanmıştır:

### 9.1 🚨 Dil Sızıntısı (Language Leakage — Öncelik #1)
* **Sorun:** Düşük olasılıklı token'larda Çince (`完全`), Vietnamca (`thật`), Hollandaca (`iets`) ve İngilizce (`Oh no`, `OyuncuComplex`) kelimelerin Türkçe yanıtlara sızması.
* **Çözüm:**
  1. Sistem promptuna katı kural eklendi: *"SADECE Türkçe kelime kullan. Tek bir yabancı kelime veya 'Oh no' gibi İngilizce ünlem bile kabul edilemez; yerine 'Eyvah', 'Olamaz' de."*
  2. `GroqAiProvider` ve test çalıştırıcı içine Latin-dışı Unicode karakter ve yabancı kelime (`Oh no`, `thật`, `iets`, vb.) **regex filtresi/temizleyicisi** yerleştirildi.
  3. API istek sıcaklığı (`temperature`) **0.9 → 0.72** değerine düşürülerek halüsinasyon riski azaltıldı.

### 9.2 🔁 İç Muhakeme Şablonlaşması (Reasoning Templating — Öncelik #2)
* **Sorun:** Aynı ruh halindeki farklı senaryolarda `durum_analizi` ve `ic_dusunce` alanlarının kalıp metin olarak tekrar etmesi.
* **Çözüm:** Sistem promptuna kural eklendi: *"`ic_dusunce` ve `durum_analizi` alanlarında genel kalıplar YASAKTIR. Mutlaka bu senaryoya özgü en az bir somut detayı (blok adı, varlık adı, obje adı, oyuncunun eylemi) belirterek özgün bir analiz yaz."* Akıllı simülatördeki tüm 50 senaryoya özel benzersiz analizler yazıldı.

### 9.3 🗣️ Kelime/İfade Tekrarı ve Klişeleşme (Öncelik #3)
* **Sorun:** Sürekli `"Vay be"`, `"Vay canına"`, `"Sen bir Minecraft dehasısın"` açılışlarının kullanılması.
* **Çözüm:**
  1. `AiBrainManager` içinde son kullanılan 8 açılış kelimesini/ifadesini tutan `RECENT_OPENING_PHRASES` (`Deque<String>`) eklendi ve prompt'a *TEKRAR ETME* listesi olarak bağlandı.
  2. API yüküne `"frequency_penalty": 0.45` parametresi eklendi.
  3. Övgü replikleri mimari, beceri ve cesaret olarak çeşitlendirildi.

### 9.4 😐 SAD Kategori Empati Derinliği (Öncelik #4)
* **Sorun:** Üzücü durumlarda müşteri hizmetleri gibi hemen *"benden ne istiyorsun / çözelim"* moduna girilmesi.
* **Çözüm:** SAD ruh hali için iki-fazlı empati talimatı eklendi: *"Önce sadece oyuncunun duygusunu ve acısını onayla/yansıt (çözüm önermeden 1 sıcak cümle kur). Müşteri hizmetleri gibi 'senden ne istiyorum/nasıl yardımcı olayım' ASLA deme. İkinci cümlede istersen hafif bir teselli ver."*

### 9.5 📐 Yapısal Monotonluk (Öncelik #5)
* **Sorun:** Her repliğin `[ünlem] + [aksiyon çağrısı] + "!"` biçiminde bitmesi.
* **Çözüm:** Prompt talimatı eklendi: *"Her replik ünlemle (!) bitmek zorunda değil. Bazen sadece sakin bir soru sor, bazen ünlemsiz bir gözlem paylaş, aksiyon çağrısı yapma. Bazen de tek kelimelik ('Şşşt...', 'Eyvah.') kısa tepkiler ver."*
