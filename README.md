# Insider One – Event Ingestion & Metrik Platformu

**Java 21**, **Spring Boot 3**, **Kafka** ve **PostgreSQL 16** ile event ingestion: ortalama ~2K, pik ~20K event/sn hedefleniyor; near real-time ingestion ve toplu metrikler.

---

**İçindekiler:** [Mimari](#mimari-özet) · [Tasarım](#tasarım-kararları-ve-ödünler) · [Teknolojiler](#teknoloji-yığını) · [Çalıştırma](#nasıl-çalıştırılır) · [API](#api-uç-noktaları) · [Yük testi](#yük-testi) · [Örnek istekler](#örnek-istekler) · [Dayanıklılık](#dayanıklılık-ve-yük-yönetimi) · [Veritabanı](#veritabanı-tasarımı) · [Proje yapısı](#proje-yapısı)

---

## Mimari Özet

```
                ┌──────────────────────────────────────────────────────────┐
                │                    API Katmanı                            │
                │   POST /events  |  POST /events/bulk  |  GET /metrics   │
                │   ┌──────────┐                                         │
                │   │ Doğrulama │  (Kafka buffer ile backpressure)        │
                │   │ (Jakarta) │                                         │
                │   └────┬─────┘                                         │
                └────────┼───────────────────────────────────────────────┘
                         │
                    202 Accepted (event Kafka'da kalıcı, acks=all)
                         │
                ┌────────┴──────────────────────────────┐
                │          Kafka Producer                │
                │  Tekil: sync .get() + CB + Retry       │
                │  Toplu: CompletableFuture + CB         │
                └────────┬──────────────────────────────┘
                         │
                events-ingestion topic
                (6 partition, user_id ile anahtarlı)
                         │
                ┌────────┴──────────────────────────────┐
                │          Kafka Consumer                │
                │  3 thread, poll başına 1000 kayıt      │
                │  ErrorHandlingDeserializer + DLT       │
                └────────┬──────────────────────────────┘
                         │                    │
        ┌────────────────┴─────────┐    ┌─────┴──────┐
        │  Tek DB Transaction      │    │    DLT     │
        │ ┌───────┐ ┌───────────┐  │    │ (Dead      │
        │ │ Inbox │ │  Events   │  │    │  Letter    │
        │ │(Dedup)│ │(Batch INS)│  │    │  Topic)    │
        │ └───────┘ └───────────┘  │    └────────────┘
        └──────────────────────────┘
                         │
             ┌───────────┴───────────┐
             │ Materialized View     │
             │ (Dakikada bir yenilenir) │
             └───────────┬───────────┘
                         │
                   GET /metrics
```

### İstek Akışı

1. **İstemci** event payload ile `POST /events` gönderir.
2. **Doğrulama** geçersiz payload’ları anında 400 ile reddeder.
3. **Circuit Breaker** Kafka’nın sağlıklı olduğunu kontrol eder.
4. **Retry** produce çağrısını sarar (1 retry, sabit 200ms).
5. Event `acks=all` ile `events-ingestion` topic’ine yazılır.
6. **Sync `.get()`** Kafka onayını bekler; 202 dönmeden önce event kalıcı olarak replike edilmiş olur.
7. **202 Accepted** = event Kafka diskinde garanti.
8. **Kafka Consumer** (3 thread) toplu 1000 kayıt okur, **Inbox** ile duplicate kontrolü yapar.
9. Yeni event’ler **tek transaction** ile `events` ve `inbox` tablolarına yazılır.
10. **Materialized View** metrik API’si için dakikada bir yenilenir.

**Toplu istekler (`POST /events/bulk`):** Tüm event’ler `CompletableFuture.allOf()` ile paralel Kafka’ya gönderilir; 1000 event sıralı ~5 sn yerine ~5–10 ms’de tamamlanır.

---

## Tasarım Kararları ve Ödünler

### Veritabanı: PostgreSQL

Inbox sayesinde idempotency’yi tek transaction’da çözmek, event’leri günlük partition’lara bölmek ve üstüne materialized view ile basit agregasyonlar koymak istedim. PostgreSQL bunu hazır veriyor (ACID, partitioning, JSONB, güçlü Spring/Flyway/Testcontainers desteği), bu yüzden ClickHouse yerine burada PostgreSQL’i seçtim.

### Yük altında bloklamama: Backpressure

Ingestion near real-time olmalı ve yük altında bloklamamalı. Kafka producer buffer (64MB) trafik patlamalarını emer; buffer dolunca `max.block.ms` (2s) ile bloklama olur (virtual thread ile ucuz). Circuit breaker yalnızca Kafka gerçekten kapalıysa 503 döner.

### Toplu gönderim: Paralel ack

`POST /events/bulk` en fazla 1000 event kabul eder. Tüm ack’ler `CompletableFuture.allOf()` ile paralel beklenir; 1000 event için ~5–10 ms.

### Event akışı: Kafka buffer

API, DB yazmasını beklemeden 202 döner (düşük gecikme). Kafka 20K/sn patlamayı emer; consumer sürdürülebilir hızda işler. Producer ve consumer bağımsız ölçeklenir; consumer kapalıyken event’ler Kafka’da kalır.

### Idempotency: Inbox

Kafka "en az bir kez" teslimat sunar. Inbox tablosu: idempotency key varsa atla, yoksa inbox + event’i aynı TX’de ekle. Idempotency key: `SHA-256(event_name + user_id + timestamp + campaign_id)`.

### Metrikler: Materialized view

Günde ~172M event’te anlık agregasyon saniyeler sürer. Materialized view `(event_name, channel, saat)` başına COUNT ve COUNT DISTINCT önceden hesaplar; REFRESH CONCURRENTLY ile bloklamadan yenilenir; parametreye bağlı (dakikada bir) yenileme. Zaman kovaları toplamında unique_user_count yaklaşıktır.

### Runtime: Virtual thread (Java 21)

20K eşzamanlı istekte platform thread tükenmesini önlemek için virtual thread kullanılıyor; I/O’da (Kafka, DB) park maliyeti düşük.

### Persistence: JdbcTemplate

Batch INSERT için `batchUpdate()` kullanılıyor; partitioned table ve materialized view refresh ham SQL ile. Write-heavy, append-only iş yükünde ORM katmanı yok.

---

## Teknoloji Yığını

| Bileşen | Teknoloji | Amaç |
|---------|-----------|------|
| **Runtime** | Java 21 + Virtual Threads | Yüksek eşzamanlılık |
| **Framework** | Spring Boot 3.5.x | Uygulama iskeleti |
| **Mesajlaşma** | Apache Kafka + Spring Kafka | Event buffer |
| **Veritabanı** | PostgreSQL 16 | Kalıcı depolama, partitioning |
| **Dayanıklılık** | Resilience4j | Circuit breaker; producer retry sabit 200ms |
| **Migration** | Flyway | Şema sürümleme |
| **API Dokümantasyonu** | SpringDoc OpenAPI | Swagger UI |
| **Gözlemlenebilirlik** | Micrometer + Prometheus | Metrik dışa aktarma |
| **Build** | Gradle | Bağımlılık yönetimi |
| **Konteyner** | Docker + Docker Compose | Yerel geliştirme ve deployment |
| **Test** | JUnit 5 + Testcontainers | Birim ve entegrasyon testleri |

---

## Nasıl Çalıştırılır

### Ön koşullar

- Docker ve Docker Compose
- Java 21 (yalnızca yerel geliştirme için)

### Seçenek 1: Docker Compose (Önerilen)

```bash
# Depoyu klonlayın
git clone <depo-url>
cd assessment

# Tüm servisleri başlatın (PostgreSQL, Kafka, Zookeeper, Uygulama)
docker compose up -d

# Servislerin sağlıklı olmasını bekleyin (yaklaşık 30–60 saniye)
docker compose ps

# Uygulama logları
docker compose logs -f app
```

Uygulama adresleri:

- **API:** http://localhost:8080
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Health:** http://localhost:8080/actuator/health
- **Prometheus:** http://localhost:8080/actuator/prometheus

#### macOS’ta Docker ile çalıştırma ve test

1. **Docker Desktop** kurulu ve çalışır olsun.

2. **Proje dizinine gidin:**
   ```bash
   cd /path/to/insider-one/assessment
   ```

3. **Tüm servisleri başlatın:**
   ```bash
   docker compose up -d
   ```
   İlk seferde imajlar iner ve uygulama build edilir; 1–3 dakika sürebilir.

4. **Servislerin hazır olmasını bekleyin:**
   ```bash
   docker compose ps
   ```
   `insider-app` satırında `healthy` görünene kadar (yaklaşık 30–60 saniye) bekleyin.

5. **Log takibi (isteğe bağlı):**
   ```bash
   docker compose logs -f app
   ```
   Çıkmak için `Ctrl+C`.

6. **API testleri (Terminal):**

   **Health:**
   ```bash
   curl -s http://localhost:8080/actuator/health | jq .
   ```

   **Tek event:**
   ```bash
   curl -s -X POST http://localhost:8080/events \
     -H "Content-Type: application/json" \
     -d '{"event_name":"product_view","user_id":"user_123","timestamp":1771156800,"channel":"web","campaign_id":"cmp_987"}' | jq .
   ```

   **Metrikler:**
   ```bash
   curl -s "http://localhost:8080/metrics?event_name=product_view&from=1771113600&to=1771200000" | jq .
   ```

7. **Tarayıcı:** http://localhost:8080/swagger-ui.html ile tüm endpoint’leri deneyebilirsiniz.

8. **Durdurmak:**
   ```bash
   docker compose down
   ```
   Veritabanı dahil her şeyi silmek için: `docker compose down -v`

### Seçenek 2: Yerel Geliştirme

```bash
# Yalnızca altyapı
docker compose up -d postgres zookeeper kafka

# Uygulamayı yerelde çalıştırın
./gradlew bootRun
```

### Servisleri Durdurma

```bash
docker compose down        # Durdur
docker compose down -v     # Durdur ve volume'ları sil (temiz başlangıç)
```

### Uygulama yapılandırması

**app.** (application.yaml)

| Ayar | Varsayılan | Açıklama |
|------|------------|----------|
| `app.kafka.topic.events-ingestion` | events-ingestion | Ingestion ve DLT topic adı (DLT: .DLT soneki). |
| `app.scheduler.materialized-view-refresh-rate` | 60000 | MV yenileme aralığı (ms). 60000 = 1 dk. |
| `app.scheduler.materialized-view-refresh-initial-delay` | 30000 | İlk MV refresh gecikmesi (ms); uygulama açılışından sonra. |
| `app.scheduler.inbox-cleanup-rate` | 3600000 | Inbox temizleme aralığı (ms). 3600000 = 1 saat. |
| `app.scheduler.inbox-retention-days` | 7 | Inbox kayıtlarının tutulacağı gün. |
| `app.scheduler.mv-retention-days` | 7 | Metrik view’da son N gün. Açılışta app_config'e yazılır; değiştirince uygulamayı yeniden başlatın. |

**MV yenileme:** Varsayılan 1 dk çoğu senaryo için yeterli. 30 sn’e indirmek mümkün; metrikler tazelenir ama REFRESH DB’de yük oluşturur, aralığın tek refresh süresinden büyük kalması iyi olur.

**Kafka (spring.kafka.\*)** — Producer: acks=all, retries=3, retry.backoff.ms=200, max.block.ms=2000, buffer.memory=67108864 (64MB), linger.ms=20, batch.size=65536, enable.idempotence=true. Consumer: group-id=event-ingestion-group, max.poll.records=1000, max.poll.interval.ms=300000. Listener: type=batch, ack-mode=manual, concurrency=3.

**Resilience4j** — retry.instances.kafkaProducer: max-attempts=2 (1 retry), wait-duration 200ms, enable-exponential-backoff=false. circuitbreaker.instances.kafkaProducer: sliding-window-size=10, failure-rate-threshold=50, wait-duration-in-open-state=30s, permitted-number-of-calls-in-half-open-state=3.

**Profil docker:** Docker Compose ile SPRING_PROFILES_ACTIVE=docker; application-docker.yaml ile spring.datasource.url (postgres:5432) ve spring.kafka.bootstrap-servers (kafka:29092) override edilir.

---

## API Uç Noktaları

| Method | Endpoint | Açıklama |
|--------|----------|----------|
| POST | `/events` | Tek event ingestion |
| POST | `/events/bulk` | Toplu event (en fazla 1000) |
| GET | `/metrics` | Toplu metrik sorgusu |
| GET | `/swagger-ui.html` | Swagger UI |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrikleri |

---

## Yük Testi

[k6](https://k6.io/) ile tek event ve toplu event endpoint’lerine yük testi yapabilirsiniz. Script’ler `loadtest/` klasöründedir.

### Gereksinim

- k6 kurulu olmalı. **macOS:** `brew install k6`. Diğer: [k6 kurulum](https://k6.io/docs/get-started/installation/).

### Hızlı başlangıç

Uygulama çalışıyorken (Docker veya `./gradlew bootRun`):

```bash
# Smoke test (5 VU, 15 sn)
k6 run loadtest/smoke.js

# Tek event yük testi (~2K req/s ortalama, ~20K peak)
k6 run loadtest/events-single.js

# 20K pik odaklı – tek event (sadece pik doğrulama)
k6 run loadtest/events-single-peak20k.js

# Toplu event (istek başına 50 event)
k6 run loadtest/events-bulk.js

# 20K pik odaklı – bulk (sadece pik doğrulama)
k6 run loadtest/events-bulk-peak20k.js
```

Farklı base URL (macOS/Linux):

```bash
BASE_URL=http://localhost:8080 k6 run loadtest/events-single.js
```

### 20K pik odaklı testler

`events-*-peak20k.js` scriptleri **sadece pik yükü** doğrular; ortalama yük aşaması yok. Kısa ramp-up, 3 dakika pik sürdürme, `events` custom metriği ile event/sn ölçülür.

| Script | Hedef | Senaryo |
|--------|-------|---------|
| `events-single-peak20k.js` | ~20K req/sn | 2500 VU, 3 dk pik |
| `events-bulk-peak20k.js` | ~20K event/sn | 400 VU × 50 event, 3 dk pik |

**Çıktıyı yorumlama:**
- **events** satırında `xxx/s` → saniyede event sayısı (hedef ~20K)
- Threshold `rate>15000` → ramp dahil ortalama en az ~15K event/sn
- Tüm threshold’lar ✓ ve `http_req_failed` %0’a yakınsa pik hedef karşılanıyor

Ayrıntılar için [loadtest/README.md](loadtest/README.md).

---

## Postman ile İstekler

Hazır Postman koleksiyonu ile tüm endpoint’leri test edebilirsiniz.

### Koleksiyonu içe aktarma

1. Postman’ı açın.
2. **Import** → **File** → `assessment/postman/Insider-One-API.postman_collection.json` seçin.
3. **Insider One - Event Ingestion API** koleksiyonu eklenir.

### Ortam

- Koleksiyonda **baseUrl** değişkeni kullanılır; varsayılan: `http://localhost:8080`.
- Farklı host/port için: koleksiyonu düzenleyip **Variables** → `baseUrl` güncelleyin.

### Koleksiyondaki istekler

| Klasör / İstek | Açıklama |
|----------------|----------|
| **Health & Actuator** | Health Check, Prometheus Metrics |
| **Events** | POST /events (tek, minimal, bulk), validation hatası örneği |
| **Metrics** | GET /metrics saatlik ve günlük + channel |

### Hızlı test sırası

1. Uygulamayı başlatın (`docker compose up -d` veya `./gradlew bootRun`).
2. **Health Check** ile `status: UP` doğrulayın.
3. **POST /events** ile birkaç event gönderin.
4. **1–2 dakika bekleyin** (Kafka → consumer → DB → materialized view yenileme).
5. **GET /metrics** ile aynı `event_name` ve uygun `from`/`to` ile sorgulayın.

Event’te `timestamp: 1771156800` kullandıysanız metrics için `from=1771113600`, `to=1771200000` uygundur.

---

## Örnek İstekler

### POST /events — Tek Event

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_name": "product_view",
    "channel": "web",
    "campaign_id": "cmp_987",
    "user_id": "user_123",
    "timestamp": 1771156800,
    "tags": ["electronics", "homepage", "flash_sale"],
    "metadata": {
      "product_id": "prod-789",
      "price": 129.99,
      "currency": "TRY",
      "referrer": "google"
    }
  }'
```

**Yanıt (202 Accepted):**
```json
{
  "status": "accepted",
  "acceptedCount": 1,
  "message": "Event queued for processing"
}
```

### POST /events/bulk — Toplu Event

```bash
curl -X POST http://localhost:8080/events/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "events": [
      {
        "event_name": "product_view",
        "user_id": "user_123",
        "timestamp": 1771156800,
        "channel": "web",
        "campaign_id": "cmp_987"
      },
      {
        "event_name": "add_to_cart",
        "user_id": "user_456",
        "timestamp": 1771156810,
        "channel": "mobile",
        "campaign_id": "cmp_789"
      }
    ]
  }'
