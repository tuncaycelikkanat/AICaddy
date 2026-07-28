# AI CADDY (AI KEDİ) — TEKNİK MİMARİ VE SİSTEM DOKÜMANTASYONU

Bu doküman, **AI Caddy (AI Kedi)** Minecraft modunun mimarisini, bileşenlerini, eşzamanlılık (concurrency) modelini, ses işleme (STT/TTS) boru hatlarını, yapay zeka entegrasyonunu ve oyun-içi duygu/olay (Mood & Event) motorunu en küçük kodsal detayına kadar açıklamaktadır.

---

## 1. SİSTEME GENEL BAKIŞ VE MİMARİ FELSEFE

AI Caddy, oyuncuya Minecraft dünyasında eşlik eden **yalnızlık giderici, canlı, duygusal ve empatik bir yapay zeka arkadaşıdır (Companion)**.
Geleneksel "oyun öğreten rehber (Tutor)" botlarının aksine; AI Kedi oyuncuya ders vermez, maddeler halinde listeler sunmaz. Oyuncunun o anki durumunu, canını, envanterini, biyomunu, saatini ve çevresindeki tehlikeleri sürekli algılar; bu durumlara göre **gerçek zamanlı ruh hali (Mood) değişimleri** yaşar ve duruma spontane tepkiler verir.

```
+-----------------------------------------------------------------------------------+
|                            MINECRAFT SUNUCUSU (Fabric)                            |
+-----------------------------------------------------------------------------------+
       |                                      ^                              ^
       | 1. ServerTick (Her 2 sn)             | 2. PTT Ses Paketi            | 8. Chat & Ses
       v                                      |    (MicrophonePacketEvent)   |    Yayınlama
+---------------------------+       +---------+--------------------+         |
|  EmotionalEventDetector   |       |  AiCompanionVoicePlugin    |         |
|  (Can, Biyom, Gece, Mob)  |       |  (Opus -> PCM Çözücü)      |         |
+-------------+-------------+       +---------+--------------------+         |
              |                               |                              |
              | 3. Mood Değişimi &            | 4. 16-bit PCM Audio          |
              |    Proaktif Tetikleme         v                              |
              |                     +------------------------------+         |
              v                     |       VoskSttManager         |         |
+---------------------------+       |  (Offline Türkçe STT / Vosk) |         |
|    CompanionMoodEngine    |       +---------+--------------------+         |
|   (6 Ruh Hali & Hafıza)   |                 |                              |
+-------------+-------------+                 | 5. 500ms Sessizlik           |
              |                               |    Sonrası Metin             |
              +---------------+---------------+                              |
                              |                                              |
                              v                                              |
                 +--------------------------+                                |
                 |      AiBrainManager      |--------------------------------+
                 | (Prompt & LLM Yönetimi)  |
                 +------------+-------------+
                              |
                              | 6. HTTP POST (JSON Schema)
                              v
                 +--------------------------+
                 |     GroqAiProvider       |
                 | (Llama 3.3 70B Versatile)|
                 +------------+-------------+
                              |
                              | 7. "final_replik" Metni
                              v
                 +--------------------------+
                 |        TtsManager        |
                 | (Microsoft Edge TTS/mpv) |
                 +--------------------------+
```

---

## 2. MODÜL VE SINIF DETAYLARI

### 2.1 Çekirdek ve Kayıt Modülü (`com.example`)

#### `ExampleMod.java`
* **Görev:** Modun giriş noktası (`ModInitializer`). Sunucu başladığında tüm alt sistemleri başlatır ve oyuncu komutlarını Minecraft komut ağacına kayıt eder.
* **Komut Ağacı (`/aikedi`):**
  * `/aikedi unut`: `AiBrainManager.clearHistory()` çağrılır. Kısa vadeli konuşma geçmişi (son 5 tur) silinir.
  * `/aikedi ses aç|kapat`: `TtsManager.setTtsEnabled(boolean)` tetiklenir. Sesli konuşma açılır veya susturulur.
  * `/aikedi debug aç|kapat`: `CompanionDebugLogger.setEnabled(boolean)` çağrılır. Oyun içi olaylar, ruh hali değişimleri ve proaktif konuşma engelleri sohbet penceresine basılır.
* **Olay Kayıtları:** `ServerTickEvents.END_SERVER_TICK` olayına `EmotionalEventDetector.tick()` metodunu bağlar.

---

### 2.2 Çevre Algılama ve Bağlam (Context) Modülü (`com.example.ai`)

