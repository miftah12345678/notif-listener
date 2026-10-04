// src/config/env.js — Environment configuration

const config = {
  port: parseInt(process.env.PORT || '3000', 10),
  host: process.env.HOST || '0.0.0.0',
  env: process.env.NODE_ENV || 'development',

  // Database
  dbPath: process.env.DB_PATH || './data/veyra.db',

  // Dashboard auth
  dashboardUser: process.env.DASHBOARD_USER || 'admin',
  dashboardPass: process.env.DASHBOARD_PASS || 'veyra2026',
  sessionSecret: process.env.SESSION_SECRET || 'veyra-secret-change-in-production',

  // Logging
  logLevel: process.env.LOG_LEVEL || 'info',
};

export default config;