```

**Yanıt (202 Accepted):**
```json
{
  "status": "accepted",
  "acceptedCount": 2,
  "message": "Events queued for processing"
}
```

### GET /metrics — Toplu Metrikler

```bash
# Saatlik (varsayılan)
curl "http://localhost:8080/metrics?event_name=product_view&from=1771113600&to=1771200000"

# Günlük + channel filtresi
curl "http://localhost:8080/metrics?event_name=product_view&from=1771113600&to=1771200000&channel=web&group_by=daily"
```

**Yanıt (200 OK):**
```json
{
  "event_name": "product_view",
  "total_count": 15234,
  "unique_user_count": 8721,
  "time_range": { "from": 1771113600, "to": 1771200000 },
  "channel": null,
  "breakdowns": [
    { "bucket": "2026-02-15T12:00:00Z", "total_count": 1523, "unique_user_count": 872 },
    { "bucket": "2026-02-15T13:00:00Z", "total_count": 1891, "unique_user_count": 1045 }
  ]
}
```

### Doğrulama Hatası Örneği

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"user_id": "user_123"}'
```

**Yanıt (400 Bad Request):** `event_name` ve `timestamp` eksikliği için hata detayları döner.

### Metrikler neden 0 dönüyor?

1. **Gecikme:** Metrikler materialized view’dan gelir; view dakikada bir yenilenir. Event gönderdikten **1–2 dakika** bekleyip tekrar deneyin.
2. **Zaman aralığı:** `from` ve `to`, event’teki `timestamp` değerini kapsamalı. Örnek: `timestamp: 1771156800` → `from=1771113600`, `to=1771200000`. Event timestamp’i mevcut partition aralığında olmalı (V1 migration şu anki ay + 2 ay için partition oluşturur).