#### `MinecraftContextProvider.java`
* **Görev:** Oyuncunun oyun içi fiziksel bağlamını anlık olarak okuyup LLM'in anlayabileceği yapılandırılmış Türkçe bir özete dönüştürür.
* **Toplanan Veriler:**
  1. **Can & Açlık:** `player.getHealth()`, `player.getMaxHealth()` ve `player.getFoodData().getFoodLevel()`.
  2. **Boyut & Biyom & Zaman:** `level.dimension()`, `level.getBiome()`, `level.isNight()` ve `level.isRaining()`. (Örn: `Gece (Canavarlar çıkıyor!), Yağmurlu`).
  3. **Ana El (Main Hand):** Oyuncunun elindeki eşya (`ItemStack`).
  4. **Baktığı Hedef (Raycast):** Oyuncunun bakış yönü (`viewVector`) doğrultusunda **7 blok mesafeye kadar** ışın yollanır (`AABB.clip`). Önce canlı varlıklara (`Entity`), yoksa bloklara (`HitResult.Type.BLOCK`) bakılır.
  5. **Envanter Özeti:** Envanterdeki ilk 10 benzersiz dolu eşya yığını listelenir.
* **Örnek Çıktı:**
  ```text
  - Can: 14/20 | Açlık: 17/20
  - Boyut: overworld | Biyom: forest | Gece (Canavarlar çıkıyor!), Temiz
  - Elinde: 1x Diamond Sword
  - Baktığı: Entity (Creeper) at distance 3.4m
  - Envanter: 64x Bread, 1x Iron Pickaxe, 12x Torch
  ```

---

### 2.3 Duygu, Ruh Hali ve Olay Motoru (`com.example.ai.mood`)

```
               +------------------------------------+
               |       EmotionalEventDetector       |
               | (40 Tick / Her 2 saniyede bir tarama) |
               +-----------------+------------------+
                                 |
         +-----------------------+-----------------------+
         |                       |                       |
         v                       v                       v
  [Gece Oldu mu?]         [Can < %25 mi?]        [Creeper Yakın mı?]
         |                       |                       |
         +-----------------------+-----------------------+
                                 |
                                 v
               +------------------------------------+
               |        CompanionMoodEngine         |
               | (Ruh Hali Değişimi & Cooldown Kont.)|
               +-----------------+------------------+
                                 |
                                 v
               +------------------------------------+
               |           AiBrainManager           |
               |   (LLM Prompt & Proaktif Tepki)   |
               +------------------------------------+
```

#### `CompanionMoodState.java`
* **Görev:** Kedi'nin bürünebileceği 6 temel ruh halini ve her birinin davranış kurallarını tanımlayan Enum yapısı.
* **Durumlar:**
  1. `HAPPY (Mutlu 😊)`: Varsayılan, enerjik, neşeli durum.
  2. `EXCITED (Heyecanlı 🤩)`: Nadir ganimet (Elmas/Netherite) bulunduğunda veya boss kesildiğinde devreye girer. Yüksek tepkili konuşur.
  3. `SCARED (Korkmuş 😱)`: Gece olduğunda, Creeper yaklaştığında veya can %25'in altına düştüğünde tetiklenir. Endişeli, panik cümleler kurar.
  4. `BORED (Sıkılmış 🥱)`: Uzun süre hiçbir olay olmadığında devreye girer. Oyuncuyu dürtükler, macera arar.
  5. `CURIOUS (Meraklı 🧐)`: Yeni bir biyoma girildiğinde veya bilinmeyen bir blok kazıldığında oluşur.
  6. `SYMPATHETIC (Empatik 🥺)`: Oyuncu öldüğünde veya ciddi hasar aldığında devreye girer. Teselli eder.

#### `CompanionMoodEngine.java`
* **Görev:** Kedi'nin duygusal durumunu yöneten ve iş parçacığı güvenliği (`Thread-Safe`) sağlayan Singleton motor.
* **Teknik Yapı:**
  * `AtomicReference<CompanionMoodState> currentMood`: Anlık ruh hali.
  * `Deque<String> emotionalMemory`: Son 5 ruh hali değişimini barındıran duygusal hafıza kuyruğu.
  * `ConcurrentHashMap<String, Long> lastEventFireTime`: Her bir olay türü için proaktif konuşma zaman damgalarını tutar.
