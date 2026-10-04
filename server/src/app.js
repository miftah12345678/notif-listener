// src/app.js — Express application setup
import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import cookieParser from 'cookie-parser';
import pino from 'pino';
import config from './config/env.js';
import logger from './config/logger.js';

// Routes
import deviceRoutes from './devices/device.routes.js';
import appRoutes from './applications/application.routes.js';
import dacRoutes from './device-apps/device-app.routes.js';
import notifRoutes from './notifications/notification.routes.js';
import dashboardRoutes from './dashboard/routes.js';

// Stats API
import * as statsService from './stats/stats.service.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.join(__dirname, '..');

// Logger is imported from config/logger.js

export function createApp() {
  const app = express();

  // ─── Middleware ─────────────────────────────────
  app.use(express.json({ limit: '5mb' }));
  app.use(express.urlencoded({ extended: true }));
  app.use(cookieParser());

  // Static files
  app.use('/public', express.static(path.join(rootDir, 'public')));

  // View engine
  app.set('view engine', 'ejs');
  app.set('views', path.join(rootDir, 'views'));

  // Request logging
  app.use((req, res, next) => {
    const start = Date.now();
    res.on('finish', () => {
      const duration = Date.now() - start;
      if (req.path.startsWith('/api/')) {
        logger.info({ method: req.method, path: req.path, status: res.statusCode, duration: `${duration}ms` });
      }
    });
    next();
  });

  // ─── API Routes ────────────────────────────────
  app.use('/api/v1/devices', deviceRoutes);
  app.use('/api/v1/apps', appRoutes);
  app.use('/api/v1/device-apps', dacRoutes);
  app.use('/api/v1/notifications', notifRoutes);

  // Stats API
  app.get('/api/v1/stats', async (req, res) => {
    const stats = await statsService.getDashboardStats();
    res.json(stats);
  });

  // ─── Dashboard Routes ─────────────────────────
  app.use('/', dashboardRoutes);

  // ─── Error Handler ─────────────────────────────
  app.use((err, req, res, _next) => {
    logger.error({ err }, 'Unhandled error');
    if (req.path.startsWith('/api/')) {
      res.status(500).json({ error: 'Internal server error' });
    } else {
      res.status(500).render('error', { page: 'error', message: 'Something went wrong' });
    }
  });

  return app;
}
