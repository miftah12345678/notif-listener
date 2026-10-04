import crypto from 'node:crypto';

function generateFingerprint({ packageName, notificationKey, postedAt, title, text }) {
  const p = packageName || '';
  const k = notificationKey || '';
  const t = title || '';
  const txt = text || '';
  
  const payload = `${Buffer.byteLength(p, 'utf8')}:${p}|${Buffer.byteLength(k, 'utf8')}:${k}|${postedAt || 0}|${Buffer.byteLength(t, 'utf8')}:${t}|${Buffer.byteLength(txt, 'utf8')}:${txt}`;
  return {
    payload,
    hash: crypto.createHash('sha256').update(payload, 'utf8').digest('hex')
  };
}

const fixtures = [
  {
    name: "1. Normal Notification",
    data: { packageName: "id.dana", notificationKey: "0|id.dana|123|null|1000", postedAt: 1696400000000, title: "Transfer", text: "Rp50.000 received" }
  },
  {
    name: "2. Text containing pipe |",
    data: { packageName: "com.whatsapp", notificationKey: "0|com.whatsapp|456", postedAt: 1696400000001, title: "Message", text: "Hello | World ||" }
  },
  {
    name: "3. Multiline text",
    data: { packageName: "com.google.android.gm", notificationKey: "123", postedAt: 1696400000002, title: "Email", text: "Line 1\nLine 2\nLine 3" }
  },
  {
    name: "4. Empty/Null fields",
    data: { packageName: "com.test", notificationKey: "", postedAt: 0, title: null, text: "" }
  },
  {
    name: "5. Unicode/Emoji",
    data: { packageName: "org.telegram", notificationKey: "abc", postedAt: 1696400000003, title: "Alert 🚨", text: "User 日本語 left the group 😭" }
  }
];

console.log("=== FINGERPRINT FIXTURES ===\n");
fixtures.forEach(f => {
  const result = generateFingerprint(f.data);
  console.log(`-- ${f.name} --`);
  console.log(`Input   :`, f.data);
  console.log(`Payload : ${result.payload}`);
  console.log(`SHA-256 : ${result.hash}\n`);
});