* **Deduplication & Cooldown Mekanizması:**
  * Eski sistemdeki genel 90 saniye bekleme limiti **kaldırılmıştır**.
  * Yerine `canSpeakProactively(String eventKey)` metodu ile **olay anahtarı başına 30 saniyelik (`SAME_EVENT_COOLDOWN_MS = 30_000`)** akıllı bekleme süresi getirilmiştir.
  * *Avantajı:* Gece olduğu için (`night_fell`) konuşan Kedi, hemen 5 saniye sonra Creeper yaklaştığında (`creeper_near`) engellenmez, anında korkuyla bağırabilir.

#### `EmotionalEventDetector.java`
* **Görev:** Minecraft sunucu döngüsünde her 40 tick'te (2 saniye) bir çalışarak oyuncunun durumunu analiz eder.
* **Algılanan Olaylar ve Tetikleyiciler:**
  1. **Gece Algılaması (`night_fell`):** `level.isNight()` `true` olduğunda ve oyuncu yeraltında/mağarada değilse ruh halini `SCARED` yapar.
  2. **Düşük Can (`low_health`):** Can %25'in (`HP <= 5.0`) altına indiğinde `SCARED` moduna geçer ve uyarır.
  3. **Creeper Yaklaşması (`creeper_near`):** Oyuncunun etrafındaki **6 blokluk kübik alanda** (`getBoundingBox().inflate(6.0D)`) Creeper varsa anında tetiklenir.
  4. **Biyom Keşfi (`biome_change`):** Oyuncunun bulunduğu biyom değiştiğinde `CURIOUS` moduna geçer.
  5. **Nadir Eşya (`rare_item`):** Envantere `Diamond`, `Netherite`, `Elytra` veya `Dragon Egg` girdiğinde `EXCITED` moduna geçer.

---

### 2.4 Yapay Zeka (LLM) ve Prompt Boru Hattı (`com.example.ai`)

#### `AiBrainManager.java`
* **Görev:** Oyuncu mesajlarını ve proaktif olayları alıp, zengin bağlamı (Context + Mood + Geçmiş) birleştirerek LLM sağlayıcısına ileten beyin.
* **Konuşma Geçmişi (Memory):**
  * `CHAT_HISTORY`: Son 5 karşılıklı konuşma turunu (`ChatTurn`) RAM'de tutar (`MAX_HISTORY_TURNS = 5`).
  * Hem oyuncunun dediğini hem de Kedi'nin yanıtını saklar.
* **Prompt Mühendisliği (`buildCompanionPrompt`):**
  * Sistem promptu kesin kurallarla yapılandırılmıştır:
    - *"Bir öğretmen ya da rehber değilsin. Sadece orada olan, aynı anı paylaşan bir arkadaşsın."*
    - *"Maksimum 2 kısa cümle kur. Maddeler ('Adım 1...') kullanma."*
    - *"SADECE JSON formatında yanıt ver."*
  * **JSON Çıktı Şeması (Schema):**
    ```json
    {
      "final_replik": "Buraya TTS tarafından seslendirilecek 2 cümlelik samimi Türkçe replik gelir."
    }
    ```

#### `GroqAiProvider.java`
* **Görev:** Groq LPU altyapısını kullanarak `llama-3.3-70b-versatile` modeliyle haberleşen asenkron API istemcisi.
* **Özel Çözümler:**
  * `response_format: { "type": "json_object" }` parametresi ile modelin kesinlikle JSON döndürmesini zorlar.
  * **Temiz Ayrıştırma (`extractFinalReplik`):** Model JSON döndürdüğünde `final_replik` anahtarını okur; eğer model yanlışlıkla düz metin döndürürse hata patlatmadan metni direkt fallback olarak kullanır.
  * **Boş `userMessage` Desteği:** Proaktif konuşmalarda `userMessage` boş gittiği için Groq API'ye gereksiz boş kullanıcı mesajı eklemez (sadece `system` mesajı gider).

---

### 2.5 Ses İşleme Boru Hattı (TTS & STT & Voice Chat)

#### A. Konuşma Tanıma (STT — Speech to Text)

```
[Oyuncu Mikrofonuna Konuşur]
              |
              v (Opus Ses Paketleri)
+------------------------------------+
|       AiCompanionVoicePlugin       |
| (MicrophonePacketEvent -> PCM 16b) |
+-----------------+------------------+
              |
              v (short[] pcmData)
+------------------------------------+
|           VoskSttManager           |
| (Vosk C++ Engine -> partialText)   |
+-----------------+------------------+
              |
              | 500ms Sessizlik Boşluğu (Silence Gap)
              v
+------------------------------------+
|  AiBrainManager.processAndRespond  |
+------------------------------------+
```

