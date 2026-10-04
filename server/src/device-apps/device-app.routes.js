import { Router } from 'express';
import { z } from 'zod';
import * as dacService from './device-app.service.js';

const router = Router();

const createSchema = z.object({
  deviceId: z.string().uuid(),
  applicationId: z.string().uuid(),
  enabled: z.boolean().optional().default(true),
  captureEnabled: z.boolean().optional().default(true),
  captureMode: z.enum(['full', 'metadata', 'disabled']).optional().default('full'),
  parserId: z.string().optional().default('generic'),
  webhookEnabled: z.boolean().optional().default(false),
});

const updateSchema = z.object({
  enabled: z.boolean().optional(),
  captureEnabled: z.boolean().optional(),
  captureMode: z.enum(['full', 'metadata', 'disabled']).optional(),
  parserId: z.string().optional(),
  webhookEnabled: z.boolean().optional(),
});

router.get('/', async (req, res) => {
  const { deviceId, applicationId } = req.query;

  let configs;
  if (deviceId) {
    configs = await dacService.listConfigsByDevice(deviceId);
  } else if (applicationId) {
    configs = await dacService.listConfigsByApp(applicationId);
  } else {
    configs = await dacService.listAllConfigs();
  }

  res.json({ configs });
});

router.get('/:id', async (req, res) => {
  const config = await dacService.getConfig(req.params.id);
  if (!config) return res.status(404).json({ error: 'Config not found' });
  res.json({ config });
});

router.post('/', async (req, res) => {
  const parsed = createSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Validation failed', details: parsed.error.flatten() });
  }

  try {
    const config = await dacService.createConfig(parsed.data);
    res.status(201).json({ config });
  } catch (err) {
    if (err.message?.includes('UNIQUE')) {
      return res.status(409).json({ error: 'Config for this device+app already exists' });
    }
    if (err.message?.includes('FOREIGN KEY')) {
      return res.status(400).json({ error: 'Invalid device or application id' });
    }
    console.error('Device-App Create Error:', err);
    res.status(500).json({ error: 'Failed to create config', detail: err.message });
  }
});

router.put('/:id', async (req, res) => {
  const parsed = updateSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Validation failed', details: parsed.error.flatten() });
  }

  const config = await dacService.updateConfig(req.params.id, parsed.data);
  if (!config) return res.status(404).json({ error: 'Config not found' });
  res.json({ config });
});

router.delete('/:id', async (req, res) => {
  await dacService.deleteConfig(req.params.id);
  res.json({ message: 'Device app config deleted' });
});

export default router;
