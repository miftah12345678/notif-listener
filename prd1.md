# PRD — Veyra Notification Monitor

**Status:** Draft V1
**Konsep:** Android Notification Monitoring & Gateway
**Target:** Multi-app, multi-device, offline-first, configurable

Dokumen awal menjadi dasar untuk bagian reliability seperti penyimpanan lokal, deduplication, retry, dan sinkronisasi; sekarang domainnya kita perluas agar tidak terikat pada DANA. 

---

# 1. Tujuan Sistem

Veyra Notification Monitor adalah sistem yang memungkinkan pengguna:

1. Memantau notifikasi dari **satu atau banyak aplikasi Android**.
2. Menentukan aplikasi mana yang boleh dipantau.
3. Mengaktifkan/nonaktifkan monitoring tanpa mengubah kode.
4. Menyimpan notifikasi secara lokal terlebih dahulu.
5. Melakukan sinkronisasi ke server secara reliable.
6. Melihat notifikasi melalui dashboard web.
7. Melakukan filtering dan pencarian.
8. Melakukan parsing notifikasi tertentu menjadi data terstruktur.
9. Mengirim event ke webhook/sistem lain.
10. Mengelola beberapa perangkat Android dari satu server.

**DANA hanya salah satu contoh aplikasi.**

---

# 2. Prinsip Utama

Ada 6 prinsip:

### 1. App-agnostic

Core system tidak boleh menganggap:

```text
DANA = aplikasi utama
```

Core hanya mengenal:

```text
packageName
notification
app configuration
parser
```

---

### 2. Local-first

Notifikasi:

```text
Notification
    ↓
SQLite
    ↓
Sync
```

bukan:

```text
Notification
    ↓
Internet
    ↓
SQLite
```

Ini mempertahankan prinsip reliable storage dari rancangan awal. 

---

### 3. Configurable

Penambahan aplikasi baru sebisa mungkin **tidak memerlukan update APK**.

Contoh:

```text
Tambah DANA
Tambah GoPay
Tambah BRImo
Tambah WhatsApp
Tambah Telegram
```

cukup melalui konfigurasi.

---

### 4. Generic first, specialized second

Semua aplikasi mendapatkan:

```text
Generic Notification
```

Parser khusus hanya jika dibutuhkan.

```text
Generic
   │
   ├── DANA Parser
   ├── GoPay Parser
   ├── BRImo Parser
   └── Custom Parser
```

---

### 5. Server tidak boleh menjadi single point of failure

Server mati:

```text
Android
 ↓
SQLite
 ↓
PENDING
```

Server hidup kembali:

```text
PENDING
 ↓
SYNC
 ↓
SYNCED
```

---

### 6. Tidak mengakses API internal aplikasi

Sistem hanya bekerja dengan notification event yang diberikan Android.

Tidak:

* mengambil session aplikasi;
* mencuri credential;
* reverse-engineering API;
* membaca database internal aplikasi;
* melakukan login otomatis ke aplikasi.

Batasan ini konsisten dengan rancangan awal yang hanya menggunakan NotificationListener. 

---

# 3. Arsitektur Besar

```text
                         ANDROID
┌────────────────────────────────────────────────────────┐
│                                                        │
│  DANA       GoPay       BRImo       WhatsApp      ...  │
│    │           │           │            │              │
│    └───────────┴───────────┴────────────┘              │
│                         │                              │
│                         ▼                              │
│             NotificationListenerService               │
│                         │                              │
│                         ▼                              │
│                  Capture Pipeline                     │
│                         │                              │
│              ┌──────────┴──────────┐                   │
│              │                     │                   │
│          App Registry          Deduplication            │
│              │                     │                   │
│              └──────────┬──────────┘                   │
│                         ▼                              │
│                   Filter Engine                       │
│                         │                              │
│                         ▼                              │
│                    Parser Engine                      │
│                         │                              │
│                         ▼                              │
│                    SQLite/Room                        │
│                         │                              │
│                         ▼                              │
│                    Sync Engine                        │
│                         │                              │
└─────────────────────────┼──────────────────────────────┘
                          │
                         HTTPS
                          │
                          ▼
┌────────────────────────────────────────────────────────┐
│                       VPS                              │
│                                                        │
│                    Express API                         │
│                         │                              │
│        ┌────────────────┼─────────────────┐            │
│        ▼                ▼                 ▼            │
│     Storage         Webhook            Dashboard       │
│                                                        │
└────────────────────────────────────────────────────────┘
```

