import crypto from 'node:crypto';
import { v4 as uuidv4 } from 'uuid';
import { getDb } from '../config/database.js';
import * as appService from '../applications/application.service.js';
import * as dacService from '../device-apps/device-app.service.js';

function generateFingerprint({ packageName, notificationKey, postedAt, title, text }) {
  const p = packageName || '';
  const k = notificationKey || '';
  const t = title || '';
  const txt = text || '';
  
  // Format: len(pkg):pkg|len(key):key|postedAt|len(title):title|len(text):text
  // Using UTF-8 byte length for safety, but since we are in JS, we can use string length if we assume JS strings, 
  // but to be 100% identical with Kotlin ByteArray size, we should use Buffer.byteLength.
  const payload = `${Buffer.byteLength(p, 'utf8')}:${p}|${Buffer.byteLength(k, 'utf8')}:${k}|${postedAt || 0}|${Buffer.byteLength(t, 'utf8')}:${t}|${Buffer.byteLength(txt, 'utf8')}:${txt}`;
  return crypto.createHash('sha256').update(payload, 'utf8').digest('hex');
}

export async function ingestBatch(deviceId, events) {
  const db = getDb();
  const accepted = [];
  const duplicates = [];
  const failed = [];

  for (const evt of events) {
    try {
      const fingerprint = evt.fingerprint || generateFingerprint({
        packageName: evt.packageName,
        notificationKey: evt.notificationKey,
        postedAt: evt.postedAt,
        title: evt.title,
        text: evt.text,
      });

      const existing = await db.get('SELECT id FROM notifications WHERE device_id = ? AND fingerprint = ?', [deviceId, fingerprint]);
      if (existing) {
        duplicates.push({
          clientId: evt.clientId || null,
          fingerprint,
          existingId: existing.id,
        });
        continue;
      }

      const app = await appService.findOrCreateApplication(evt.packageName, evt.appName);
      await dacService.findOrCreateConfig(deviceId, app.id);

      const id = uuidv4();
      await db.run(`
        INSERT INTO notifications (id, device_id, application_id, package_name, title, text, raw_extras, posted_at, received_at, fingerprint, category, parsed_data)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), ?, ?, ?)
      `, [
        id,
        deviceId,
        app.id,
        evt.packageName,
        evt.title || null,
        evt.text || null,
        evt.rawExtras ? JSON.stringify(evt.rawExtras) : null,
        evt.postedAt || null,
        fingerprint,
        evt.category || 'OTHER',
        evt.parsedData ? JSON.stringify(evt.parsedData) : null
      ]);

      accepted.push({
        clientId: evt.clientId || null,
        serverId: id,
        fingerprint,
      });
    } catch (err) {
      failed.push({
        clientId: evt.clientId || null,
        error: err.message,
      });
    }
  }

  return { accepted, duplicates, failed };
}

export async function listNotifications({ page = 1, limit = 50, deviceId, applicationId, packageName, category, search, dateFrom, dateTo } = {}) {
  const db = getDb();
  const conditions = [];
  const params = [];

  if (deviceId) { conditions.push('n.device_id = ?'); params.push(deviceId); }
  if (applicationId) { conditions.push('n.application_id = ?'); params.push(applicationId); }
  if (packageName) { conditions.push('n.package_name = ?'); params.push(packageName); }
  if (category) { conditions.push('n.category = ?'); params.push(category); }
  if (search) {
    conditions.push('(n.title LIKE ? OR n.text LIKE ?)');
    params.push(`%${search}%`, `%${search}%`);
  }
  if (dateFrom) { conditions.push('n.posted_at >= ?'); params.push(dateFrom); }
  if (dateTo) { conditions.push('n.posted_at <= ?'); params.push(dateTo); }

  const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
  const offset = (page - 1) * limit;

  const countRow = await db.get(`SELECT COUNT(*) as total FROM notifications n ${where}`, params);
  const total = countRow.total;

  const notifications = await db.all(`
    SELECT n.*, a.app_name, d.device_name
    FROM notifications n
    LEFT JOIN applications a ON a.id = n.application_id
    LEFT JOIN devices d ON d.id = n.device_id
    ${where}
    ORDER BY n.posted_at DESC
    LIMIT ? OFFSET ?
  `, [...params, limit, offset]);

  return {
    notifications,
    pagination: {
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit),
    },
  };
}

export async function getNotification(id) {
  const db = getDb();
  return await db.get(`
    SELECT n.*, a.app_name, a.package_name as app_package, d.device_name, d.device_model
    FROM notifications n
    LEFT JOIN applications a ON a.id = n.application_id
    LEFT JOIN devices d ON d.id = n.device_id
    WHERE n.id = ?
  `, [id]);
}

export async function deleteNotification(id) {
  const db = getDb();
  return await db.run('DELETE FROM notifications WHERE id = ?', [id]);
}

export async function getCategories() {
  const db = getDb();
  const rows = await db.all('SELECT DISTINCT category FROM notifications WHERE category IS NOT NULL ORDER BY category');
  return rows.map(r => r.category);
}
