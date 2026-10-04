import { Router } from 'express';
import { dashboardAuth, login, destroySession } from '../auth/dashboard-auth.js';
import * as statsService from '../stats/stats.service.js';
import * as notifService from '../notifications/notification.service.js';
import * as appService from '../applications/application.service.js';
import * as deviceService from '../devices/device.service.js';
import * as dacService from '../device-apps/device-app.service.js';

const router = Router();

router.get('/login', (req, res) => {
  res.render('login', { error: null });
});

router.post('/login', async (req, res) => {
  const { username, password } = req.body;
  const sessionId = await login(username, password);

  if (!sessionId) {
    return res.render('login', { error: 'Invalid credentials' });
  }

  res.cookie('veyra_session', sessionId, {
    httpOnly: true,
    maxAge: 24 * 60 * 60 * 1000,
    sameSite: 'lax',
  });
  res.redirect('/');
});

router.get('/logout', async (req, res) => {
  const sessionId = req.cookies?.veyra_session;
  if (sessionId) await destroySession(sessionId);
  res.clearCookie('veyra_session');
  res.redirect('/login');
});

router.use(dashboardAuth);

router.get('/', async (req, res) => {
  const stats = await statsService.getDashboardStats();
  res.render('dashboard', { page: 'dashboard', stats });
});

router.get('/notifications', async (req, res) => {
  const { page, deviceId, applicationId, category, search, dateFrom, dateTo } = req.query;
  const result = await notifService.listNotifications({
    page: page ? parseInt(page, 10) : 1,
    limit: 30,
    deviceId, applicationId, category, search, dateFrom, dateTo,
  });
  const apps = await appService.listApplications();
  const devices = await deviceService.listDevices();
  const categories = await notifService.getCategories();
  res.render('notifications', {
    page: 'notifications',
    ...result,
    filters: { deviceId, applicationId, category, search, dateFrom, dateTo },
    apps,
    devices,
    categories,
  });
});

router.get('/notifications/:id', async (req, res) => {
  const notif = await notifService.getNotification(req.params.id);
  if (!notif) return res.status(404).render('error', { page: 'error', message: 'Notification not found' });
  res.render('notification-detail', { page: 'notifications', notif });
});

router.get('/applications', async (req, res) => {
  const apps = await appService.listApplications();
  const appStats = await appService.getApplicationStats();
  res.render('applications', { page: 'applications', apps, appStats });
});

router.get('/devices', async (req, res) => {
  const devices = await deviceService.listDevices();
  const deviceStats = await statsService.getDeviceStats();
  res.render('devices', { page: 'devices', devices, deviceStats });
});

router.get('/devices/:id', async (req, res) => {
  const device = await deviceService.getDevice(req.params.id);
  if (!device) return res.status(404).render('error', { page: 'error', message: 'Device not found' });
  const configs = await dacService.listConfigsByDevice(device.id);
  const dStats = await deviceService.getDeviceStats(device.id);
  const { token_hash, ...safeDevice } = device;
  res.render('device-detail', { page: 'devices', device: safeDevice, configs, stats: dStats });
});

router.get('/statistics', async (req, res) => {
  const dashboard = await statsService.getDashboardStats();
  const appStats = await statsService.getAppStats();
  const deviceStats = await statsService.getDeviceStats();
  res.render('statistics', { page: 'statistics', dashboard, appStats, deviceStats });
});

router.get('/settings', (req, res) => {
  res.render('settings', { page: 'settings' });
});

export default router;
