// Classic script: executes before the async Vite module is evaluated.
const params = new URLSearchParams(window.location.search);
document.documentElement.dataset.theme = params.get('theme') === 'dark' ? 'dark' : 'light';
document.documentElement.lang = params.get('language') === 'ru' ? 'ru' : 'en';

window.__npBootStage = 'classic script';
console.info('[NP-Boot] classic web-boot executed for ' + location.pathname);
