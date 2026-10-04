import { Router } from 'express';
import { z } from 'zod';
import { deviceAuth } from '../auth/device-auth.js';
import * as notifService from './notification.service.js';

const router = Router();

const batchEventSchema = z.object({
  clientId: z.string().optional(),
  packageName: z.string().min(1),
  appName: z.string().optional(),
  notificationKey: z.string().optional(),
  title: z.string().optional(),
  text: z.string().optional(),
  rawExtras: z.any().optional(),
  postedAt: z.string().optional(),
  fingerprint: z.string().optional(),
  category: z.string().optional(),
  parsedData: z.any().optional(),
});

const batchSchema = z.object({
  events: z.array(batchEventSchema).min(1).max(500),
});

router.post('/batch', deviceAuth, async (req, res) => {
  const parsed = batchSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Validation failed', details: parsed.error.flatten() });
  }

  try {
    const result = await notifService.ingestBatch(req.device.id, parsed.data.events);
    res.status(200).json({
      accepted: result.accepted,
      duplicates: result.duplicates,
      failed: result.failed,
      summary: {
        total: parsed.data.events.length,
        accepted: result.accepted.length,
        duplicates: result.duplicates.length,
        failed: result.failed.length,
      },
    });
  } catch (err) {
    res.status(500).json({ error: 'Batch ingestion failed', message: err.message });
  }
});

router.get('/', async (req, res) => {
  const { page, limit, deviceId, applicationId, packageName, category, search, dateFrom, dateTo } = req.query;
  const result = await notifService.listNotifications({
    page: page ? parseInt(page, 10) : 1,
    limit: limit ? parseInt(limit, 10) : 50,
    deviceId, applicationId, packageName, category, search, dateFrom, dateTo,
  });
  res.json(result);
});

router.get('/categories', async (req, res) => {
  const categories = await notifService.getCategories();
  res.json({ categories });
});

router.get('/:id', async (req, res) => {
  const notif = await notifService.getNotification(req.params.id);
  if (!notif) return res.status(404).json({ error: 'Notification not found' });
  res.json({ notification: notif });
});

router.delete('/:id', async (req, res) => {
  await notifService.deleteNotification(req.params.id);
  res.json({ message: 'Notification deleted' });
});

export default router;