---

# 4. Komponen Android

## 4.1 Notification Listener

Ini adalah pintu masuk.

Android memberikan:

```text
packageName
notification key
post time
title
text
extras
actions
etc.
```

Kemudian kita normalisasi menjadi:

```json
{
  "packageName": "id.dana",
  "title": "DANA",
  "text": "Anda menerima Rp100.000",
  "postedAt": "2026-10-04T18:20:00+07:00"
}
```

---

# 5. App Registry

Ini bagian yang baru dan penting.

Database:

```text
MonitoredApp
```

Contoh:

```json
{
  "id": "app_01",
  "packageName": "id.dana",
  "appName": "DANA",
  "enabled": true,
  "capture": true,
  "parser": "generic",
  "createdAt": "...",
  "updatedAt": "..."
}
```

Contoh lainnya:

```json
{
  "id": "app_02",
  "packageName": "com.whatsapp",
  "appName": "WhatsApp",
  "enabled": false
}
```

---

# 6. CRUD App

## CREATE

Tambahkan aplikasi.

```text
+ Add Application
```

---

## READ

Lihat aplikasi yang terdaftar.

```text
DANA       ON
GoPay      ON
WhatsApp   OFF
```

---

## UPDATE

Misalnya:

```text
DANA

Monitoring:
ON → OFF

Parser:
Generic → DANA Transaction
```

---

## DELETE

Menghapus konfigurasi monitoring.

**Catatan penting:** menghapus app dari registry **tidak otomatis menghapus histori notifikasi**.

Jadi:

```text
Delete App
      ↓
hapus konfigurasi
      ↓
historical notifications tetap ada
```

Menurutku ini lebih aman.

---

# 7. Bagaimana mengetahui aplikasi yang terpasang?

Android bisa menyediakan daftar aplikasi yang relevan untuk pemilihan.

UI:

```text
ADD APPLICATION

Search...

┌─────────────────────────────┐
│ ○ DANA                      │
│   id.dana                   │
├─────────────────────────────┤
│ ○ GoPay                     │
│   com.gojek.gopay           │
├─────────────────────────────┤
│ ○ WhatsApp                  │
│   com.whatsapp              │
└─────────────────────────────┘

             [ ADD ]
```

User tidak perlu mengetahui:

```text
id.dana
com.whatsapp
```

secara manual.

---

# 8. App Profile

Setiap aplikasi dapat memiliki konfigurasi.

Contoh:

```text
DANA

Status
[ ON ]

Capture
[x] Title
[x] Text
[x] Extras

Parser
[DANA Transaction]

Storage
[x] Save raw notification

Webhook
[x] Enabled
```

Sedangkan:

```text
WhatsApp

Status
[ OFF ]

Parser
[Generic]

Webhook
[OFF]
```

---

# 9. Filter Engine

Filter bekerja setelah notification diterima.

Urutannya:

```text
Notification
      ↓
Package Filter
      ↓
App Enabled?
      ↓
Notification Filter
      ↓
Parser
      ↓
Storage
```

---

## 9.1 App filter

```text
DANA       ALLOW
GoPay      ALLOW
WhatsApp   BLOCK
```

---

## 9.2 Keyword filter

Misalnya:

```text
DANA

Include:
"transfer"
"menerima"
"pembayaran"

Exclude:
"promo"
"voucher"
"diskon"
```