---

## Dayanıklılık ve Yük Yönetimi

### Backpressure

Hedef: near real-time, yük altında bloklamama.

- **Kafka producer buffer (64MB)** trafik patlamalarını reddetmeden emer.
- **max.block.ms (2s):** Buffer dolunca producer bloklar; virtual thread ile maliyet düşük.
- **Circuit Breaker:** Kafka gerçekten kapalıysa 503 + `Retry-After`.
- **Sonuç:** Aşırı yükte sistem yumuşak yavaşlar, sert red (429) yok.

### Veri Tutarlılığı

- **Tek event:** `kafkaTemplate.send().get(1, SECONDS)` — sync ack, 202 = event Kafka diskinde.
- **Toplu:** `CompletableFuture.allOf()` — tüm ack’ler paralel beklenir.
- **acks=all:** Tüm in-sync replica’lara yazılmadan onay dönmez.

### Retry

- **Producer (Resilience4j):** 1 retry (toplam 2 deneme), sabit 200ms bekleme; `.get()` timeout 1s. (Exponential backoff kapalı.)
- **Producer (Kafka):** Broker tarafı 3 retry, `retry.backoff.ms` 200; `max.block.ms` 2s.
- **Consumer:** Üstel backoff — ilk 1s, çarpan 2x, max interval 4s, toplam en fazla 4 sn sonra DLT; partition en fazla 4 sn bloklanır.

