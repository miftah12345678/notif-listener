import { v4 as uuidv4 } from 'uuid';
import { getDb } from '../config/database.js';

export async function createApplication({ packageName, appName, icon }) {
  const db = getDb();
  const id = uuidv4();
  await db.run(`
    INSERT INTO applications (id, package_name, app_name, icon)
    VALUES (?, ?, ?, ?)
  `, [id, packageName, appName, icon || null]);
  return await getApplication(id);
}

export async function listApplications() {
  const db = getDb();
  return await db.all('SELECT * FROM applications ORDER BY app_name ASC');
}

export async function getApplication(id) {
  const db = getDb();
  return await db.get('SELECT * FROM applications WHERE id = ?', [id]);
}

export async function getApplicationByPackage(packageName) {
  const db = getDb();
  return await db.get('SELECT * FROM applications WHERE package_name = ?', [packageName]);
}

export async function findOrCreateApplication(packageName, appName) {
  let app = await getApplicationByPackage(packageName);
  if (!app) {
    app = await createApplication({ packageName, appName: appName || packageName });
  }
  return app;
}

export async function updateApplication(id, { appName, icon }) {
  const db = getDb();
  const sets = [];
  const params = [];

  if (appName !== undefined) { sets.push('app_name = ?'); params.push(appName); }
  if (icon !== undefined) { sets.push('icon = ?'); params.push(icon); }

  if (sets.length === 0) return await getApplication(id);

  params.push(id);
  await db.run(`UPDATE applications SET ${sets.join(', ')} WHERE id = ?`, params);
  return await getApplication(id);
}

export async function deleteApplication(id) {
  const db = getDb();
  return await db.run('DELETE FROM applications WHERE id = ?', [id]);
}

export async function getApplicationStats() {
  const db = getDb();
  return await db.all(`
    SELECT
      a.id,
      a.package_name,
      a.app_name,
      COUNT(n.id) as notification_count,
      (SELECT COUNT(*) FROM device_app_configs dac WHERE dac.application_id = a.id AND dac.enabled = 1) as active_devices
    FROM applications a
    LEFT JOIN notifications n ON n.application_id = a.id
    GROUP BY a.id
    ORDER BY notification_count DESC
  `);
}