---

## 9.3 Notification type

Kita dapat membangun kategori:

```text
TRANSACTION
MESSAGE
PROMOTION
SYSTEM
AUTHENTICATION
OTHER
```

Tetapi kategori ini **hasil klasifikasi kita**, bukan kategori resmi Android.

---

# 10. Parser Engine

Ini salah satu bagian paling menarik.

Semua notification awalnya:

```text
GenericNotification
```

Kemudian parser bisa menghasilkan:

```text
ParsedNotification
```

Contoh DANA:

```json
{
  "type": "transaction",
  "direction": "incoming",
  "amount": 100000,
  "currency": "IDR"
}
```

GoPay:

```json
{
  "type": "transaction",
  "direction": "outgoing",
  "amount": 25000,
  "currency": "IDR"
}
```

WhatsApp:

```json
{
  "type": "message"
}
```

---

# 11. Jangan jadikan parser bagian dari NotificationListener

Ini penting untuk maintainability.

Jangan:

```text
NotificationListener
 ├── if DANA
 ├── if GoPay
 ├── if BRImo
 ├── if Shopee
 └── if WhatsApp
```

Karena lama-lama menjadi monster.

Lebih baik:

```text
NotificationListener
       ↓
NotificationEvent
       ↓
ParserRegistry
       ↓
Parser
```

Misalnya:

```text
ParserRegistry

id.dana              → DanaParser
com.gojek.gopay      → GoPayParser
com.whatsapp         → GenericParser
unknown               → GenericParser
```

---

# 12. Generic Parser

Aplikasi yang belum memiliki parser khusus tetap bisa digunakan.

Output:

```json
{
  "type": "generic",
  "app": "Unknown App",
  "title": "...",
  "text": "..."
}
```

Dengan demikian:

> **Menambahkan aplikasi ≠ harus membuat parser baru.**

Parser khusus hanya diperlukan kalau kita ingin mengekstrak informasi lebih dalam.

---

# 13. Database Android

Minimal kita memiliki:

### `monitored_apps`

```text
id
package_name
app_name
enabled
capture_enabled
parser_id
created_at
updated_at
```

### `notifications`

```text
id
device_id
app_id
package_name
title
text
raw_extras
posted_at
received_at
fingerprint
category
parsed_data
sync_status
sync_attempts
synced_at
created_at
```

### `sync_queue`

Bisa digunakan jika nanti diperlukan antrean terpisah.

---

# 14. Deduplication

Fingerprint:

```text
packageName
+
notificationKey
+
postedAt
+
title
+
text
```

→ SHA-256

Misalnya:

```text
a31c4f...
```

Database:

```text
fingerprint UNIQUE
```

Maka event yang sama tidak masuk dua kali.

Rancangan awal juga sudah menempatkan deduplication sebagai komponen penting reliability. 

---

# 15. Sync Engine

Setiap notification:

```text
PENDING
```

Kemudian:

```text
PENDING
   ↓
SYNCING
   ↓
SYNCED
```

Jika gagal:

```text
FAILED
   ↓
RETRY
   ↓
SYNCING
```

Backoff:

```text
1 menit
5 menit
15 menit
30 menit
1 jam
...
```

Tidak perlu retry tanpa batas secara agresif.

---

# 16. Batch Sync

Jangan kirim satu request untuk setiap notifikasi.

Buruk:

```text
notif 1 → HTTP
notif 2 → HTTP
notif 3 → HTTP
notif 4 → HTTP
```

Lebih bagus:

```text
notif 1 ┐
notif 2 ├──→ POST /notifications/batch
notif 3 │
notif 4 ┘
```

Contoh:

```json
{
  "deviceId": "dev_001",
  "events": [
    {...},
    {...},
    {...}
  ]
}
```

Ini lebih efisien ketika HP baru online setelah lama offline.

---

# 17. Server

