import { DatabaseSync } from 'node:sqlite';
import path from 'path';
import { fileURLToPath } from 'url';
import logger from './logger.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const dbPath = process.env.DB_PATH || path.resolve(__dirname, '../../../data/database.sqlite');

let dbInstance = null;

class AsyncDbWrapper {
  constructor(db) {
    this.db = db;
  }
  
  async run(sql, params = []) {
    const stmt = this.db.prepare(sql);
    // Support both array of params and single param object/value
    if (Array.isArray(params)) {
      return stmt.run(...params);
    } else {
      return stmt.run(params);
    }
  }
  
  async get(sql, params = []) {
    const stmt = this.db.prepare(sql);
    if (Array.isArray(params)) {
      return stmt.get(...params);
    } else {
      return stmt.get(params);
    }
  }
  
  async all(sql, params = []) {
    const stmt = this.db.prepare(sql);
    if (Array.isArray(params)) {
      return stmt.all(...params);
    } else {
      return stmt.all(params);
    }
  }
  
  async exec(sql) {
    return this.db.exec(sql);
  }
}

export async function initDb() {
  if (dbInstance) return dbInstance;

  try {
    const rawDb = new DatabaseSync(dbPath);
    const db = new AsyncDbWrapper(rawDb);
    
    // Enable WAL mode
    await db.exec('PRAGMA journal_mode = WAL');
    await db.exec('PRAGMA foreign_keys = ON');

    // Create tables
    await db.exec(`
      CREATE TABLE IF NOT EXISTS devices (
        id TEXT PRIMARY KEY,
        device_name TEXT NOT NULL,
        device_model TEXT,
        android_version TEXT,
        token_hash TEXT UNIQUE NOT NULL,
        status TEXT DEFAULT 'offline',
        last_seen_at DATETIME,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS applications (
        id TEXT PRIMARY KEY,
        package_name TEXT UNIQUE NOT NULL,
        app_name TEXT NOT NULL,
        icon TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS device_app_configs (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        application_id TEXT NOT NULL,
        enabled BOOLEAN DEFAULT 1,
        capture_enabled BOOLEAN DEFAULT 1,
        capture_mode TEXT DEFAULT 'full',
        parser_id TEXT DEFAULT 'generic',
        webhook_enabled BOOLEAN DEFAULT 0,
        webhook_url TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
        UNIQUE (device_id, application_id)
      );

      CREATE TABLE IF NOT EXISTS notifications (
        id TEXT PRIMARY KEY,
        device_id TEXT,
        application_id TEXT,
        package_name TEXT NOT NULL,
        title TEXT,
        text TEXT,
        raw_extras TEXT,
        posted_at DATETIME,
        received_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        fingerprint TEXT NOT NULL,
        category TEXT,
        parsed_data TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE SET NULL,
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
        UNIQUE (device_id, fingerprint)
      );

      CREATE TABLE IF NOT EXISTS dashboard_sessions (
        id TEXT PRIMARY KEY,
        user_id TEXT NOT NULL,
        expires_at DATETIME NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        username TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
      );
    `);

    dbInstance = db;
    logger.info('Database initialized successfully using node:sqlite');
    return db;
  } catch (error) {
    logger.error('Failed to initialize database:', error);
    throw error;
  }
}

export function getDb() {
  if (!dbInstance) {
    throw new Error('Database has not been initialized. Call initDb() first.');
  }
  return dbInstance;
}
