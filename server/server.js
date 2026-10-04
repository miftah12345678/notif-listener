import { createApp } from './src/app.js';
import { initDb } from './src/config/database.js';
import { ensureDefaultUser } from './src/auth/dashboard-auth.js';
import logger from './src/config/logger.js';
import env from './src/config/env.js';

const PORT = env.port || 3000;

async function startServer() {
  try {
    await initDb();
    await ensureDefaultUser();
    
    const app = createApp();

    app.listen(PORT, () => {
      logger.info(`Server running on port ${PORT}`);
      logger.info(`Dashboard accessible at http://localhost:${PORT}`);
    });
  } catch (err) {
    logger.error('Failed to start server:', err);
    process.exit(1);
  }
}

startServer();
