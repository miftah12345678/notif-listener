import { v4 as uuidv4 } from 'uuid';
import { getDb } from '../config/database.js';

export async function createConfig({ deviceId, applicationId, enabled, captureEnabled, captureMode, parserId, webhookEnabled }) {
  const db = getDb();
  const id = uuidv4();
  await db.run(`
    INSERT INTO device_app_configs (id, device_id, application_id, enabled, capture_enabled, capture_mode, parser_id, webhook_enabled)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `, [
    id,
    deviceId,
    applicationId,
    enabled === false ? 0 : 1,
    captureEnabled === false ? 0 : 1,
    captureMode || 'full',
    parserId || 'generic',
    webhookEnabled === true ? 1 : 0
  ]);
  return await getConfig(id);
}

export async function getConfig(id) {
  const db = getDb();
  return await db.get(`
    SELECT dac.*, a.package_name, a.app_name, d.device_name
    FROM device_app_configs dac
    JOIN applications a ON a.id = dac.application_id
    JOIN devices d ON d.id = dac.device_id
    WHERE dac.id = ?
  `, [id]);
}

export async function getConfigByDeviceApp(deviceId, applicationId) {
  const db = getDb();
  return await db.get(`
    SELECT dac.*, a.package_name, a.app_name, d.device_name
    FROM device_app_configs dac
    JOIN applications a ON a.id = dac.application_id
    JOIN devices d ON d.id = dac.device_id
    WHERE dac.device_id = ? AND dac.application_id = ?
  `, [deviceId, applicationId]);
}

export async function listConfigsByDevice(deviceId) {
  const db = getDb();
  return await db.all(`
    SELECT dac.*, a.package_name, a.app_name
    FROM device_app_configs dac
    JOIN applications a ON a.id = dac.application_id
    WHERE dac.device_id = ?
    ORDER BY a.app_name ASC
  `, [deviceId]);
}

export async function listConfigsByApp(applicationId) {
  const db = getDb();
  return await db.all(`
    SELECT dac.*, d.device_name, d.device_model
    FROM device_app_configs dac
    JOIN devices d ON d.id = dac.device_id
    WHERE dac.application_id = ?
    ORDER BY d.device_name ASC
  `, [applicationId]);
}

export async function listAllConfigs() {
  const db = getDb();
  return await db.all(`
    SELECT dac.*, a.package_name, a.app_name, d.device_name
    FROM device_app_configs dac
    JOIN applications a ON a.id = dac.application_id
    JOIN devices d ON d.id = dac.device_id
    ORDER BY d.device_name ASC, a.app_name ASC
  `);
}

export async function updateConfig(id, { enabled, captureEnabled, captureMode, parserId, webhookEnabled }) {
  const db = getDb();
  const sets = [];
  const params = [];

  if (enabled !== undefined) { sets.push('enabled = ?'); params.push(enabled ? 1 : 0); }
  if (captureEnabled !== undefined) { sets.push('capture_enabled = ?'); params.push(captureEnabled ? 1 : 0); }
  if (captureMode !== undefined) { sets.push('capture_mode = ?'); params.push(captureMode); }
  if (parserId !== undefined) { sets.push('parser_id = ?'); params.push(parserId); }
  if (webhookEnabled !== undefined) { sets.push('webhook_enabled = ?'); params.push(webhookEnabled ? 1 : 0); }

  if (sets.length === 0) return await getConfig(id);

  params.push(id);
  await db.run(`UPDATE device_app_configs SET ${sets.join(', ')} WHERE id = ?`, params);
  return await getConfig(id);
}

export async function deleteConfig(id) {
  const db = getDb();
  return await db.run('DELETE FROM device_app_configs WHERE id = ?', [id]);
}

export async function findOrCreateConfig(deviceId, applicationId) {
  let cfg = await getConfigByDeviceApp(deviceId, applicationId);
  if (!cfg) {
    cfg = await createConfig({ deviceId, applicationId });
  }
  return cfg;
}