1. **`AiCompanionVoicePlugin.java` (Simple Voice Chat Entegrasyonu):**
   * Simple Voice Chat modunun `MicrophonePacketEvent` olayını dinler.
   * Gelen `Opus` sıkıştırılmış ses paketlerini çözer (`OpusDecoder.decode`) ve `short[]` (16-bit PCM) dizisine dönüştürür.
   * **Sessizlik Boşluğu ile Paketleme (Silence-Gap Debounce):**
     * Her ses paketi geldiğinde `FLUSH_SCHEDULER` üzerindeki 500ms'lik zamanlayıcı iptal edilip baştan başlatılır.
     * Oyuncu mikrofon tuşunu (PTT) bıraktığında veya konuşmayı kestiğinde, **500 milisaniye boyunca yeni ses gelmezse** `flushCurrentSpeech()` tetiklenir.
     * *Sorun Çözümü:* Bu mekanizma sayesinde mikrofon art arda açılıp kapatıldığında cümlede kayıp yaşanmaz.

2. **`VoskSttManager.java` (Offline Türkçe STT):**
   * AlphaCephei'nin `vosk-model-small-tr-0.3` (45 MB) Türkçe akustik modelini kullanır.
   * Mod ilk açıldığında modeli otomatik olarak indirmekte ve `models/vosk-model-tr/` dizinine açmaktadır.
   * `transcribe(short[] pcmData)` metodu ses dalgalarını kelimelere döker ve `flushPartial()` metodu ile tamamlanan cümleyi dışarı aktarıp tanıyıcıyı (`Recognizer`) sıfırlar.

---

#### B. Metin Seslendirme (TTS — Text to Speech)

```
[LLM 'final_replik' Üretir]
              |
              v
+------------------------------------+
|             TtsManager             |
| (In-Game kedi sesi: CAT_PURREOW)   |
+-----------------+------------------+
              |
              +------------------------------------+
              | 1. Öncelikli Motor                 | 2. Hata Olursa
              v                                    v
+------------------------------------+   +------------------------------------+
|        Microsoft Edge TTS          |   |      ElevenLabs API (Bella)        |
|  (edge-tts --voice tr-TR-Emel)     |   |  (EXAVITQu4vr4xnSDxMaL / v2)       |
+-----------------+------------------+   +-----------------+------------------+
                  |                                        |
                  | STDOUT Pipe                            | HTTP InputStream
                  v                                        v
+-----------------------------------------------------------------------------+
|                          MPV Player (Arka Plan Audio)                       |
|                   (/usr/bin/mpv - --no-video --really-quiet)                |
+-----------------------------------------------------------------------------+
```

1. **`TtsManager.java`:**
   * Seslendirme başladığında oyun içinde oyuncunun konumunda sevimli bir kedi sesi (`SoundEvents.CAT_PURREOW`, pitch 1.25f) çalar.
   * Asenkron olarak 3 kademeli bir seslendirme mimarisi çalıştırır:
     1. **Birincil Motor (Microsoft Edge TTS):**
        - Python `edge-tts` kütüphanesi (`/home/tuncay/.venvs/tts/bin/edge-tts`) kullanılarak **`tr-TR-EmelNeural`** sesi çağrılır.
        - **Sıfır Disk Gecikmesi (Zero-Disk Latency):** Ses dosyası asla geçici bir MP3 dosyasına yazılmaz. `edge-tts --write-media -` komutunun stdout çıktısı doğrudan `mpv` player'ın stdin girdisine **pipe** edilir (`mpv - --no-video`).
        - Ücretsizdir, karakter limiti yoktur ve en doğal Türkçe kadın seslerinden birini sunar.
     2. **İkincil Motor (ElevenLabs API):**
        - Eğer Edge TTS başarısız olursa veya venv bulunamazsa, `config/elevenlabs_api_key.txt` kontrol edilir.
        - `EXAVITQu4vr4xnSDxMaL` (Bella) sesi ve `eleven_multilingual_v2` modeli ile HTTP POST isteği yapılır.
     3. **Üçüncül Motor (StreamElements Fallback):**
        - Son çare olarak Twitch yayıncılarının kullandığı Amazon Polly altyapılı `Filiz` neural sesi HTTPS üzerinden çağrılır.

---

### 2.6 Hata Ayıklama ve Gözlem (Debug & Diagnostics)

