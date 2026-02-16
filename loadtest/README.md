# Yük testi (k6)

Bu klasörde [k6](https://k6.io/) ile yazılmış yük testleri var; event ingestion API’sine yönelik.

## Karşılama

Hedefler ve nasıl test edildiği:

| Gereksinim | Nasıl test ediliyor |
|-----------------|---------------------|
| **Ortalama ~2.000 event/sn** | `events-single.js`: 400 VU. `events-bulk.js`: 40 VU × 50 event × 1 iter/s (sleep 1s) ≈ 2K event/sn. |
| **Pik ~20.000 event/sn** | `events-single.js`: 2500 VU peak. `events-bulk.js`: 400 VU × 50 event × 1 iter/s ≈ 20K event/sn. |
| **Çok düşük gecikme** | Threshold: `http_req_duration` p95 < 300 ms, p99 < 500 ms (single). Bulk için p95 < 1 sn. |
| **Yük altında bloklamama** | `http_req_failed` < %1; aşımda test fail olur (sistem 202 dönmeye devam etmeli). |

Sonuçları doğrulamak için test bitiminde k6 çıktısındaki **http_reqs** (bulk’ta event sayısı = req × 50) ve **http_req_duration** değerlerine bakın.

### Örnek çıktıya göre gereksinim karşılığı

| Gereksinim | events-single çıktısı | events-bulk çıktısı |
|------------------|------------------------|----------------------|
| **~2.000 event/sn ortalama** | Single: http_reqs /s ≥ 2.000. | Bulk: 40 VU, sleep(1) → ~40 req/s × 50 ≈ 2K event/sn. |
| **~20.000 event/sn peak** | 2500 VU ile http_reqs /s ~20K. | 400 VU, sleep(1) → ~400 req/s × 50 ≈ 20K event/sn. |
| **Çok düşük gecikme** | p(95)<300ms, p(99)<500ms → threshold’da ✓. | p(95)<1000ms, p(99)<2000ms → ✓. |
| **Bloklama yok** | `http_req_failed` rate=0.00% (veya <%1) → ✓. | Aynı şekilde 0% → ✓. |

Tüm threshold’lar **✓** ve `http_req_failed` düşükse gereksinimler karşılanıyor demektir.

---

## Gereksinim

- **k6** kurulu olmalı:
  - **macOS:** `brew install k6`
  - **Linux:** paket yöneticisi veya [k6 indirme](https://k6.io/docs/get-started/installation/)

Kurulumu doğrulamak için: `k6 version`

---

## Önce uygulama çalışıyor olmalı

Docker ile:

```bash
cd /path/to/insider-one/assessment
docker compose up -d
```

Yerel çalıştırma: `./gradlew bootRun`

---

## Scriptler

| Dosya | Açıklama |
|-------|----------|
| `smoke.js` | Kısa smoke test (5 VU, 15 sn). API'nin cevap verdiğini kontrol eder. |
| `events-single.js` | POST /events. Ortalama ~2K req/sn, peak ~20K req/sn. |
| `events-single-peak20k.js` | POST /events. **Sadece 20K pik odaklı**: 2500 VU, 3 dk pik, `events` rate threshold. |
| `events-bulk.js` | POST /events/bulk. Ortalama ~2K event/sn, peak ~20K event/sn; istek başına 50 event. |
| `events-bulk-peak20k.js` | POST /events/bulk. **Sadece 20K pik odaklı**: 400 VU × 50 event, 3 dk pik, `events` rate threshold. |

---

## Çalıştırma

Proje kökünden (assessment klasöründen):

```bash
# Smoke test (hızlı kontrol)
k6 run loadtest/smoke.js

# Tek event yük testi (varsayılan http://localhost:8080)
k6 run loadtest/events-single.js

# 20K pik odaklı – tek event
k6 run loadtest/events-single-peak20k.js

# Toplu event yük testi
k6 run loadtest/events-bulk.js

# 20K pik odaklı test (sadece pik yükü doğrular)
k6 run loadtest/events-bulk-peak20k.js
```

### Farklı base URL (uzak sunucu veya farklı port)

```bash
BASE_URL=http://localhost:8080 k6 run loadtest/events-single.js
BASE_URL=http://192.168.1.10:8080 k6 run loadtest/events-single.js
```

### Bulk testte istek başına event sayısı

Varsayılan 50. Değiştirmek için:

```bash
EVENTS_PER_REQUEST=100 k6 run loadtest/events-bulk.js
```

### Sonuçları JSON olarak kaydetmek

```bash
k6 run --out json=results.json loadtest/events-single.js
```

Konsol çıktısı özet metrikleri gösterir; detaylı görselleştirme için [Grafana k6](https://grafana.com/docs/k6/latest/set-up/export-results/) kullanılabilir.

---

## Metrikler

k6 çalışma sonunda raporlar:

- **http_reqs** – Toplam istek sayısı
- **http_req_duration** – Yanıt süreleri (avg, min, max, p95, p99)
- **http_req_failed** – Başarısız istek oranı
- **iterations** – Tamamlanan VU iterasyonları
- **vus** – Sanal kullanıcı sayısı

Threshold’lar aşılırsa test başarısız sayılır (exit code ≠ 0).

---

## Nasıl test edilir (adım adım)

### Adım 1: k6 kurulumu (macOS)

1. Homebrew ile kurun:
   ```bash
   brew install k6
   ```
2. Doğrulayın:
   ```bash
   k6 version
   ```
   Sürüm satırı (örn. `0.48.0`) görünmeli.

---

### Adım 2: Uygulamayı ayağa kaldırın

**Seçenek A – Docker (önerilen):**

1. Proje dizinine gidin:
   ```bash
   cd /path/to/insider-one/assessment
   ```
2. Tüm servisleri başlatın:
   ```bash
   docker compose up -d
   ```
3. Uygulamanın hazır olmasını bekleyin (yaklaşık 1–2 dakika). Kontrol:
   ```bash
   curl -s http://localhost:8080/actuator/health | jq .
   ```
   veya tarayıcıda `http://localhost:8080/actuator/health` açın. `"status":"UP"` görmelisiniz.

**Seçenek B – Yerel (Gradle):**

1. Aynı dizinde:
   ```bash
   ./gradlew bootRun
   ```
2. Konsolda "Started AssessmentApplication" görünene kadar bekleyin.
3. Sadece altyapı için: `docker compose up -d postgres zookeeper kafka` çalıştırıp uygulamayı yerelde çalıştırabilirsiniz.

---

### Adım 3: Smoke test (önce bunu çalıştırın)

Amaç: API’nin açık olduğunu ve 202 döndüğünü hızlıca doğrulamak.

**Komut (assessment klasöründen):**

```bash
cd /path/to/insider-one/assessment
k6 run loadtest/smoke.js
```

**Süre:** Yaklaşık 15 saniye.

**Başarılı çıktı örneği:**

```
     ✓ status 202

     checks.........................: 100.00% ✓ 750   ✗ 0
     http_req_duration..............: avg=45ms   min=12ms  med=42ms  max=120ms  p(95)=85ms
     http_req_failed................: 0.00%   ✓ 0     ✗ 750
     http_reqs......................: 750      49.8/s
```

- `http_req_failed: 0.00%` ve `checks: 100%` olmalı.
- En sonda threshold’larda **✓** görünmeli; **✗** varsa smoke test başarısız.

**Hata alırsanız:** "connection refused" → Uygulama 8080’de dinlemiyor; Docker veya `./gradlew bootRun` ile servisi başlatıp health check deneyin.

---

### Adım 4: Tek event yük testi (POST /events)

Gereksinimdeki **~2.000 event/sn ortalama** ve **~20.000 event/sn peak** hedefini tek event ile test eder.

**Komut:**

```bash
cd /path/to/insider-one/assessment
k6 run loadtest/events-single.js
```

**Süre:** Yaklaşık 6 dakika (ısınma → ortalama yük → peak → iniş).

**Çıktıda dikkat edilecekler:**

1. **İlerleme:** Her 10 saniyede bir satır gelir; `vus` önce 400’e, sonra 2500’e çıkar.
2. **Bitiş özeti:**
   - **http_reqs** – Toplam istek; **xxx/s** ortalaması ~2K (ortalama faz) ve peak’te daha yüksek olabilir.
   - **http_req_duration** – p95 < 300 ms, p99 < 500 ms hedeflenir.
   - **http_req_failed** – %0’a yakın olmalı (threshold %1’den az).
3. **Threshold sonucu:** En altta **✓** = geçti, **✗** = gecikme veya hata aşıldı.

---

### Adım 5: Toplu event yük testi (POST /events/bulk)

Her istek 50 event içerir; **event/saniye** cinsinden ~2K ortalama ve ~20K peak hedeflenir.

**Komut:**

```bash
cd /path/to/insider-one/assessment
k6 run loadtest/events-bulk.js
```

**Süre:** Yaklaşık 6 dakika.

**Yorumlama:**

- **http_reqs** = toplam istek; **toplam event** ≈ http_reqs × 50.
- **/s** değeri × 50 ≈ saniyede event sayısı (hedef ~2.000 event/sn ortalama, ~20.000 peak).
- **http_req_duration** bulk için p95 < 1 sn threshold.
- **http_req_failed** %1’in altında olmalı.

---

### Adım 6: Sonuçları özetleme

| Ne kontrol edilir | Nerede bakılır | Hedef (Gereksinim) |
|-------------------|----------------|---------------------|
| Ortalama ~2K event/sn | events-single: http_reqs /s. events-bulk: (http_reqs × 50) / süre | ~2.000/sn |
| Peak ~20K event/sn | Aynı metrikler, peak fazında | ~20.000/sn |
| Düşük gecikme | http_req_duration p95, p99 | single: p95<300ms, p99<500ms |
| Yük altında bloklama yok | http_req_failed | < %1 |

Tüm threshold’lar **✓** ve hata oranı düşükse, yük gereksinimleri karşılanıyor kabul edilir.

---

### Sık karşılaşılan sorunlar

| Sorun | Olası neden | Ne yapılır |
|-------|-------------|------------|
| `connection refused` | Uygulama kapalı veya farklı port | `docker compose ps` veya `./gradlew bootRun` ile 8080’i kontrol edin; `curl http://localhost:8080/actuator/health` deneyin. |
| Çok fazla `http_req_failed` | Sunucu aşırı yüklendi veya Kafka/DB yavaş | VU sayısını düşürün veya testi tekrarlayın; Docker’a daha fazla bellek/CPU verin. |
| Gecikme threshold’u (p95/p99) kırmızı | Ağ veya sunucu yavaş | Yerel makinede normal olabilir; gerekirse `loadtest/events-single.js` içindeki threshold değerlerini geçici artırabilirsiniz. |
| `k6` komutu bulunamıyor | PATH’e eklenmemiş | `brew install k6` tekrar deneyin veya tam yolu kullanın (örn. `/opt/homebrew/bin/k6 run loadtest/smoke.js`). |

---

### Özet komut listesi (kopyala-yapıştır)

Tüm testleri sırayla çalıştırmak için (Terminal, assessment dizininde):

```bash
cd /path/to/insider-one/assessment
k6 run loadtest/smoke.js
k6 run loadtest/events-single.js
k6 run loadtest/events-bulk.js
k6 run loadtest/events-bulk-peak20k.js
k6 run loadtest/events-single-peak20k.js
```

Farklı makinedeki API için:

```bash
BASE_URL=http://SUNUCU_IP:8080 k6 run loadtest/smoke.js
BASE_URL=http://SUNUCU_IP:8080 k6 run loadtest/events-single.js
BASE_URL=http://SUNUCU_IP:8080 k6 run loadtest/events-single-peak20k.js
BASE_URL=http://SUNUCU_IP:8080 k6 run loadtest/events-bulk.js
BASE_URL=http://SUNUCU_IP:8080 k6 run loadtest/events-bulk-peak20k.js
```