### Circuit Breaker

- **Pencere:** 10 çağrı, %50 hata eşiği.
- **Açık süre:** 30 saniye, sonra half-open, 3 test çağrısı.
- **Red:** 503 Service Unavailable + `Retry-After: 30`.

### Dead Letter Topic (DLT)

- **Topic:** `events-ingestion.DLT`
- **Kafka deserialization hataları:** ErrorHandlingDeserializer ile kayıt DLT’ye.
- **Uygulama parse hataları:** Consumer parse edilemeyen kayıtları DLT’ye gönderir.
- **İşleme hataları (DB):** Retry tükendikten sonra DefaultErrorHandler DLT’ye gönderir.
- **Kurtarma:** DLT kayıtları incelenip düzeltmeden sonra tekrar oynatılabilir.

---

## Veritabanı Tasarımı

### Events Tablosu (Range Partitioned)

```sql
events (
  id, event_name, channel, campaign_id, user_id,
  event_timestamp, event_date, tags JSONB, metadata JSONB,
  idempotency_key, created_at
) PARTITION BY RANGE (event_date)
```

### Inbox Tablosu (Idempotency)

```sql
inbox (
  idempotency_key VARCHAR(64) PRIMARY KEY,
  received_at TIMESTAMPTZ
)
-- 7 gün tutma, zamanlanmış job ile temizlenir
```

