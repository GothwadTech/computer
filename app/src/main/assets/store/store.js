// Gothwad Store Engine (HTML5/CSS3/JavaScript)

let currentCategory = "all";
let currentTab = "home";
let searchQuery = "";
let currentCarouselIndex = 0;
let carouselTimer = null;
let installedAppsMap = new Set();
let selectedApp = null;

// Initialize on DOM ready
document.addEventListener("DOMContentLoaded", () => {
  syncInstalledApps();
  setupEventListeners();
  renderStore();
  startHeroCarousel();
});

function syncInstalledApps() {
  if (window.AndroidStoreBridge && window.AndroidStoreBridge.getInstalledAppsJson) {
    try {
      const jsonStr = window.AndroidStoreBridge.getInstalledAppsJson();
      const urls = JSON.parse(jsonStr || "[]");
      installedAppsMap = new Set(urls.map(u => normalizeUrl(u)));
    } catch (e) {
      console.error("Error fetching installed apps:", e);
    }
  }
}

function normalizeUrl(url) {
  if (!url) return "";
  return url.trim().replace(/^https?:\/\//, "").replace(/\/$/, "").toLowerCase();
}

function isAppInstalled(app) {
  const norm = normalizeUrl(app.url);
  return installedAppsMap.has(norm);
}

function setupEventListeners() {
  // Sidebar navigation
  document.querySelectorAll(".nav-item[data-tab]").forEach(item => {
    item.addEventListener("click", () => {
      document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
      item.classList.add("active");
      currentTab = item.getAttribute("data-tab");
      searchQuery = "";
      const searchInput = document.getElementById("searchInput");
      if (searchInput) searchInput.value = "";
      document.getElementById("searchClearBtn").style.display = "none";
      renderStore();
    });
  });

  // Search input
  const searchInput = document.getElementById("searchInput");
  const searchClearBtn = document.getElementById("searchClearBtn");

  searchInput.addEventListener("input", (e) => {
    searchQuery = e.target.value.trim().toLowerCase();
    searchClearBtn.style.display = searchQuery ? "flex" : "none";
    renderStore();
  });

  searchClearBtn.addEventListener("click", () => {
    searchInput.value = "";
    searchQuery = "";
    searchClearBtn.style.display = "none";
    renderStore();
  });

  // Modal close
  document.getElementById("modalCloseBtn").addEventListener("click", closeModal);
  document.getElementById("modalOverlay").addEventListener("click", (e) => {
    if (e.target.id === "modalOverlay") closeModal();
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeModal();
  });
}

function renderStore() {
  const contentArea = document.getElementById("contentArea");
  contentArea.innerHTML = "";

  // Update total apps count badge
  const totalCounter = document.getElementById("totalAppsCount");
  if (totalCounter) {
    totalCounter.textContent = `${STORE_APPS.length} Web Apps`;
  }

  if (searchQuery) {
    renderSearchResults();
    return;
  }

  if (currentTab === "library") {
    renderLibraryView();
    return;
  }

  if (currentTab === "home") {
    renderHomeView();
  } else {
    renderCategoryPageView(currentTab);
  }
}

function renderHomeView() {
  const contentArea = document.getElementById("contentArea");

  // 1. Hero Spotlight Carousel
  const featuredApps = STORE_APPS.filter(a => a.isFeatured).slice(0, 6);
  const heroHtml = document.createElement("div");
  heroHtml.className = "hero-carousel";
  heroHtml.id = "heroCarousel";

  let slidesHtml = "";
  let dotsHtml = "";

  featuredApps.forEach((app, idx) => {
    const isInst = isAppInstalled(app);
    slidesHtml += `
      <div class="carousel-slide ${idx === 0 ? "active" : ""}" style="background: ${app.bannerGradient || 'linear-gradient(135deg, #0078D4 0%, #107C41 100%)'}">
        <div class="slide-content">
          <div class="slide-tag">Featured Cloud App</div>
          <h1 class="slide-title">${app.name}</h1>
          <p class="slide-desc">${app.shortDesc}</p>
          <div class="slide-actions">
            <button class="btn-get ${isInst ? 'btn-installed' : ''}" onclick="onCarouselAction('${app.id}', event)">
              ${isInst ? 'Open App' : 'Get Free'}
            </button>
            <button class="btn-card-action" style="padding: 8px 16px; font-size: 13px;" onclick="openModal('${app.id}')">
              View Details
            </button>
          </div>
        </div>
        <img class="app-icon" style="width: 110px; height: 110px; border-radius: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.5);" 
             src="${app.icon}" alt="${app.name}" onerror="onIconLoadError(this, '${app.name}')"/>
      </div>
    `;
    dotsHtml += `<div class="indicator-dot ${idx === 0 ? "active" : ""}" onclick="goToCarouselSlide(${idx})"></div>`;
  });

  heroHtml.innerHTML = slidesHtml + `<div class="carousel-indicators">${dotsHtml}</div>`;
  contentArea.appendChild(heroHtml);

  // 2. Category Filter Chips
  const chipsRow = document.createElement("div");
  chipsRow.className = "chips-container";
  const categories = [
    { id: "all", label: "All Apps" },
    { id: "ai", label: "AI & Assistants" },
    { id: "social", label: "Social & Chat" },
    { id: "media", label: "Media & Streaming" },
    { id: "productivity", label: "Productivity & Office" },
    { id: "design", label: "Design & Photo" },
    { id: "dev", label: "Developer Tools" },
    { id: "gaming", label: "Gaming" },
    { id: "shopping", label: "Shopping" },
    { id: "news", label: "News & Education" }
  ];

  chipsRow.innerHTML = categories.map(cat => `
    <button class="chip-btn ${currentCategory === cat.id ? 'active' : ''}" onclick="filterCategory('${cat.id}')">
      ${cat.label}
    </button>
  `).join("");
  contentArea.appendChild(chipsRow);

  // 3. Top Free / Essential Apps Horizontal Reel
  const essentials = STORE_APPS.filter(a => a.isEssential);
  const railSection = document.createElement("div");
  railSection.innerHTML = `
    <div class="section-header">
      <div>
        <h2 class="section-title">Top Essential Cloud Apps</h2>
        <p class="section-subtitle">Most popular web apps ready to install on your PC</p>
      </div>
    </div>
    <div class="horizontal-rail">
      ${essentials.map(app => renderRailCard(app)).join("")}
    </div>
  `;
  contentArea.appendChild(railSection);

  // 4. Categorized Apps Grid
  const gridSection = document.createElement("div");
  const filteredApps = currentCategory === "all" 
    ? STORE_APPS 
    : STORE_APPS.filter(a => a.category === currentCategory);

  gridSection.innerHTML = `
    <div class="section-header">
      <div>
        <h2 class="section-title">${getCategoryTitle(currentCategory)}</h2>
        <p class="section-subtitle">${filteredApps.length} web apps available</p>
      </div>
    </div>
    <div class="apps-grid">
      ${filteredApps.map(app => renderGridCard(app)).join("")}
    </div>
  `;
  contentArea.appendChild(gridSection);
}

function renderCategoryPageView(tabKey) {
  const contentArea = document.getElementById("contentArea");
  let catFilter = "all";
  let pageTitle = "Apps";
  let pageSub = "Explore web applications";

  if (tabKey === "apps") {
    catFilter = "productivity";
    pageTitle = "Productivity & Tools";
    pageSub = "Get more done with top cloud workspace apps";
  } else if (tabKey === "gaming") {
    catFilter = "gaming";
    pageTitle = "Gaming & Cloud Play";
    pageSub = "Instant web games and cloud streaming platforms";
  } else if (tabKey === "media") {
    catFilter = "media";
    pageTitle = "Entertainment & Media";
    pageSub = "Stream music, movies, and video platforms";
  } else if (tabKey === "social") {
    catFilter = "social";
    pageTitle = "Social & Messaging";
    pageSub = "Stay connected with family and communities";
  } else if (tabKey === "ai") {
    catFilter = "ai";
    pageTitle = "AI & Innovation";
    pageSub = "Experience the latest artificial intelligence tools";
  }

  const apps = STORE_APPS.filter(a => a.category === catFilter);

  contentArea.innerHTML = `
    <div class="section-header" style="margin-bottom: 20px;">
      <div>
        <h1 class="section-title" style="font-size: 24px;">${pageTitle}</h1>
        <p class="section-subtitle">${pageSub} • ${apps.length} applications</p>
      </div>
    </div>
    <div class="apps-grid">
      ${apps.map(app => renderGridCard(app)).join("")}
    </div>
  `;
}

function renderLibraryView() {
  const contentArea = document.getElementById("contentArea");
  syncInstalledApps();

  const installedApps = STORE_APPS.filter(a => isAppInstalled(a));

  if (installedApps.length === 0) {
    contentArea.innerHTML = `
      <div class="section-header">
        <h1 class="section-title" style="font-size: 24px;">My Library</h1>
      </div>
      <div class="empty-state">
        <svg class="empty-state-icon" viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
        <h3 style="color: #fff; font-size: 16px;">No installed web apps yet</h3>
        <p style="font-size: 13px; max-width: 320px;">Explore the Store and click "Get" on any web application to install it directly onto your desktop!</p>
      </div>
    `;
    return;
  }

  contentArea.innerHTML = `
    <div class="section-header" style="margin-bottom: 20px;">
      <div>
        <h1 class="section-title" style="font-size: 24px;">My Library</h1>
        <p class="section-subtitle">${installedApps.length} installed apps on this PC</p>
      </div>
    </div>
    <div class="apps-grid">
      ${installedApps.map(app => renderGridCard(app)).join("")}
    </div>
  `;
}

function renderSearchResults() {
  const contentArea = document.getElementById("contentArea");
  const matches = STORE_APPS.filter(a => 
    a.name.toLowerCase().includes(searchQuery) ||
    a.developer.toLowerCase().includes(searchQuery) ||
    a.shortDesc.toLowerCase().includes(searchQuery) ||
    a.category.toLowerCase().includes(searchQuery)
  );

  if (matches.length === 0) {
    contentArea.innerHTML = `
      <div class="empty-state">
        <svg class="empty-state-icon" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>
        <h3 style="color: #fff; font-size: 16px;">No results found for "${searchQuery}"</h3>
        <p style="font-size: 13px;">Check the spelling or try searching for another app name or category.</p>
      </div>
    `;
    return;
  }

  contentArea.innerHTML = `
    <div class="section-header" style="margin-bottom: 20px;">
      <div>
        <h1 class="section-title" style="font-size: 20px;">Search results for "${searchQuery}"</h1>
        <p class="section-subtitle">${matches.length} apps found</p>
      </div>
    </div>
    <div class="apps-grid">
      ${matches.map(app => renderGridCard(app)).join("")}
    </div>
  `;
}

function renderRailCard(app) {
  const isInst = isAppInstalled(app);
  return `
    <div class="rail-card" onclick="openModal('${app.id}')">
      <img class="app-icon" src="${app.icon}" alt="${app.name}" onerror="onIconLoadError(this, '${app.name}')"/>
      <div class="app-name">${app.name}</div>
      <div class="app-category">${app.category}</div>
      <div class="app-rating-row">
        <span>★ ${app.rating}</span>
        <span class="app-rating-count">(${app.reviews})</span>
      </div>
      <button class="btn-card-action ${isInst ? 'btn-installed' : ''}" 
              onclick="onCardActionClick('${app.id}', event)">
        ${isInst ? 'Open' : 'Get'}
      </button>
    </div>
  `;
}

function renderGridCard(app) {
  const isInst = isAppInstalled(app);
  return `
    <div class="grid-card" onclick="openModal('${app.id}')">
      <img class="grid-card-icon" src="${app.icon}" alt="${app.name}" onerror="onIconLoadError(this, '${app.name}')"/>
      <div class="grid-card-info">
        <div>
          <div class="grid-card-title">${app.name}</div>
          <div class="grid-card-dev">${app.developer}</div>
        </div>
        <div class="grid-card-bottom">
          <div class="app-rating-row">
            <span>★ ${app.rating}</span>
          </div>
          <button class="btn-card-action ${isInst ? 'btn-installed' : ''}" 
                  onclick="onCardActionClick('${app.id}', event)">
            ${isInst ? 'Open' : 'Get'}
          </button>
        </div>
      </div>
    </div>
  `;
}

function getCategoryTitle(cat) {
  const map = {
    all: "Popular Web Apps",
    ai: "Artificial Intelligence & LLMs",
    social: "Social Networks & Messaging",
    media: "Entertainment, Video & Music",
    productivity: "Productivity & Office Suites",
    design: "Creative & Design Studio",
    dev: "Developer Tools & Code",
    gaming: "Web Gaming & Cloud Platforms",
    shopping: "E-Commerce & Online Shopping",
    news: "News, Reference & Learning"
  };
  return map[cat] || "Apps";
}

function filterCategory(catId) {
  currentCategory = catId;
  renderStore();
}

// Carousel Controls
function startHeroCarousel() {
  if (carouselTimer) clearInterval(carouselTimer);
  carouselTimer = setInterval(() => {
    goToNextSlide();
  }, 5000);
}

function goToNextSlide() {
  const slides = document.querySelectorAll(".carousel-slide");
  if (!slides.length) return;
  currentCarouselIndex = (currentCarouselIndex + 1) % slides.length;
  updateCarouselView();
}

function goToCarouselSlide(idx) {
  currentCarouselIndex = idx;
  updateCarouselView();
  startHeroCarousel();
}

function updateCarouselView() {
  const slides = document.querySelectorAll(".carousel-slide");
  const dots = document.querySelectorAll(".indicator-dot");
  slides.forEach((slide, idx) => {
    slide.classList.toggle("active", idx === currentCarouselIndex);
  });
  dots.forEach((dot, idx) => {
    dot.classList.toggle("active", idx === currentCarouselIndex);
  });
}

function onCarouselAction(appId, event) {
  event.stopPropagation();
  const app = STORE_APPS.find(a => a.id === appId);
  if (!app) return;
  if (isAppInstalled(app)) {
    openAppInDesktop(app);
  } else {
    installAppToDesktop(app);
  }
}

function onCardActionClick(appId, event) {
  event.stopPropagation();
  const app = STORE_APPS.find(a => a.id === appId);
  if (!app) return;
  if (isAppInstalled(app)) {
    openAppInDesktop(app);
  } else {
    installAppToDesktop(app);
  }
}

// Modal View
function openModal(appId) {
  const app = STORE_APPS.find(a => a.id === appId);
  if (!app) return;
  selectedApp = app;

  const isInst = isAppInstalled(app);
  const modalOverlay = document.getElementById("modalOverlay");
  const banner = document.getElementById("modalBanner");
  banner.style.background = app.bannerGradient || "linear-gradient(135deg, #0078D4 0%, #161616 100%)";

  document.getElementById("modalIcon").src = app.icon;
  document.getElementById("modalTitle").textContent = app.name;
  document.getElementById("modalDev").textContent = `${app.developer} • Verified Cloud App`;
  document.getElementById("modalRating").textContent = `★ ${app.rating} (${app.reviews} ratings)`;
  document.getElementById("modalDesc").textContent = app.description || app.shortDesc;

  // Actions
  const btnInstall = document.getElementById("modalBtnInstall");
  const btnOpen = document.getElementById("modalBtnOpen");
  const btnUninstall = document.getElementById("modalBtnUninstall");

  if (isInst) {
    btnInstall.style.display = "none";
    btnOpen.style.display = "inline-flex";
    btnUninstall.style.display = "inline-flex";
  } else {
    btnInstall.style.display = "inline-flex";
    btnInstall.textContent = "Get & Install to Desktop";
    btnOpen.style.display = "none";
    btnUninstall.style.display = "none";
  }

  btnInstall.onclick = () => installAppToDesktop(app);
  btnOpen.onclick = () => openAppInDesktop(app);
  btnUninstall.onclick = () => uninstallAppFromDesktop(app);

  modalOverlay.classList.add("active");
}

function closeModal() {
  const modalOverlay = document.getElementById("modalOverlay");
  modalOverlay.classList.remove("active");
  selectedApp = null;
}

// Bridge Actions
function installAppToDesktop(app) {
  showToast(`Installing ${app.name} to Desktop...`);
  if (window.AndroidStoreBridge && window.AndroidStoreBridge.installApp) {
    window.AndroidStoreBridge.installApp(app.name, app.url, app.icon, app.category);
  } else {
    setTimeout(() => {
      onAppInstalled(app.url);
    }, 800);
  }
}

function uninstallAppFromDesktop(app) {
  showToast(`Removing ${app.name} from Desktop...`);
  if (window.AndroidStoreBridge && window.AndroidStoreBridge.uninstallApp) {
    window.AndroidStoreBridge.uninstallApp(app.url);
  } else {
    setTimeout(() => {
      onAppUninstalled(app.url);
    }, 500);
  }
}

function openAppInDesktop(app) {
  if (window.AndroidStoreBridge && window.AndroidStoreBridge.openApp) {
    window.AndroidStoreBridge.openApp(app.url, app.name);
  } else {
    window.open(app.url, "_blank");
  }
}

// Callbacks invoked from Android Bridge
window.onAppInstalled = function(url) {
  syncInstalledApps();
  const app = STORE_APPS.find(a => normalizeUrl(a.url) === normalizeUrl(url));
  const appName = app ? app.name : "App";
  showToast(`${appName} installed to Desktop!`);
  renderStore();
  if (selectedApp && normalizeUrl(selectedApp.url) === normalizeUrl(url)) {
    openModal(selectedApp.id);
  }
};

window.onAppUninstalled = function(url) {
  syncInstalledApps();
  const app = STORE_APPS.find(a => normalizeUrl(a.url) === normalizeUrl(url));
  const appName = app ? app.name : "App";
  showToast(`${appName} removed from Desktop`);
  renderStore();
  if (selectedApp && normalizeUrl(selectedApp.url) === normalizeUrl(url)) {
    openModal(selectedApp.id);
  }
};

function showToast(msg) {
  const toast = document.getElementById("toastNotice");
  if (!toast) return;
  toast.textContent = msg;
  toast.style.display = "block";
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => {
    toast.style.display = "none";
  }, 3000);
}

// Fallback image generator for broken icon links
function onIconLoadError(img, title) {
  img.onerror = null;
  const letter = (title || "A").trim().charAt(0).toUpperCase();
  const colors = ["#2563EB", "#059669", "#D97706", "#7C3AED", "#DC2626", "#0284C7"];
  const color = colors[Math.abs(title.hashCode ? title.hashCode() : title.length) % colors.length];

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 128 128">
    <rect width="128" height="128" rx="28" fill="${color}"/>
    <text x="64" y="80" fill="#ffffff" font-family="Segoe UI, sans-serif" font-weight="bold" font-size="64" text-anchor="middle">${letter}</text>
  </svg>`;
  img.src = "data:image/svg+xml;utf8," + encodeURIComponent(svg);
}
