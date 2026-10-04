import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.join(__dirname, '..');
const dbPath = path.join(rootDir, 'data', 'integration.sqlite');

let serverProcess;

async function startServer() {
  return new Promise((resolve, reject) => {
    console.log('Starting server...');
    serverProcess = spawn('node', ['server.js'], {
      cwd: rootDir,
      env: { ...process.env, DB_PATH: dbPath, PORT: 3001, LOG_LEVEL: 'info' },
    });
    
    serverProcess.stdout.on('data', (data) => {
      const str = data.toString();
      if (str.includes('Server running on port')) {
        resolve();
      }
    });
    
    serverProcess.stderr.on('data', (data) => {
      console.error(`Server error: ${data}`);
    });
    
    serverProcess.on('error', reject);
  });
}

async function stopServer() {
  return new Promise((resolve) => {
    if (serverProcess) {
      console.log('Stopping server...');
      serverProcess.on('exit', () => resolve());
      serverProcess.kill();
    } else {
      resolve();
    }
  });
}

const API = 'http://127.0.0.1:3001/api/v1';

async function req(method, endpoint, body = null, token = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  
  const res = await fetch(`${API}${endpoint}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  
  const data = await res.json().catch(() => null);
  return { status: res.status, data };
}

async function runTests() {
  try {
    // Cleanup old test DB if exists
    try { await fs.unlink(dbPath); } catch (e) {}

    await startServer();
    console.log('--- Server Started ---');

    // a. create/register Device A
    console.log('Registering Device A...');
    const regA = await req('POST', '/devices/register', { deviceName: 'Device A' });
    if (regA.status !== 201) throw new Error('Failed to register Device A');
    const deviceA = regA.data;
    
    // b. create/register Device B
    console.log('Registering Device B...');
    const regB = await req('POST', '/devices/register', { deviceName: 'Device B' });
    if (regB.status !== 201) throw new Error('Failed to register Device B');
    const deviceB = regB.data;

    // c. create applications
    console.log('Creating Apps...');
    const appDanaRes = await req('POST', '/apps', { packageName: 'id.dana', appName: 'DANA' });
    if (appDanaRes.status !== 201) throw new Error(`App creation failed: ${JSON.stringify(appDanaRes.data)}`);
    const appGopayRes = await req('POST', '/apps', { packageName: 'com.gojek.gopay', appName: 'GoPay' });
    const appWaRes = await req('POST', '/apps', { packageName: 'com.whatsapp', appName: 'WhatsApp' });
    
    const appDana = appDanaRes.data.application;
    const appGopay = appGopayRes.data.application;
    const appWa = appWaRes.data.application;

    // d. create device_app_configs sesuai skenario
    console.log('Setting up Device Configs...');
    // Device A: DANA ON, GoPay ON, WhatsApp OFF
    const c1 = await req('POST', '/device-apps', { deviceId: deviceA.deviceId, applicationId: appDana.id, enabled: true });
    if (c1.status !== 201) throw new Error(`Config error: ${JSON.stringify(c1.data)}`);
    await req('POST', '/device-apps', { deviceId: deviceA.deviceId, applicationId: appGopay.id, enabled: true });
    await req('POST', '/device-apps', { deviceId: deviceA.deviceId, applicationId: appWa.id, enabled: false });

    // Device B: DANA OFF, GoPay ON, WhatsApp ON
    await req('POST', '/device-apps', { deviceId: deviceB.deviceId, applicationId: appDana.id, enabled: false });
    await req('POST', '/device-apps', { deviceId: deviceB.deviceId, applicationId: appGopay.id, enabled: true });
    await req('POST', '/device-apps', { deviceId: deviceB.deviceId, applicationId: appWa.id, enabled: true });

    // e. verify configuration Device A dan Device B benar-benar berbeda
    const confA = await req('GET', `/device-apps?deviceId=${deviceA.deviceId}`);
    const confB = await req('GET', `/device-apps?deviceId=${deviceB.deviceId}`);
    
    console.log('Configs Device A:', confA.data);

    const isADanaEnabled = confA.data.configs.find(c => c.package_name === 'id.dana').enabled === 1;
    const isBDanaEnabled = confB.data.configs.find(c => c.package_name === 'id.dana').enabled === 1;
    const isAWaEnabled = confA.data.configs.find(c => c.package_name === 'com.whatsapp').enabled === 1;
    const isBWaEnabled = confB.data.configs.find(c => c.package_name === 'com.whatsapp').enabled === 1;
    
    if (isADanaEnabled !== true || isBDanaEnabled !== false) throw new Error('DANA config mismatch');
    if (isAWaEnabled !== false || isBWaEnabled !== true) throw new Error('WhatsApp config mismatch');
    console.log('✅ Configuration verified per device.');

    // f. Device A mengirim notification DANA
    console.log('Device A sending DANA notification...');
    const notifPayloadA = {
      events: [{
        packageName: 'id.dana',
        title: 'Transfer',
        text: 'Rp50.000 received',
        postedAt: new Date().toISOString()
      }]
    };
    const notifResA = await req('POST', '/notifications/batch', notifPayloadA, deviceA.token);
    if (notifResA.status !== 200 || notifResA.data.accepted.length !== 1) throw new Error('Failed to ingest from A');
    console.log('✅ Device A notification accepted.');

    // g. Device B mengirim notification WhatsApp
    console.log('Device B sending WhatsApp notification...');
    const notifPayloadB = {
      events: [{
        packageName: 'com.whatsapp',
        title: 'Budi',
        text: 'Halo',
        postedAt: new Date().toISOString()
      }]
    };
    const notifResB = await req('POST', '/notifications/batch', notifPayloadB, deviceB.token);
    if (notifResB.status !== 200 || notifResB.data.accepted.length !== 1) throw new Error('Failed to ingest from B');
    console.log('✅ Device B notification accepted.');

    // h. kirim notification yang sama dua kali
    console.log('Device B re-sending identical batch...');
    const notifResB2 = await req('POST', '/notifications/batch', notifPayloadB, deviceB.token);
    if (notifResB2.data.duplicates.length !== 1 || notifResB2.data.accepted.length !== 0) throw new Error('Idempotency failed');
    console.log('✅ Idempotency verified: Duplicate was caught.');

    // i. pastikan database hanya memiliki satu event untuk fingerprint yang sama (per device)
    const checkDupRes = await req('GET', `/notifications?packageName=com.whatsapp&deviceId=${deviceB.deviceId}`);
    if (checkDupRes.data.notifications.length !== 1) throw new Error('Multiple records found for identical notification in DB');
    console.log('✅ DB strictly holds only 1 record per fingerprint per device.');

    // B. Same fingerprint across devices
    console.log('Device A sending identical fingerprint to Device B...');
    const notifResB_asA = await req('POST', '/notifications/batch', notifPayloadB, deviceA.token);
    if (notifResB_asA.status !== 200 || notifResB_asA.data.accepted.length !== 1) throw new Error('Failed to ingest cross-device identical fingerprint');
    console.log('✅ Device A accepted identical fingerprint (cross-device isolation).');

    // C. Authentication isolation
    console.log('Testing authentication coercion...');
    const spoofPayload = {
      deviceId: deviceB.deviceId,
      events: [{ ...notifPayloadB.events[0], text: 'Spoofed' }]
    };
    const spoofRes = await req('POST', '/notifications/batch', spoofPayload, deviceA.token);
    // Since server ignores payload.deviceId and uses req.device.id, it should be saved under Device A
    const listA_spoof = await req('GET', `/notifications?deviceId=${deviceA.deviceId}`);
    const hasSpoofedA = listA_spoof.data.notifications.some(n => n.text === 'Spoofed');
    const listB_spoof = await req('GET', `/notifications?deviceId=${deviceB.deviceId}`);
    const hasSpoofedB = listB_spoof.data.notifications.some(n => n.text === 'Spoofed');
    if (!hasSpoofedA || hasSpoofedB) throw new Error('Auth isolation failed. Device spoofing succeeded!');
    console.log('✅ Auth isolation verified (Server coerces device_id from Bearer token).');

    // j. pastikan notification Device A tidak muncul sebagai milik Device B
    const nListA = await req('GET', `/notifications?deviceId=${deviceA.deviceId}`);
    const nListB = await req('GET', `/notifications?deviceId=${deviceB.deviceId}`);
    if (nListA.data.notifications.some(n => n.device_id === deviceB.deviceId)) throw new Error('Device B notif found in Device A query');
    if (nListB.data.notifications.some(n => n.device_id === deviceA.deviceId)) throw new Error('Device A notif found in Device B query');
    console.log('✅ Notifications are strictly isolated by device_id.');

    // E. Foreign key integrity
    console.log('Testing ON DELETE SET NULL for notifications...');
    await req('DELETE', `/apps/${appWa.id}`);
    const postDeleteList = await req('GET', `/notifications?deviceId=${deviceB.deviceId}`);
    const historyKept = postDeleteList.data.notifications.find(n => n.package_name === 'com.whatsapp');
    if (!historyKept || historyKept.application_id !== null) throw new Error('Foreign key cascade deleted history or did not SET NULL!');
    console.log('✅ Historical notifications preserved (SET NULL) after app deletion.');

    // k. restart server
    console.log('\n--- Restarting server to test persistence ---');
    await stopServer();
    await startServer();

    // l. pastikan seluruh data tetap tersedia
    const pNListA = await req('GET', `/notifications?deviceId=${deviceA.deviceId}`);
    const pNListB = await req('GET', `/notifications?deviceId=${deviceB.deviceId}`);
    
    console.log('Post-restart lengths:', pNListA.data.notifications.length, pNListB.data.notifications.length);

    // Device A should have 3 notifications now (DANA, cross-device spoof test WhatsApp, and the coercion test payload)
    // Device B should have 1 notification (WhatsApp)
    if (pNListA.data.notifications.length !== 3 || pNListB.data.notifications.length !== 1) throw new Error('Data lost after restart!');
    console.log('✅ Data persisted successfully across restarts.');

    // Extra validations:
    const malformed = await req('POST', '/notifications/batch', { events: [] }, deviceA.token);
    if (malformed.status !== 400) throw new Error('Failed 400 check');
    
    const unauth = await req('POST', '/notifications/batch', notifPayloadA, 'wrong-token');
    if (unauth.status !== 401) throw new Error('Failed 401 check');

    const notFound = await req('GET', '/devices/00000000-0000-0000-0000-000000000000');
    if (notFound.status !== 404) throw new Error('Failed 404 check');
    
    console.log('✅ Extra API checks passed (400, 401, 404).');
    
    console.log('\n🎉 ALL INTEGRATION TESTS PASSED!');
  } catch (err) {
    console.error(`\n❌ TEST FAILED: ${err.message}`);
    console.error(err.stack);
  } finally {
    await stopServer();
  }
}

runTests();
