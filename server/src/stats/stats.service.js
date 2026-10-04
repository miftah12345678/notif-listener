import { getDb } from '../config/database.js';

export async function getDashboardStats() {
  const db = getDb();

  const totals = await db.get(`
    SELECT
      (SELECT COUNT(*) FROM applications) as total_apps,
      (SELECT COUNT(DISTINCT dac.application_id) FROM device_app_configs dac WHERE dac.enabled = 1) as active_apps,
      (SELECT COUNT(*) FROM devices) as total_devices,
      (SELECT COUNT(*) FROM devices WHERE status = 'online') as online_devices,
      (SELECT COUNT(*) FROM notifications) as total_notifications
  `);

  const todayNotifs = await db.all(`
    SELECT
      COALESCE(a.app_name, n.package_name) as app_name,
      COUNT(*) as count
    FROM notifications n
    LEFT JOIN applications a ON a.id = n.application_id
    WHERE date(n.posted_at) = date('now')
    GROUP BY COALESCE(a.app_name, n.package_name)
    ORDER BY count DESC
    LIMIT 10
  `);

  const weekTrend = await db.all(`
    SELECT
      date(posted_at) as day,
      COUNT(*) as count
    FROM notifications
    WHERE posted_at >= datetime('now', '-7 days')
    GROUP BY date(posted_at)
    ORDER BY day ASC
  `);

  const categories = await db.all(`
    SELECT
      category,
      COUNT(*) as count
    FROM notifications
    GROUP BY category
    ORDER BY count DESC
  `);

  const recent = await db.all(`
    SELECT n.*, a.app_name, d.device_name
    FROM notifications n
    LEFT JOIN applications a ON a.id = n.application_id
    LEFT JOIN devices d ON d.id = n.device_id
    ORDER BY n.posted_at DESC
    LIMIT 10
  `);

  return { totals, todayNotifs, weekTrend, categories, recent };
}

export async function getAppStats() {
  const db = getDb();
  return await db.all(`
    SELECT
      a.id,
      a.package_name,
      a.app_name,
      COUNT(n.id) as total_notifications,
      COUNT(CASE WHEN date(n.posted_at) = date('now') THEN 1 END) as today_count,
      (SELECT COUNT(*) FROM device_app_configs dac WHERE dac.application_id = a.id AND dac.enabled = 1) as active_devices,
      MAX(n.posted_at) as last_notification
    FROM applications a
    LEFT JOIN notifications n ON n.application_id = a.id
    GROUP BY a.id
    ORDER BY total_notifications DESC
  `);
}

export async function getDeviceStats() {
  const db = getDb();
  return await db.all(`
    SELECT
      d.id,
      d.device_name,
      d.device_model,
      d.status,
      d.last_seen_at,
      COUNT(n.id) as total_notifications,
      COUNT(CASE WHEN date(n.posted_at) = date('now') THEN 1 END) as today_count,
      (SELECT COUNT(*) FROM device_app_configs dac WHERE dac.device_id = d.id AND dac.enabled = 1) as active_apps
    FROM devices d
    LEFT JOIN notifications n ON n.device_id = d.id
    GROUP BY d.id
    ORDER BY d.device_name ASC
  `);
}
