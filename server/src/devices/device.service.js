import { v4 as uuidv4 } from 'uuid';
import { getDb } from '../config/database.js';
import { generateToken, hashToken } from '../auth/device-auth.js';

export async function registerDevice({ deviceName, deviceModel, androidVersion }) {
  const db = getDb();
  const id = uuidv4();
  const rawToken = generateToken();
  const tokenHash = hashToken(rawToken);

  await db.run(`
    INSERT INTO devices (id, device_name, device_model, android_version, token_hash, status, last_seen_at)
    VALUES (?, ?, ?, ?, ?, 'online', datetime('now'))
  `, [id, deviceName, deviceModel || null, androidVersion || null, tokenHash]);

  const device = await db.get('SELECT * FROM devices WHERE id = ?', [id]);
  return { device, token: rawToken };
}

export async function listDevices() {
  const db = getDb();
  return await db.all('SELECT * FROM devices ORDER BY created_at DESC');
}

export async function getDevice(id) {
  const db = getDb();
  return await db.get('SELECT * FROM devices WHERE id = ?', [id]);
}

export async function updateDevice(id, { deviceName, deviceModel, androidVersion }) {
  const db = getDb();
  const sets = [];
  const params = [];

  if (deviceName !== undefined) { sets.push('device_name = ?'); params.push(deviceName); }
  if (deviceModel !== undefined) { sets.push('device_model = ?'); params.push(deviceModel); }
  if (androidVersion !== undefined) { sets.push('android_version = ?'); params.push(androidVersion); }

  if (sets.length === 0) return await getDevice(id);

  params.push(id);
  await db.run(`UPDATE devices SET ${sets.join(', ')} WHERE id = ?`, params);
  return await getDevice(id);
}

export async function deleteDevice(id) {
  const db = getDb();
  return await db.run('DELETE FROM devices WHERE id = ?', [id]);
}

export async function getDeviceStats(deviceId) {
  const db = getDb();
  const notifCount = await db.get('SELECT COUNT(*) as count FROM notifications WHERE device_id = ?', [deviceId]);
  const appCount = await db.get('SELECT COUNT(*) as count FROM device_app_configs WHERE device_id = ? AND enabled = 1', [deviceId]);
  return {
    notificationCount: notifCount.count,
    activeApps: appCount.count,
  };
}
