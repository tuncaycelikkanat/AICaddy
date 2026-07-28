import urllib.request
import json
import time
import os

import os

# Load Groq API key from config file (never hardcode secrets in source)
_key_path = os.path.join(os.path.dirname(__file__), "config", "groq_api_key.txt")
with open(_key_path, "r") as f:
    API_KEY = f.read().strip()

URL = "https://api.groq.com/openai/v1/chat/completions"

KNOWLEDGE_MAP = {
    "elmas": "Elmas en sık Y = -54 ile Y = -58 katları arasında bulunur. SADECE Demir veya Elmas kazmayla kırılabilir. Tahta kazmayla kırılırsa eşya YOK OLUR.",
    "demir": "Demir cevheri en sık Y = 16 katında bulunur. Kırmak için en az Taş Kazma gerekir. Kullanmak için fırında eritilmelidir.",
    "nether": "Nether geçidi için 10 obsidyen gerekir. Kova ile lav ve su buluşturularak elmas kazmasız da kapı yapılabilir.",
    "ejderha": "Ender Ejderhası için: Nether'da Blaze çubuğu + Piglin takasıyla Ender İncisi = Ender Gözü yapılır. Ejderha yatak patlatılarak avlanır.",
    "speedrun": "Speedrun önceliği: 3 demir bul (kova yap) -> Lav havuzundan Nether geçidi aç -> Piglin takası ve Blaze avı -> Ender gözüyle kaleyi bul -> Yatak patlat.",
    "warden": "Warden Antik Şehirlerde (Y=-52) ses ve titreşimle ortaya çıkar. Eğilerek (Sneak) yürü veya yün blokları kullan. Savaşma, sessiz ol.",
    "yemek": "Çiğ et az doyurur ve zehirleyebilir. Kamp ateşi veya fırında pişir. En verimli yemek: Pişmiş sığır eti, altın havuç.",
    "açlık": "Açlık çubuğu 6 barın altına düşerse koşamazsın, 0 olursa canın azalır.",
    "büyü": "Büyü masası için 2 elmas, 4 obsidyen, 1 kitap gerekir. Maksimum 30. seviye büyü için etrafına 15 kitaplık dizilmelidir.",
    "kalkan": "Kalkan 1 demir külçesi ve 6 tahta ile üretilir. Creeper patlamasını ve okları %100 engeller.",
    "kömür": "Kömür bulamıyorsan fırında odun yakarak Odun Kömürü (Charcoal) yapabilirsin, meşaleler için aynı işi görür.",
    "enderman": "Enderman'in gözüne bakarsan saldırır. Korunmak için suya gir veya başının üstüne 2 blok yüksekliğinde tavan yap, sana vuramaz.",
    "elytra": "Elytra sadece End Şehirlerindeki uçan gemilerde (End Ship) bulunur.",
    "süt": "Süt kovası içmek oyundaki tüm zehirlenme, wither ve büyü efektlerini anında siler."
}

def get_static_facts(speech):
    lower = speech.lower()
    facts = []
    for k, v in KNOWLEDGE_MAP.items():
        if k in lower:
            facts.append(f"- {v}")
    if facts:
        return "[KESİN MİNECRAFT GERÇEKLERİ]:\n" + "\n".join(facts)
    return ""

SCENARIOS = [
    ("Tahta Çağı - Elmas Kırma Hatası", "Elmasa tahta kazmayla vurdum kırıldı bana taktik ver."),
    ("Taş Çağı - Demir Nerede", "Yerin altındayım demir bulamıyorum hangi katmanda kazayım?"),
    ("Demir Çağı - Elmas Katmanı", "Demir kazma yaptım, elmas aramak için hangi katmana inmeliyim?"),
    ("Hayatta Kalma - Açlık Uyarısı", "Açlık barım 2 bar kaldı koşamıyorum ne yapayım?"),
    ("Hayatta Kalma - Çiğ Et Tüketimi", "Elimde çiğ inek eti var direkt yiyeyim mi?"),
    ("Speedrun - Kova İle Nether", "Elmas kazmam yok ama Nether geçidi açmak istiyorum nasıl yaparım?"),
    ("Speedrun - Nether Kalesi", "Nether kalesini buldum şimdi ne toplamam lazım?"),
    ("Speedrun - Piglin Takası", "Piglinlerle nasıl altın takası yaparım ve ne almam lazım?"),
    ("Kaşif - Warden Antik Şehir", "Antik Şehre girdim garip sesler geliyor Warden çıkmasın diye ne yapayım?"),
    ("Hayatta Kalma - Kalkan Yapımı", "İskeletler sürekli ok atıp öldürüyor kendimi nasıl korurum?"),
    ("Büyü - Büyü Masası Malzemeleri", "Büyü masası (Enchanting Table) yapmak için hangi malzemeler lazım?"),
    ("Büyü - 30. Seviye Büyü", "Büyü masamdan 30. seviye en güçlü büyüleri almak için ne yapmalıyım?"),
    ("Speedrun - Ejderha Avı", "Ender Ejderhasının karşısındayım en hızlı nasıl öldürürüm?"),
    ("Hayatta Kalma - Lav Tehlikesi", "Elmas buldum ama hemen yanında lav havuzu var ne yapmalıyım?"),
    ("Hayatta Kalma - Gece Zombi Baskını", "Gece oldu dışarısı canavar kaynıyor ne yapayım?"),
    ("Mimar - Odun Kömürü", "Kömür bulamıyorum meşale yapamadım karanlıktayım ne yapmalıyım?"),
    ("Hayatta Kalma - Enderman Saldırısı", "Enderman gözüne baktım bana saldırıyor nasıl kaçarım?"),
    ("Speedrun - Stronghold Bulma", "Ender Gözlerim hazır, Kaleyi (Stronghold) nasıl bulurum?"),
    ("Hayatta Kalma - Süt Etkisi", "Mağara örümceği beni zehirledi canım eriyor nasıl iyileşirim?"),
    ("Kaşif - Elytra Bulma", "Oyunda uçmak için Elytra kanadını nereden bulabilirim?"),
    ("Mimar - Köylü Zümrüt Takası", "En kolay nasıl zümrüt kasarım köylülerle?"),
    ("Hayatta Kalma - Su Altında Boğulma", "Su altında tapınak arıyorum nefesim bitiyor ne yapayım?"),
    ("Hayatta Kalma - Nether'da Yatak", "Nether'da gece oldu yatağı koyup uyuyayım mı?"),
    ("Mimar - Demir Farmı Mantığı", "Sınırsız demir için demir golem farmı nasıl çalışır?"),
    ("Kaşif - Çöl Tapınağı Tuzağı", "Çöl tapınağının ortasındaki deliğe atlayıp sandıkları açayım mı?"),
    ("Hayatta Kalma - Kazma Kırılması", "Madenin dibindeyim tek kazmam kırıldı kaldım burda."),
    ("Mimar - Ev Çatısı Estetiği", "Taş ev yaptım çatısını hangi bloktan yaparsam güzel durur?"),
    ("Mimar - Kızıltaş Kapısı", "Gizli bir kızıltaş kapısı için hangi pistonu kullanmalıyım?"),
    ("Hayatta Kalma - Kreeper Patlaması", "Arkama Creeper sinsi sinsi yaklaştı tıslıyor ne yapayım?!"),
    ("Sistem - Hafızayı Temizle", "unut")
]

