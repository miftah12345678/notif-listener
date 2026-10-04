import crypto from 'node:crypto';
import { getDb } from '../config/database.js';
import config from '../config/env.js';
import { v4 as uuidv4 } from 'uuid';

const SESSION_DURATION_MS = 24 * 60 * 60 * 1000; // 24 hours

export function hashPassword(password) {
  return crypto.createHash('sha256').update(password + config.sessionSecret).digest('hex');
}

export async function ensureDefaultUser() {
  const db = getDb();
  const existing = await db.get('SELECT id FROM users WHERE username = ?', [config.dashboardUser]);
  if (!existing) {
    await db.run('INSERT INTO users (id, username, password_hash) VALUES (?, ?, ?)', [
      uuidv4(),
      config.dashboardUser,
      hashPassword(config.dashboardPass)
    ]);
  }
}

export async function login(username, password) {
  const db = getDb();
  const user = await db.get('SELECT * FROM users WHERE username = ?', [username]);
  if (!user) return null;

  const hash = hashPassword(password);
  if (hash !== user.password_hash) return null;

  const sessionId = uuidv4();
  const expiresAt = new Date(Date.now() + SESSION_DURATION_MS).toISOString();
  await db.run('INSERT INTO dashboard_sessions (id, user_id, expires_at) VALUES (?, ?, ?)', [sessionId, user.id, expiresAt]);
  await db.run("DELETE FROM dashboard_sessions WHERE expires_at < datetime('now')");

  return sessionId;
}

export async function validateSession(sessionId) {
  if (!sessionId) return null;
  const db = getDb();
  const row = await db.get(`
    SELECT s.*, u.username
    FROM dashboard_sessions s
    JOIN users u ON u.id = s.user_id
    WHERE s.id = ? AND s.expires_at > datetime('now')
  `, [sessionId]);
  return row || null;
}

export async function destroySession(sessionId) {
  const db = getDb();
  await db.run('DELETE FROM dashboard_sessions WHERE id = ?', [sessionId]);
}

export async function dashboardAuth(req, res, next) {
  try {
    const sessionId = req.cookies?.veyra_session;
    const session = await validateSession(sessionId);

    if (!session) {
      if (req.xhr || req.path.startsWith('/api/')) {
        return res.status(401).json({ error: 'Not authenticated' });
      }
      return res.redirect('/login');
    }

    req.user = session;
    next();
  } catch (err) {
    next(err);
  }
}
