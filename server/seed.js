import { getDb, initDb } from './src/config/database.js';
import { v4 as uuidv4 } from 'uuid';
import crypto from 'node:crypto';
import { ensureDefaultUser } from './src/auth/dashboard-auth.js';

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function randomDate(daysBack = 7) {
  const now = Date.now();
  const past = now - daysBack * 24 * 60 * 60 * 1000;
  return new Date(past + Math.random() * (now - past)).toISOString();
}

function fingerprint(parts) {
  return crypto.createHash('sha256').update(parts.join('|')).digest('hex');
}

async function runSeed() {
  console.log('🌱 Seeding database...\n');
  await initDb();
  
  const db = getDb();
  await ensureDefaultUser();

  const devices = [
    { id: '11111111-1111-1111-1111-111111111111', device_name: 'HP Utama', device_model: 'Samsung Galaxy S24', android_version: '15', token: 'seed-token-device-1' },
    { id: '22222222-2222-2222-2222-222222222222', device_name: 'HP Toko', device_model: 'Xiaomi Redmi Note 13', android_version: '14', token: 'seed-token-device-2' },
    { id: '33333333-3333-3333-3333-333333333333', device_name: 'HP Backup', device_model: 'Google Pixel 8', android_version: '15', token: 'seed-token-device-3' },
  ];

  for (const d of devices) {
    await db.run(`
      INSERT OR IGNORE INTO devices (id, device_name, device_model, android_version, token_hash, status, last_seen_at)
      VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
    `, [d.id, d.device_name, d.device_model, d.android_version, hashToken(d.token), d === devices[2] ? 'offline' : 'online']);
  }
  console.log(`✅ ${devices.length} devices created`);

  const apps = [
    { id: 'a1111111-a111-a111-a111-a11111111111', package_name: 'id.dana', app_name: 'DANA' },
    { id: 'a2222222-a222-a222-a222-a22222222222', package_name: 'com.gojek.gopay', app_name: 'GoPay' },
    { id: 'a3333333-a333-a333-a333-a33333333333', package_name: 'id.co.bri.brimo', app_name: 'BRImo' },
    { id: 'a4444444-a444-a444-a444-a44444444444', package_name: 'com.whatsapp', app_name: 'WhatsApp' },
    { id: 'a5555555-a555-a555-a555-a55555555555', package_name: 'org.telegram.messenger', app_name: 'Telegram' },
    { id: 'a6666666-a666-a666-a666-a66666666666', package_name: 'com.shopee.id', app_name: 'Shopee' },
    { id: 'a7777777-a777-a777-a777-a77777777777', package_name: 'com.tokopedia.tkpd', app_name: 'Tokopedia' },
    { id: 'a8888888-a888-a888-a888-a88888888888', package_name: 'com.google.android.gm', app_name: 'Gmail' },
  ];

  for (const a of apps) {
    await db.run(`
      INSERT OR IGNORE INTO applications (id, package_name, app_name)
      VALUES (?, ?, ?)
    `, [a.id, a.package_name, a.app_name]);
  }
  console.log(`✅ ${apps.length} applications created`);

  const configs = [
    { device: devices[0], app: apps[0], enabled: 1, parser_id: 'dana' },
    { device: devices[0], app: apps[1], enabled: 1, parser_id: 'gopay' },
    { device: devices[0], app: apps[2], enabled: 1, parser_id: 'brimo' },
    { device: devices[0], app: apps[3], enabled: 0, parser_id: 'generic' },
    { device: devices[0], app: apps[5], enabled: 1, parser_id: 'generic' },
    { device: devices[1], app: apps[0], enabled: 1, parser_id: 'dana' },
    { device: devices[1], app: apps[1], enabled: 1, parser_id: 'gopay' },
    { device: devices[1], app: apps[3], enabled: 1, parser_id: 'generic' },
    { device: devices[1], app: apps[4], enabled: 1, parser_id: 'generic' },
    { device: devices[2], app: apps[0], enabled: 0, parser_id: 'dana' },
    { device: devices[2], app: apps[1], enabled: 1, parser_id: 'gopay' },
    { device: devices[2], app: apps[3], enabled: 1, parser_id: 'generic' },
  ];

  for (const c of configs) {
    await db.run(`
      INSERT OR IGNORE INTO device_app_configs (id, device_id, application_id, enabled, capture_enabled, capture_mode, parser_id, webhook_enabled)
      VALUES (?, ?, ?, ?, 1, 'full', ?, 0)
    `, [c.device.id + '-' + c.app.id.substring(0, 13), c.device.id, c.app.id, c.enabled, c.parser_id]);
  }
  console.log(`✅ ${configs.length} device-app configs created`);

  const notifTemplates = [
    { app: apps[0], category: 'TRANSACTION', title: 'DANA', texts: ['Anda menerima Rp100.000 dari JOHN DOE', 'Pembayaran Rp25.000 ke Toko ABC berhasil', 'Transfer Rp50.000 ke 0812xxxx berhasil', 'DANA Kaget! Anda mendapat Rp5.000', 'Top up saldo Rp200.000 berhasil'], parsedFn: (text) => ({ type: 'transaction', direction: text.includes('menerima') || text.includes('Kaget') ? 'incoming' : 'outgoing', amount: parseInt((text.match(/Rp([\d.]+)/)||['','0'])[1].replace(/\./g,'')), currency: 'IDR' }) },
    { app: apps[1], category: 'TRANSACTION', title: 'GoPay', texts: ['Pembayaran Rp15.000 di GoFood berhasil', 'Anda menerima Rp75.000 dari transfer', 'Top up GoPay Rp100.000 berhasil', 'Cashback Rp3.000 diterima'], parsedFn: (text) => ({ type: 'transaction', direction: text.includes('menerima') || text.includes('Cashback') ? 'incoming' : 'outgoing', amount: parseInt((text.match(/Rp([\d.]+)/)||['','0'])[1].replace(/\./g,'')), currency: 'IDR' }) },
    { app: apps[2], category: 'TRANSACTION', title: 'BRImo', texts: ['Transfer masuk Rp1.500.000 dari PT XYZ', 'Transfer keluar Rp300.000 ke 1234xxxx', 'Pembayaran listrik Rp250.000 berhasil', 'Mutasi rekening tersedia'], parsedFn: (text) => ({ type: 'transaction', direction: text.includes('masuk') ? 'incoming' : 'outgoing' }) },
    { app: apps[3], category: 'MESSAGE', title: 'WhatsApp', texts: ['Budi: Halo, sudah sampai?', 'Grup Keluarga: Foto liburan', 'Mama: Jangan lupa makan', '3 pesan baru'], parsedFn: () => ({ type: 'message' }) },
    { app: apps[4], category: 'MESSAGE', title: 'Telegram', texts: ['Channel Update: Berita hari ini', 'Bot Alert: Server status OK', 'User123: File shared'], parsedFn: () => ({ type: 'message' }) },
    { app: apps[5], category: 'PROMOTION', title: 'Shopee', texts: ['Flash Sale dimulai!', 'Pesanan #SHP123 sedang dikirim', 'Gratis Ongkir khusus hari ini', 'Voucher diskon 50% untukmu'], parsedFn: () => ({ type: 'promotion' }) },
    { app: apps[6], category: 'PROMOTION', title: 'Tokopedia', texts: ['Pesanan dalam perjalanan', 'Cashback 10% menunggu', 'Review pesanan terakhirmu'], parsedFn: () => ({ type: 'promotion' }) },
    { app: apps[7], category: 'OTHER', title: 'Gmail', texts: ['New email from support@example.com', 'Newsletter: Weekly digest', 'Meeting invite: Standup tomorrow 9 AM'], parsedFn: () => ({ type: 'email' }) },
  ];

  let notifCount = 0;
  for (let i = 0; i < 200; i++) {
    const template = notifTemplates[Math.floor(Math.random() * notifTemplates.length)];
    const text = template.texts[Math.floor(Math.random() * template.texts.length)];
    const device = devices[Math.floor(Math.random() * devices.length)];
    const postedAt = randomDate(14);
    const id = uuidv4();
    const fp = fingerprint([template.app.package_name, id, postedAt, template.title, text]);
    const parsedData = template.parsedFn(text);

    try {
      await db.run(`
        INSERT OR IGNORE INTO notifications (id, device_id, application_id, package_name, title, text, raw_extras, posted_at, received_at, fingerprint, category, parsed_data)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `, [
        id, device.id, template.app.id, template.app.package_name, template.title, text,
        JSON.stringify({ android_key: `${Math.random().toString(36).slice(2)}` }),
        postedAt,
        new Date(new Date(postedAt).getTime() + 500 + Math.random() * 2000).toISOString(),
        fp, template.category, JSON.stringify(parsedData)
      ]);
      notifCount++;
    } catch (err) {}
  }
  
  console.log(`✅ ${notifCount} notifications created`);
  console.log('\n🎉 Seed complete!');
  console.log('\n📋 Dashboard login:\n   Username: admin\n   Password: veyra2026');
  console.log(`\n📱 Device tokens (for API testing):`);
  for (const d of devices) {
    console.log(`   ${d.device_name}: ${d.token}`);
  }
}

runSeed().catch(console.error);