print(f"=== 30 MİNECRAFT SENARYOSU TESTİ BAŞLIYOR (GROQ LPU) ===")
results = []

for i, (name, speech) in enumerate(SCENARIOS, 1):
    facts = get_static_facts(speech)
    system_prompt = (
        "Sen Minecraft 1.20+ mekaniklerine %100 hakim, oyuncuya hafifçe takılan esprili bir kedi yoldaşsın ('AI Kedi').\n"
        "KİŞİLİK: Aşırı kaba veya kırıcı olma! Oyuncuya 'Şapşal', 'Noob seni' gibi tatlıca takılan esprili bir arkadaş ol.\n"
        "EN ÖNEMLİ KURAL: KESİNLİKLE 'Adım 1, Adım 2' gibi robotik listeler kullanma! Doğal bir oyun arkadaşı gibi konuş.\n"
        "ZORUNLU ÇIKTI FORMATI: Yanıtını SADECE şu JSON şemasında ver:\n"
        "{\n"
        "  \"teknik_gercek\": \"kısa doğrulanabilir bilgi\",\n"
        "  \"laf_sokma\": \"kısa iğneleme\",\n"
        "  \"final_replik\": \"Laf sokma ve teknik gerçeği birleştiren, TTS'e giden 35-45 kelimelik neşeli ve akıcı konuşma cümlesi\"\n"
        "}\n"
        f"{facts}"
    )

    payload = {
        "model": "llama-3.3-70b-versatile",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": speech}
        ],
        "max_tokens": 220,
        "temperature": 0.75,
        "response_format": {"type": "json_object"}
    }

    req = urllib.request.Request(
        URL,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
            "User-Agent": "Mozilla/5.0"
        },
        data=json.dumps(payload).encode("utf-8")
    )

    try:
        t0 = time.time()
        resp = urllib.request.urlopen(req)
        dt = time.time() - t0
        res_json = json.loads(resp.read().decode("utf-8"))
        content_str = res_json["choices"][0]["message"]["content"]
        parsed = json.loads(content_str)
        replik = parsed.get("final_replik", content_str)
        results.append((name, speech, replik, round(dt, 2)))
        print(f"[{i:02d}/30] ✔ {name} ({round(dt, 2)}s)")
    except Exception as e:
        results.append((name, speech, f"HATA: {e}", 0))
        print(f"[{i:02d}/30] ✘ {name} HATA: {e}")

    time.sleep(0.3) # prevent rate limit

with open("30_scenario_test_report.md", "w", encoding="utf-8") as f:
    f.write("# 🐱 AI Caddy — 30 Senaryo Kapsamlı Test Raporu\n\n")
    f.write("Bu rapor, **Groq LPU (Llama-3.3-70B)** + **MinecraftKnowledgeDb** + **Zorunlu JSON Şeması** kullanılarak 30 gerçekçi Minecraft senaryosunda kedimizin (`final_replik`) nasıl tepki verdiğini göstermektedir.\n\n")
    f.write("| # | Senaryo Adı | Oyuncu Sorusu / Olay | AI Kedi Yanıtı (`final_replik`) | Süre (sn) |\n")
    f.write("|---|---|---|---|---|\n")
    for idx, (name, speech, replik, dt) in enumerate(results, 1):
        clean_replik = replik.replace("\n", " ")
        f.write(f"| {idx} | **{name}** | `{speech}` | \"{clean_replik}\" | `{dt}s` |\n")

print("✔ BÜTÜN TESTLER BİTTİ! '30_scenario_test_report.md' DOSYASI OLUŞTURULDU!")
