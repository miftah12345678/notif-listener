import { Router } from 'express';
import { z } from 'zod';
import * as appService from './application.service.js';

const router = Router();

const createSchema = z.object({
  packageName: z.string().min(1).max(255),
  appName: z.string().min(1).max(100),
  icon: z.string().optional(),
});

const updateSchema = z.object({
  appName: z.string().min(1).max(100).optional(),
  icon: z.string().optional(),
});

router.get('/', async (req, res) => {
  const apps = await appService.listApplications();
  res.json({ applications: apps });
});

router.get('/stats', async (req, res) => {
  const stats = await appService.getApplicationStats();
  res.json({ stats });
});

router.get('/:id', async (req, res) => {
  const app = await appService.getApplication(req.params.id);
  if (!app) return res.status(404).json({ error: 'Application not found' });
  res.json({ application: app });
});

router.post('/', async (req, res) => {
  const parsed = createSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Validation failed', details: parsed.error.flatten() });
  }

  try {
    const app = await appService.createApplication(parsed.data);
    res.status(201).json({ application: app });
  } catch (err) {
    if (err.message?.includes('UNIQUE')) {
      return res.status(409).json({ error: 'Application with this package name already exists' });
    }
    res.status(500).json({ error: 'Failed to create application' });
  }
});

router.put('/:id', async (req, res) => {
  const parsed = updateSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Validation failed', details: parsed.error.flatten() });
  }

  const app = await appService.updateApplication(req.params.id, parsed.data);
  if (!app) return res.status(404).json({ error: 'Application not found' });
  res.json({ application: app });
});

router.delete('/:id', async (req, res) => {
  await appService.deleteApplication(req.params.id);
  res.json({ message: 'Application removed from catalog. Historical notifications are preserved.' });
});

export default router;