Aku merekomendasikan:

```text
Node.js 22
Express
Zod
Pino
SQLite
EJS
Vanilla JS
```

Untuk V1 **tidak perlu PostgreSQL dulu**.

Server cukup:

```text
server/
├── API
├── Auth
├── Storage
├── Webhook
└── Dashboard
```

---

# 18. API

Minimal:

```http
POST /api/v1/devices/register
```

Register perangkat.

```http
POST /api/v1/notifications/batch
```

Sync notification.

```http
GET /api/v1/notifications
```

List.

```http
GET /api/v1/notifications/:id
```

Detail.

```http
GET /api/v1/apps
```

Aplikasi yang terdaftar.

```http
GET /api/v1/devices
```

Daftar perangkat.

```http
GET /api/v1/stats
```

Statistik.

---

# 19. Device Management

Satu akun bisa mempunyai:

```text
Devices

● HP Utama
  Android
  Online

● HP Toko
  Android
  Online

● HP Backup
  Android
  Offline
```

Setiap notification mempunyai:

```text
deviceId
```

Jadi server tahu sumbernya.

---

# 20. Dashboard

Sidebar:

```text
┌─────────────────────┐
│ VEYRA MONITOR       │
├─────────────────────┤
│ Dashboard           │
│ Notifications       │
│ Applications        │
│ Devices             │
│ Webhooks            │
│ Statistics          │
│ Settings            │
└─────────────────────┘
```

---

## Dashboard

```text
NOTIFICATION MONITOR

Applications       8
Active Apps        5
Devices            3
Notifications      12,483

──────────────────────────

Today's Notifications

DANA          123
GoPay          82
BRImo          47
WhatsApp        0
```

---

# 21. Halaman Applications

```text
APPLICATIONS

[ + ADD APPLICATION ]

App              Package              Status

DANA             id.dana              ● ON
GoPay            com.gojek.gopay      ● ON
BRImo            ...                   ● ON
WhatsApp         com.whatsapp          ○ OFF
```

Action:

```text
Edit
Enable/Disable
Delete
View Notifications
```

---

# 22. Halaman Notifications

Filter:

```text
[ All Apps ▼ ]
[ All Types ▼ ]
[ Today ▼ ]

🔍 Search
```

Kolom:

```text
Time
App
Category
Title
Text
Sync
```

---

# 23. Detail Notification

```text
NOTIFICATION DETAIL

Application
DANA

Package
id.dana

Device
HP Utama

Received
04 Oct 2026 18:24:01

Category
TRANSACTION

────────────────────

TITLE

DANA

TEXT

Anda menerima Rp100.000

────────────────────

PARSED DATA

Direction
INCOMING

Amount
Rp100.000

────────────────────

SYNC

SYNCED
```

Dan ada:

```text
RAW EXTRAS
```

untuk debugging.

---

# 24. Webhook Engine

Webhook tidak harus aktif untuk semua aplikasi.

Misalnya:

```text
DANA
Webhook: ON

GoPay
Webhook: ON

WhatsApp
Webhook: OFF
```

Event:

```text
notification.received
notification.parsed
```

Contoh:

```json
{
  "event": "notification.parsed",
  "timestamp": "...",
  "device": {
    "id": "dev_001"
  },
  "application": {
    "packageName": "id.dana",
    "name": "DANA"
  },
  "data": {
    "type": "transaction",
    "direction": "incoming",
    "amount": 100000
  }
}
```

---

# 25. Security

Ini harus kita pikirkan dari awal.

Android tidak boleh sekadar:

```text
POST https://server/api/notifications
```

tanpa autentikasi.

Minimal:

```text
Device
   ↓
Device ID
+
Device Secret / Token
   ↓
HTTPS
   ↓
Server
```

Server juga harus memvalidasi:

```text
device valid?
token valid?
payload valid?
timestamp valid?
duplicate?
```

