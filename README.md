# 🐱 AI Caddy — Akıllı, Duygusal ve Sesli Minecraft Arkadaşı

[![Fabric 1.21.1](https://img.shields.io/badge/Fabric-1.21.1-D08C5C.svg)](https://fabricmc.net/)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Groq LPU](https://img.shields.io/badge/LLM-Groq%20Llama%203.3%2070B-blue.svg)](https://groq.com/)
[![SQLite](https://img.shields.io/badge/Memory-SQLite%203.46-003B57.svg)](https://www.sqlite.org/)

**AI Caddy**, Minecraft dünyanızda size eşlik eden, oyunu öğretmek veya ders vermek yerine **sizinle aynı heyecanı, korkuyu ve yalnızlığı paylaşan** akıllı bir oyun arkadaşıdır ("Kedi"). 

Sesli iletişim (Simple Voice Chat & Vosk Offline STT), anlık duygu değişimleri (Valence-Arousal modeli), kalıcı hafıza (SQLite) ve ultra düşük gecikmeli canlı ses sentezi (Edge TTS / ElevenLabs / StreamElements) ile donatılmıştır.

---

## 🌟 Temel Mimarî Özellikler (8 Aşamalı Sistem)

1. **🚀 Ultra Düşük Gecikme (Streaming LLM + FIFO TTS)**
   * Server-Sent Events (SSE) akışı üzerinden yanıtlar canlı dinlenir; ilk cümle bittiği milisaniyede TTS sentezi başlar. İlk ses duyulma gecikmesi **~2.3 saniyeden ~660 ms'ye** (%72 hızlanma) indirilmiştir.
2. **🛡️ Dayanıklılık (Circuit Breaker + Yedek Ses Motorları)**
   * EdgeTTS, ElevenLabs ve StreamElements arasında otomatik yedekleme (fallback) çalışır.
   * Tüm ses servisleri kesilirse veya API arızası olursa oyun içi **Canned Fallback (Kedi Mırıldanması - `CAT_PURR`)** ses efekti devreye girer.
3. **🧠 Kalıcı Hafıza (SQLite Entegrasyonu)**
   * Sunucu kapansa veya yeniden başlasa bile oyuncuların keşfettiği biyomları, bulduğu efsanevi eşyaları ve atlattığı tehlikeleri unutmaz.
4. **🎭 Psikolojik Duygu Motoru (Valence-Arousal Vektör Modeli)**
   * Katı enum durumları yerine psikolojik **Valence (-1..1)** ve **Arousal (0..1)** koordinat düzleminde çalışır. Üstel yumuşatma (`exponential smoothing`) ile duygu geçişleri son derece doğaldır.
5. **🤖 Multi-Agent Tek Çağrı Taktiksel Analiz**
   * Groq 70B modelinden tek çağrıda yapılandırılmış JSON olarak; önce **durum analizi (`durum_analizi`)**, sonra **iç düşünce (`ic_dusunce`)** ve en sonda **replik (`final_replik`)** üretilir.
6. **👥 Çok Oyunculu UUID Bazlı İzolasyon**
   * Her oyuncunun sohbet geçmişi, duygu vektörü ve hafızası UUID bazında bağımsızdır.
7. **🔎 Gözlemlenebilirlik (Yapılandırılmış JSONL Audit Logları)**
   * Mod yöneticileri için `logs/ai_caddy_events.jsonl` dosyasına makine tarafından okunabilen tam denetim logları kaydedilir.
8. **📊 Doğrulanmış Performans**
   * Ana sunucu thread'i hiçbir G/Ç işleminden etkilenmez (**Sabit 20.0 TPS**).

---

## 🎮 Komutlar ve Kullanım

| Komut | Açıklama |
| :--- | :--- |
| `/kedi <mesaj>` | Kedi ile yazılı olarak konuşursunuz (Sesli yanıt verir). |
| `/aikedi unut` | Kedi'nin sizinle ilgili konuşma geçmişini sıfırlar. |
| `/aikedi debug aç` / `kapat` | Oyun içi sohbette canlı duygu ve JSON loglarını gösterir/gizler. |
| `/aikedi ses aç` / `kapat` | Kedi'nin seslendirme (TTS) özelliğini açar/kapatır. |

---

## 📚 Dokümantasyon ve Raporlar

* 📊 **[Performans ve Benchmark Raporu (BENCHMARK_REPORT.md)](BENCHMARK_REPORT.md)**: Gecikme, TPS ve bellek testlerinin teknik dökümü.
* 🏗️ **[Sistem Mimarisi (SYSTEM_ARCHITECTURE.md)](SYSTEM_ARCHITECTURE.md)**: Modüllerin ve arayüzlerin detaylı açıklaması.
* 🗺️ **[Geliştirme Yol Haritası (DEVELOPMENT_ROADMAP.md)](DEVELOPMENT_ROADMAP.md)**: Tamamlanan 8 aşamalı geliştirme rehberi.
