import { Router } from 'express';
import { z } from 'zod';
import { deviceAuth } from '../auth/device-auth.js';
import * as deviceService from './device.service.js';

const router = Router();

const registerSchema = z.object({
  deviceName: z.string().min(1).max(100),
  deviceModel: z.string().max(100).optional(),
  androidVersion: z.string().max(20).optional(),
});

router.post('/register', async (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Validation failed', details: parsed.error.flatten() });
  }

  try {
    const result = await deviceService.registerDevice(parsed.data);
    res.status(201).json({
      deviceId: result.device.id,
      deviceName: result.device.device_name,
      token: result.token,
      message: 'Device registered. Store the token securely — it cannot be retrieved later.',
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to register device' });
  }
});

router.get('/', async (req, res) => {
  const devices = await deviceService.listDevices();
  const safe = devices.map(({ token_hash, ...rest }) => rest);
  res.json({ devices: safe });
});

router.get('/config', deviceAuth, async (req, res) => {
  try {
    const { token_hash, ...safeDevice } = req.device;
    const db = (await import('../config/database.js')).getDb();
    const configs = await db.all(`
      SELECT 
        a.package_name as packageName,
        dac.enabled,
        dac.capture_enabled as captureEnabled,
        dac.capture_mode as captureMode,
        dac.parser_id as parserId,
        dac.webhook_enabled as webhookEnabled
      FROM device_app_configs dac
      JOIN applications a ON a.id = dac.application_id
      WHERE dac.device_id = ?
    `, [req.device.id]);

    // Format boolean values since sqlite stores them as 1/0
    const applications = configs.map(c => ({
      packageName: c.packageName,
      enabled: c.enabled === 1,
      captureEnabled: c.captureEnabled === 1,
      captureMode: c.captureMode,
      parserId: c.parserId,
      webhookEnabled: c.webhookEnabled === 1
    }));

    res.json({
      device: safeDevice,
      applications
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch config' });
  }
});

router.get('/:id', async (req, res) => {
  const device = await deviceService.getDevice(req.params.id);
  if (!device) return res.status(404).json({ error: 'Device not found' });
  const { token_hash, ...safe } = device;
  const stats = await deviceService.getDeviceStats(device.id);
  res.json({ device: safe, stats });
});

router.put('/:id', async (req, res) => {
  const device = await deviceService.updateDevice(req.params.id, req.body);
  if (!device) return res.status(404).json({ error: 'Device not found' });
  const { token_hash, ...safe } = device;
  res.json({ device: safe });
});

router.delete('/:id', async (req, res) => {
  await deviceService.deleteDevice(req.params.id);
  res.json({ message: 'Device deleted' });
});

router.post('/heartbeat', deviceAuth, (req, res) => {
  res.json({ status: 'ok', serverTime: new Date().toISOString() });
});

export default router;