#### `CompanionDebugLogger.java`
* **Görev:** Mod geliştirirken veya oyun oynarken sistemin iç mekanizmalarını gerçek zamanlı izleme imkanı sunar.
* **Komut:** `/aikedi debug aç`
* **Chate Basılan Renkli Log Bildirimleri:**
  * `§a⚡ OLAY | GECE_OLDU: isNight() = true`: Bir oyun olayı başarıyla yakalandığında.
  * `§6🎭 MOOD DEĞİŞTİ | NIGHT_FELL -> SCARED 😱`: Kedi'nin ruh hali değiştiğinde.
  * `§d🗣 PROAKTİF KONUŞMA | night_fell`: Kedi kendi kendine konuşma kararı aldığında.
  * `§c⏳ PROAKTİF ENGEL | 'creeper_near' cooldown aktif (30s)`: Olay çok sık tekrarlandığı için konuşma engellendiğinde.

---

## 3. VERİ VE DOSYA YAPISI

AI Caddy modunun yapılandırma dosyaları ve modelleri aşağıdaki konumlarda saklanır:

```
Projects/mc/
├── config/
│   ├── groq_api_key.txt           # Groq LPU API Anahtarı (gsk_...)
│   ├── elevenlabs_api_key.txt     # ElevenLabs API Anahtarı (sk_...)
│   └── ai_companion_provider.txt  # Aktif LLM Sağlayıcısı (groq/openai/ollama/gemini)
├── models/
│   └── vosk-model-tr/             # Offline Türkçe Vosk STT Modeli (45 MB)
└── src/main/java/com/example/
    ├── ExampleMod.java            # Mod Başlatıcı & Komutlar
    ├── ai/
    │   ├── AiBrainManager.java    # LLM Prompt & Konuşma Geçmişi Yönetimi
    │   ├── MinecraftContextProvider.java # Çevre & Envanter Okuyucu
    │   ├── debug/
    │   │   └── CompanionDebugLogger.java # Oyun-içi Live Debug Logger
    │   ├── mood/
    │   │   ├── CompanionMoodEngine.java  # Thread-Safe Duygu Motoru
    │   │   ├── CompanionMoodState.java   # 6 Ruh Hali & Davranış Kuralları
    │   │   ├── EmotionalEventDetector.java # 2s (40 tick) Olay Algılayıcı
    │   │   └── MoodTrigger.java          # Olay Tetikleme Modeli
    │   ├── provider/
    │   │   ├── AiProvider.java           # AI Sağlayıcı Arayüzü
    │   │   ├── GroqAiProvider.java       # Groq Llama 3.3 70B İstemcisi
    │   │   ├── GeminiAiProvider.java     # Google Gemini İstemcisi
    │   │   ├── OllamaAiProvider.java     # Local Ollama İstemcisi
    │   │   └── OpenAiProvider.java       # OpenAI ChatGPT İstemcisi
    │   └── tts/
    │       └── TtsManager.java           # Edge TTS / ElevenLabs / mpv Pipeline
    └── voice/
        ├── AiCompanionVoicePlugin.java   # Simple Voice Chat Opus Çözücü
        └── VoskSttManager.java           # Offline Vosk STT Motoru
```

---

## 4. GÜVENLİK, PERFORMANS VE CONCURRENCY PATTERN'LERİ

1. **Ana Thread Güvenliği (Minecraft Client Thread vs. Worker Threads):**
   - Ağ istekleri (Groq API, ElevenLabs HTTP, Edge TTS işlemi, Vosk ses çözme) **asla** Minecraft ana thread'inde çalıştırılmaz. Hepsi `CompletableFuture.runAsync()`, `Executors.newSingleThreadExecutor()` veya `FLUSH_SCHEDULER` üzerinde arka planda yürütülür.
   - Sonuçlar oyuna yansıyacağında (`broadcastSystemMessage`, kedi sesi çalma) mutlaka `ExampleMod.SERVER_INSTANCE.execute(...)` içine sarılarak Thread-Safe şekilde ana döngüye iletilir.
2. **Kilit ve Bellek Yönetimi (`CompanionMoodEngine`):**
   - Anlık ruh hali atamaları kilit kullanmadan `AtomicReference` üzerinden atomik olarak yapılır.
   - Proaktif zaman damgaları `ConcurrentHashMap` üzerinde eşzamanlı kilitlenmeden saklanır.
3. **Akış Gecikmesi Optimizasyonu (Latency Mitigation):**
   - Vosk STT modeli oyun açılırken arka planda asenkron yüklenir, ilk konuşmada donma yaşanmaz.
   - Microsoft Edge TTS komut satırı pipe mimarisi diske I/O yapmadığı için 300-400ms gibi çok kısa sürede ses vermeye başlar.
