import crypto from 'node:crypto';
import { getDb } from '../config/database.js';

export function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export function generateToken() {
  return crypto.randomBytes(32).toString('hex');
}

export async function deviceAuth(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid Authorization header' });
    }

    const token = authHeader.slice(7);
    const tokenHash = hashToken(token);

    const db = getDb();
    const device = await db.get('SELECT * FROM devices WHERE token_hash = ?', [tokenHash]);

    if (!device) {
      return res.status(401).json({ error: 'Invalid device token' });
    }

    await db.run(`UPDATE devices SET status = 'online', last_seen_at = datetime('now') WHERE id = ?`, [device.id]);

    req.device = device;
    next();
  } catch (err) {
    next(err);
  }
}
