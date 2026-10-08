// Screen Idle Shutdown - GitHub Pages App Script
const REPO_OWNER = 'deroki';
const REPO_NAME = 'android_timer_shutdown';
const GITHUB_REPO_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}`;
const DEFAULT_TAG = 'v1.0.0';
const DEFAULT_DOWNLOAD_URL = `${GITHUB_REPO_URL}/releases/download/${DEFAULT_TAG}/ScreenIdleShutdown-${DEFAULT_TAG}.apk`;

document.addEventListener('DOMContentLoaded', () => {
  initReleaseData();
});

async function initReleaseData() {
  const downloadBtn = document.getElementById('primary-download-btn');
  const btnTagLabel = document.getElementById('btn-tag-label');
  const heroTagBadge = document.getElementById('hero-tag-badge');
  const metaVersion = document.getElementById('meta-version');
  const metaSize = document.getElementById('meta-size');
  const metaDate = document.getElementById('meta-date');
  const metaFilename = document.getElementById('meta-filename');
  const releaseTagEl = document.getElementById('release-current-tag');
  const releaseDateEl = document.getElementById('release-current-date');
  const releaseNotesEl = document.getElementById('release-current-notes');
  const qrContainer = document.getElementById('qrcode');

  let activeTag = DEFAULT_TAG;
  let activeDownloadUrl = DEFAULT_DOWNLOAD_URL;
  let activeSizeText = '~11.2 MB';
  let activeDateText = new Date().toLocaleDateString();
  let activeFilename = `ScreenIdleShutdown-${DEFAULT_TAG}.apk`;
  let activeNotes = `Release ${DEFAULT_TAG}\n\n- Automated Screen Idle Shutdown\n- Accessibility Service support (No root required)\n- Superuser fast poweroff for rooted devices\n- Exact AlarmManager countdown resilience\n- OEM Security advisor`;

  try {
    const res = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`);
    if (res.ok) {
      const release = await res.json();
      activeTag = release.tag_name || DEFAULT_TAG;
      
      // Look for an APK asset
      const apkAsset = release.assets && release.assets.find(a => a.name.endsWith('.apk'));
      if (apkAsset) {
        activeDownloadUrl = apkAsset.browser_download_url;
        activeFilename = apkAsset.name;
        if (apkAsset.size) {
          activeSizeText = (apkAsset.size / (1024 * 1024)).toFixed(1) + ' MB';
        }
      } else {
        activeDownloadUrl = `${GITHUB_REPO_URL}/releases/download/${activeTag}/ScreenIdleShutdown-${activeTag}.apk`;
        activeFilename = `ScreenIdleShutdown-${activeTag}.apk`;
      }

      if (release.published_at) {
        activeDateText = new Date(release.published_at).toLocaleDateString(undefined, {
          year: 'numeric',
          month: 'short',
          day: 'numeric'
        });
      }

      if (release.body) {
        activeNotes = release.body;
      }
    }
  } catch (err) {
    console.warn('Could not fetch latest release from GitHub API, using fallback defaults:', err);
  }

  // Update UI Elements
  if (heroTagBadge) heroTagBadge.textContent = activeTag;
  if (btnTagLabel) btnTagLabel.textContent = `(${activeTag})`;
  if (downloadBtn) {
    downloadBtn.href = activeDownloadUrl;
    downloadBtn.setAttribute('download', activeFilename);
  }
  if (metaVersion) metaVersion.textContent = activeTag;
  if (metaSize) metaSize.textContent = activeSizeText;
  if (metaDate) metaDate.textContent = activeDateText;
  if (metaFilename) metaFilename.textContent = activeFilename;
  if (releaseTagEl) releaseTagEl.textContent = activeTag;
  if (releaseDateEl) releaseDateEl.textContent = activeDateText;
  if (releaseNotesEl) releaseNotesEl.textContent = activeNotes;

  // Render QR Code for Mobile Scanning
  renderQRCode(qrContainer, activeDownloadUrl);
}

function renderQRCode(container, url) {
  if (!container) return;
  container.innerHTML = '';

  if (typeof QRCode !== 'undefined') {
    new QRCode(container, {
      text: url,
      width: 130,
      height: 130,
      colorDark: '#0b0f19',
      colorLight: '#ffffff',
      correctLevel: QRCode.CorrectLevel.M
    });
  } else {
    // Fallback if QRCode script not loaded
    const img = document.createElement('img');
    img.src = `https://api.qrserver.com/v1/create-qr-code/?size=130x130&data=${encodeURIComponent(url)}`;
    img.alt = 'Download APK QR Code';
    img.width = 130;
    img.height = 130;
    container.appendChild(img);
  }
}