Dan jangan menyimpan secret secara plaintext di database aplikasi jika bisa dihindari.

---

# 26. Data Sensitif

Karena notifikasi bisa berisi:

* nominal transaksi;
* nama;
* pesan;
* OTP;
* kode;
* informasi akun;

kita harus memberi konfigurasi capture.

Misalnya:

```text
Capture Mode

○ Full
○ Metadata Only
○ Disabled
```

**Full:**

```text
title
text
extras
```

**Metadata Only:**

```text
package
timestamp
category
```

Ini berguna untuk aplikasi seperti WhatsApp/Telegram yang mungkin tidak ingin seluruh isi notifikasinya disimpan.

---

# 27. Permission & Privacy

Saat pertama kali aplikasi dijalankan:

```text
Notification Access

Veyra Notification Monitor
needs notification access to monitor
notifications from applications you select.

[ OPEN SETTINGS ]
```

Kemudian user sendiri memberikan permission Android.

Kita tidak boleh menganggap semua aplikasi otomatis bisa dibaca.

---

# 28. Recovery

Kalau HP restart:

```text
BOOT
 ↓
Application starts
 ↓
Database available
 ↓
Sync pending events
```

Dokumen awal memang mengusulkan recovery setelah reboot dan background sync. 

Tetapi kita perlu hati-hati: **NotificationListenerService sendiri dikelola oleh Android**, sehingga desain kita tidak boleh bergantung pada asumsi bahwa service bisa kita “paksa hidup” dengan cara tertentu. Kita akan menguji perilaku nyata pada perangkat target.

---

# 29. Foreground Service

Aku juga tidak ingin langsung menjadikan Foreground Service sebagai solusi untuk semuanya.

Target awal:

```text
NotificationListenerService
+
Room
+
WorkManager
```

Kemudian kalau pengujian perangkat menunjukkan masalah lifecycle tertentu, baru kita evaluasi Foreground Service.

Ini lebih bersih daripada menjalankan service terus-menerus tanpa kebutuhan.

---

# 30. Scope V1

Supaya proyek tidak melebar, **V1 resmi** menurutku:

### Android

* [x] NotificationListener
* [x] App discovery
* [x] CRUD monitored apps
* [x] Enable/disable app
* [x] Generic notification capture
* [x] Local SQLite
* [x] Deduplication
* [x] Sync queue
* [x] Retry
* [x] App notification history
* [x] Basic parser architecture
* [x] Device registration

### Server

* [x] Device authentication
* [x] Batch notification API
* [x] Notification storage
* [x] Application/device data
* [x] Dashboard
* [x] Search
* [x] Filter
* [x] Statistics sederhana

### V1.1

* [ ] Webhook
* [ ] Custom filters
* [ ] Parser configuration
* [ ] Export CSV/JSON
* [ ] Multiple accounts

### V2

* [ ] Advanced parser engine
* [ ] Rule builder
* [ ] Webhook retry
* [ ] Advanced analytics
* [ ] Telegram integration
* [ ] More sophisticated device management

---

# 31. Hal yang **tidak** masuk V1

Supaya jelas:

```text
❌ Membaca database aplikasi lain
❌ Mengambil saldo langsung dari aplikasi
❌ Mengakses private API
❌ Mengambil session/token aplikasi
❌ Reverse engineering aplikasi
❌ Mengotomatisasi login
❌ Mengirim command ke aplikasi lain
```

Sistem ini adalah:

> **Notification monitoring & forwarding platform**, bukan automation framework untuk mengambil alih aplikasi.

---

# 32. Struktur project yang aku rekomendasikan

Karena kamu cenderung menghindari arsitektur yang terlalu kompleks, kita juga jangan membuat monorepo berlebihan.

