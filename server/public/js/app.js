// public/js/app.js — Client-side JavaScript

document.addEventListener('DOMContentLoaded', () => {
  // ─── Sidebar Toggle (mobile) ─────────────────
  const sidebar = document.getElementById('sidebar');
  const sidebarToggle = document.getElementById('sidebar-toggle');

  if (sidebarToggle && sidebar) {
    sidebarToggle.addEventListener('click', () => {
      sidebar.classList.toggle('open');
    });

    // Close sidebar when clicking outside
    document.addEventListener('click', (e) => {
      if (sidebar.classList.contains('open') &&
          !sidebar.contains(e.target) &&
          !sidebarToggle.contains(e.target)) {
        sidebar.classList.remove('open');
      }
    });
  }

  // ─── Search debounce ─────────────────────────
  const searchInput = document.getElementById('search-input');
  if (searchInput) {
    let debounceTimer;
    searchInput.addEventListener('keyup', (e) => {
      if (e.key === 'Enter') {
        const form = document.getElementById('filters-form');
        if (form) form.submit();
      }
    });
  }

  // ─── Add App Form ────────────────────────────
  const addAppForm = document.getElementById('add-app-form');
  if (addAppForm) {
    addAppForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const packageName = document.getElementById('app-package').value.trim();
      const appName = document.getElementById('app-name').value.trim();

      if (!packageName || !appName) return;

      try {
        const res = await fetch('/api/v1/apps', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ packageName, appName }),
        });

        if (res.ok) {
          window.location.reload();
        } else {
          const data = await res.json();
          alert(data.error || 'Failed to add application');
        }
      } catch (err) {
        alert('Failed to add application');
      }
    });
  }

  // ─── Add Config Form (Device Detail page) ───
  const addConfigForm = document.getElementById('add-config-form');
  if (addConfigForm) {
    addConfigForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const deviceId = document.getElementById('config-device-id').value;
      const applicationId = document.getElementById('config-app-id').value;
      const captureMode = document.getElementById('config-capture-mode').value;
      const parserId = document.getElementById('config-parser').value.trim();

      if (!applicationId) return alert('Please select an application');

      try {
        const res = await fetch('/api/v1/device-apps', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            deviceId,
            applicationId,
            enabled: true,
            captureEnabled: true,
            captureMode,
            parserId: parserId || 'generic',
            webhookEnabled: false,
          }),
        });

        if (res.ok) {
          window.location.reload();
        } else {
          const data = await res.json();
          alert(data.error || 'Failed to add configuration');
        }
      } catch (err) {
        alert('Failed to add configuration');
      }
    });
  }
});

// ─── Modal helpers ───────────────────────────────
function openAddAppModal() {
  const modal = document.getElementById('add-app-modal');
  if (modal) modal.classList.add('active');
}

function closeModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.classList.remove('active');
}

// Close modal on backdrop click
document.addEventListener('click', (e) => {
  if (e.target.classList.contains('modal-backdrop')) {
    e.target.classList.remove('active');
  }
});

// ─── Application CRUD ────────────────────────────
async function editApp(id, currentName) {
  const newName = prompt('Edit application name:', currentName);
  if (!newName || newName === currentName) return;

  try {
    const res = await fetch(`/api/v1/apps/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ appName: newName }),
    });
    if (res.ok) window.location.reload();
    else alert('Failed to update application');
  } catch { alert('Failed to update application'); }
}

async function deleteApp(id, name) {
  if (!confirm(`Delete "${name}" from catalog?\n\nHistorical notifications will be preserved.`)) return;

  try {
    const res = await fetch(`/api/v1/apps/${id}`, { method: 'DELETE' });
    if (res.ok) window.location.reload();
    else alert('Failed to delete application');
  } catch { alert('Failed to delete application'); }
}

// ─── Device App Config actions ───────────────────
async function toggleConfig(id, enabled) {
  try {
    const res = await fetch(`/api/v1/device-apps/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    });
    if (res.ok) window.location.reload();
    else alert('Failed to update config');
  } catch { alert('Failed to update config'); }
}

async function deleteConfig(id) {
  if (!confirm('Remove this app configuration from device?')) return;

  try {
    const res = await fetch(`/api/v1/device-apps/${id}`, { method: 'DELETE' });
    if (res.ok) window.location.reload();
    else alert('Failed to delete config');
  } catch { alert('Failed to delete config'); }
}

// ─── Open Add Config modal (device detail) ──────
async function openAddConfigModal() {
  const modal = document.getElementById('add-config-modal');
  const select = document.getElementById('config-app-id');
  if (!modal || !select) return;

  // Fetch available applications
  try {
    const res = await fetch('/api/v1/apps');
    const data = await res.json();
    select.innerHTML = '<option value="">Select application...</option>';
    (data.applications || []).forEach(app => {
      const opt = document.createElement('option');
      opt.value = app.id;
      opt.textContent = `${app.app_name} (${app.package_name})`;
      select.appendChild(opt);
    });
  } catch { /* keep existing options */ }

  modal.classList.add('active');
}