### Materialized View (Metrikler)

```sql
event_metrics AS SELECT
  event_name, channel, date_hour, event_date,
  COUNT(*), COUNT(DISTINCT user_id)
FROM events
WHERE event_date >= CURRENT_DATE - N * INTERVAL '1 day'
GROUP BY ...
-- @Scheduled ile dakikada bir REFRESH MATERIALIZED VIEW CONCURRENTLY
```

**Son N gün penceresi (`mv-retention-days`):** View yalnızca son N günün event’lerini içerir; REFRESH süresi kabaca sabit kalır. N, `application.yaml`’da `app.scheduler.mv-retention-days` (varsayılan 7). Açılışta `AppConfigSyncRunner` bu değeri `app_config` tablosuna yazar; MV sorgusu N’i oradan okur. N’i değiştirmek için yapılandırmayı güncelleyip uygulamayı yeniden başlatın.

---

## Proje Yapısı

```
assessment/
├── docker-compose.yml
├── Dockerfile
├── postman/
│   └── Insider-One-API.postman_collection.json
├── loadtest/
│   ├── smoke.js
│   ├── events-single.js
│   ├── events-single-peak20k.js
│   ├── events-bulk.js
│   ├── events-bulk-peak20k.js
│   └── README.md
├── build.gradle
├── src/main/
│   ├── java/.../assessment/
│   │   ├── config/
│   │   ├── api/ (controller, dto, exception)
│   │   ├── domain/ (model, service)
│   │   ├── infrastructure/ (kafka, persistence)
│   │   └── scheduler/
│   └── resources/
│       ├── application.yaml
│       ├── application-docker.yaml
│       └── db/migration/
└── src/test/
```