```text
veyra-notification-monitor/
│
├── android/
│   └── ...
│
├── server/
│   ├── src/
│   │   ├── config/
│   │   ├── api/
│   │   ├── auth/
│   │   ├── notifications/
│   │   ├── applications/
│   │   ├── devices/
│   │   ├── webhooks/
│   │   ├── storage/
│   │   └── dashboard/
│   │
│   ├── data/
│   └── package.json
│
├── docs/
│   ├── PRD.md
│   ├── API.md
│   └── ARCHITECTURE.md
│
└── README.md
```

Android dan server tetap dua aplikasi yang jelas, tetapi masing-masing tidak perlu dipecah menjadi puluhan package.

---

# 33. Alur lengkap ketika transaksi terjadi

Sekarang kita simulasi yang sebenarnya.

Misalnya DANA menghasilkan:

```text
DANA
Anda menerima Rp100.000
```

### Step 1

Android menerima event.

```text
packageName = id.dana
```

### Step 2

App Registry:

```text
id.dana
enabled = true
```

### Step 3

Deduplication:

```text
fingerprint = abc123
```

Belum ada.

### Step 4

Parser:

```text
DanaParser
```

menghasilkan:

```json
{
  "type": "transaction",
  "direction": "incoming",
  "amount": 100000
}
```

### Step 5

SQLite:

```text
status = PENDING
```

### Step 6

WorkManager melakukan sync.

```text
POST /notifications/batch
```

### Step 7

Server menyimpan.

```text
SYNCED
```

### Step 8

Jika webhook aktif:

```text
Server
 ↓
Webhook
```

### Step 9

Dashboard otomatis menunjukkan:

```text
DANA
+ Rp100.000
```

---

# 34. Kalau internet mati?

```text
DANA
 ↓
Android
 ↓
SQLite
 ↓
PENDING
```

Selesai.

Tidak hilang.

Internet kembali:

```text
PENDING
 ↓
SYNC
 ↓
SERVER
 ↓
SYNCED
```

Ini inti reliability yang ingin kita pertahankan dari rancangan awal. 

---

# 35. Kalau ada 5 aplikasi sekaligus?

Misalnya dalam 1 detik:

```text
DANA       → transaksi
GoPay      → transaksi
WhatsApp   → pesan
Telegram   → pesan
BRImo      → transaksi
```

Listener menerima semuanya.

```text
                NotificationListener
                        │
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
      DANA            GoPay             BRImo
        │               │                │
      Parser          Parser           Parser
        │               │                │
        └───────────────┼────────────────┘
                        ▼
                      SQLite
```

Tidak perlu lima service.

**Satu NotificationListener menangani seluruh aplikasi.**

---

# 36. Kesimpulan desain

Jadi sistem yang kita bangun **bukan**:

> “Aplikasi untuk menangkap notifikasi DANA.”

Melainkan:

> **Platform Android untuk menangkap, menyimpan, memfilter, memproses, dan meneruskan notifikasi dari aplikasi yang dipilih pengguna.**

DANA hanya:

```text
Application Profile
```

Begitu juga:

```text
GoPay
BRImo
Shopee
Tokopedia
WhatsApp
Telegram
Gmail
...
```

Dan arsitektur utamanya:

```text
              ANY ANDROID APP
                    │
                    ▼
          Notification Listener
                    │
                    ▼
             App Registry
                    │
             ┌──────┴──────┐
             │             │
           ALLOW          BLOCK
             │
             ▼
          Dedup
             │
             ▼
           Filter
             │
             ▼
           Parser
             │
             ▼
        Local SQLite
             │
             ▼
        Reliable Sync
             │
             ▼
           Veyra API
             │
       ┌─────┴──────┐
       ▼            ▼
   Dashboard      Webhook
```

**Menurutku ini sudah cukup solid untuk dijadikan blueprint coding.** Tahap setelah PRD ini sebaiknya bukan langsung menulis UI, melainkan kita kunci **database schema + API contract + state machine Android + struktur package Android/server**. Itu akan menjadi fondasi sebelum kita memberikan instruksi implementasi ke Antigravity.
