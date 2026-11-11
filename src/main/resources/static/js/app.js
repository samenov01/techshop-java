const $ = selector => document.querySelector(selector);
const money = value => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value) + ' тг';
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const statuses = { NEW: 'Новый', CONFIRMED: 'Подтверждён', SHIPPED: 'Отправлен', COMPLETED: 'Завершён', CANCELLED: 'Отменён' };
const payments = { UNPAID: 'Не оплачен', PAID: 'Оплачен', REFUNDED: 'Оплата возвращена' };
let currentUser = null;
let csrf = null;
let categories = [];
let adminProducts = [];
let cart = { items: [], total: 0, count: 0 };
let authMode = 'login';
let currentPage = 'catalog';
let adminTab = 'products';
let chart = null;
let toastTimer;
let catalogRequest = 0;
let catalogAssets = {};
let pendingPayment = null;

function productImage(productName, category = '') {
    if (catalogAssets[productName]) return catalogAssets[productName].image;
    const name = category.toLowerCase();
    if (name.includes('клавиат')) return '/images/keyboard.svg';
    if (name.includes('науш')) return '/images/headphones.svg';
    return '/images/mouse.svg';
}

async function refreshCsrf() {
    const response = await fetch('/api/auth/csrf');
    if (!response.ok) throw new Error('Не удалось подключиться к магазину');
    csrf = await response.json();
}

async function api(path, options = {}) {
    const method = options.method || 'GET';
    const headers = { ...options.headers };
    let body;
    if (options.form) {
        body = new URLSearchParams(options.form);
    } else if (options.body !== undefined) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(options.body);
    }
    if (method !== 'GET') {
        if (!csrf) await refreshCsrf();
        headers[csrf.headerName] = csrf.token;
    }
    let response;
    try {
        response = await fetch('/api' + path, { method, headers, body });
    } catch {
        throw new Error('Нет связи с сервером. Проверьте подключение и попробуйте ещё раз');
    }
    const data = response.status === 204 ? null : await response.json();
    if (!response.ok) {
        const details = Object.values(data.errors || {}).join('. ');
        const error = new Error(details || data.message || 'Не удалось выполнить запрос');
        error.status = response.status;
        if (response.status === 401) {
            currentUser = null;
            updateHeader();
        }
        throw error;
    }
    return data;
}

function toast(message, error = false) {
    clearTimeout(toastTimer);
    const element = $('#toast');
    element.textContent = message;
    element.className = 'toast' + (error ? ' error' : '');
    element.hidden = false;
    toastTimer = setTimeout(() => element.hidden = true, error ? 7000 : 3500);
}

function updateHeader() {
    $('#account-button').textContent = currentUser ? currentUser.name : 'Войти';
    $('#account-button').title = currentUser ? currentUser.email : 'Войти в аккаунт';
    $('#logout-button').hidden = !currentUser;
    $('#admin-link').hidden = currentUser?.role !== 'ADMIN';
    const count = currentUser ? cart.count : 0;
    $('#cart-count').textContent = count;
    $('#floating-cart-count').textContent = count;
    $('#floating-cart-count').hidden = count === 0;
    $('#floating-cart-button').setAttribute('aria-label', 'Корзина, товаров: ' + count);
    renderMiniCart();
}

function emptyState(title, description, action = '') {
    return `<div class="empty-state"><h3>${escapeHtml(title)}</h3><p>${escapeHtml(description)}</p>${action}</div>`;
}

function loginState() {
    return emptyState('Войдите в аккаунт', 'Ваша корзина и заказы будут храниться в личном кабинете.', '<button class="button dark" data-action="login">Войти или зарегистрироваться</button>');
}

async function loadCategories() {
    categories = await api('/categories');
    const selected = $('#category-filter').value;
    const options = categories.map(category => `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join('');
    $('#category-filter').innerHTML = '<option value="">Все категории</option>' + options;
    $('#category-filter').value = categories.some(category => String(category.id) === selected) ? selected : '';
    $('#edit-category').innerHTML = options;
}

async function loadCatalog() {
    const requestId = ++catalogRequest;
    const params = new URLSearchParams();
    for (const [key, value] of new FormData($('#filter-form'))) {
        if (value !== '') params.set(key, value);
    }
    if ($('#search-input').value.trim()) params.set('search', $('#search-input').value.trim());
    params.set('sort', $('#sort-select').value);
    $('#products').innerHTML = '<p class="loading">Загружаем товары…</p>';
    try {
        const products = await api('/products?' + params);
        if (requestId !== catalogRequest) return;
        $('#product-count').textContent = products.length;
        $('#products').innerHTML = products.length ? products.map(product => `<article class="product-card">
            <div class="product-image ${product.categoryName.toLowerCase().includes('клавиат') ? 'keyboard-image' : ''}"><span class="stock ${product.stock ? '' : 'empty'}">${product.stock ? 'В наличии · ' + product.stock : 'Нет в наличии'}</span><img src="${productImage(product.name, product.categoryName)}" alt="" loading="lazy"></div>
            <div class="product-body"><p class="category-label">${escapeHtml(product.categoryName)}</p><h3>${escapeHtml(product.name)}</h3><details><summary>О товаре</summary><p>${escapeHtml(product.description)}</p></details>
            <div class="product-bottom"><span class="price">${money(product.price)}</span><button class="add-button" data-action="add" data-id="${product.id}" aria-label="Добавить ${escapeHtml(product.name)} в корзину" title="В корзину" ${product.stock === 0 ? 'disabled' : ''}>+</button></div></div>
        </article>`).join('') : emptyState('Ничего не найдено', 'Попробуйте другое название или измените фильтры.', '<button class="button" data-action="reset-filters">Сбросить фильтры</button>');
    } catch (error) {
        if (requestId !== catalogRequest) return;
        $('#products').innerHTML = emptyState('Не удалось загрузить каталог', error.message, '<button class="button" data-action="reload-catalog">Повторить</button>');
    }
}

async function loadCart() {
    if (!currentUser) {
        cart = { items: [], total: 0, count: 0 };
        return;
    }
    cart = await api('/cart');
    updateHeader();
}

function renderCart() {
    if (!currentUser) {
        $('#cart-content').innerHTML = loginState();
        return;
    }
    if (!cart.items.length) {
        $('#cart-content').innerHTML = emptyState('Пока здесь пусто', 'Начните с каталога — там найдётся что-нибудь для вашего рабочего места.', '<a class="button dark" href="#catalog">Перейти в каталог</a>');
        return;
    }
    const unavailable = cart.items.some(item => item.quantity > item.stock);
    $('#cart-content').innerHTML = `<div class="cart-layout"><div class="cart-list">${cart.items.map(item => `<div class="cart-row">
        <img src="${productImage(item.name, item.categoryName)}" alt=""><div class="cart-info"><h3>${escapeHtml(item.name)}</h3><p>${money(item.price)} за шт.</p>${item.quantity > item.stock ? '<p class="form-error">Осталось на складе: ' + item.stock + '</p>' : ''}<button class="text-button" data-action="remove" data-id="${item.productId}">Удалить</button></div>
        <div class="quantity"><button data-action="quantity" data-id="${item.productId}" data-value="${item.quantity - 1}" aria-label="Уменьшить количество" ${item.quantity <= 1 ? 'disabled' : ''}>−</button><input type="number" min="1" max="10000" step="1" value="${item.quantity}" data-quantity-id="${item.productId}" aria-label="Количество ${escapeHtml(item.name)}"><button data-action="quantity" data-id="${item.productId}" data-value="${item.quantity + 1}" aria-label="Увеличить количество" ${item.quantity >= item.stock || item.quantity >= 10000 ? 'disabled' : ''}>+</button></div><span class="price">${money(item.subtotal)}</span></div>`).join('')}</div>
        <aside class="summary"><h3>Ваш заказ</h3><div class="summary-line"><span>Итого</span><strong>${money(cart.total)}</strong></div><button class="button dark full" data-action="checkout" ${unavailable ? 'disabled' : ''}>Оформить заказ →</button><p>${unavailable ? 'Измените количество или удалите товары, которых не хватает.' : 'Наличие и итоговая сумма проверяются при оформлении.'}</p><p>После оформления откроется окно оплаты. Списаний нет</p></aside></div>`;
}

function orderCard(order, admin = false) {
    const changeable = ['NEW', 'CONFIRMED'].includes(order.status);
    const next = { NEW: 'CONFIRMED', CONFIRMED: 'SHIPPED', SHIPPED: 'COMPLETED' }[order.status];
    let actions = '';
    if (admin && next) actions += `<button class="button small dark" data-action="status" data-id="${order.id}" data-value="${next}">${{ CONFIRMED: 'Подтвердить', SHIPPED: 'Отправить', COMPLETED: 'Завершить' }[next]}</button>`;
    if (!admin && changeable && order.paymentStatus === 'UNPAID') actions += `<button class="button small dark" data-action="pay" data-id="${order.id}">Оплатить</button>`;
    if (changeable) actions += `<button class="button small danger" data-action="${admin ? 'status' : 'cancel'}" data-value="CANCELLED" data-id="${order.id}">Отменить</button>`;
    return `<article class="order-card"><div class="order-heading"><div><h3>Заказ № ${order.id}</h3><p>${new Date(order.createdAt).toLocaleString('ru-RU', { dateStyle: 'medium', timeStyle: 'short' })}${admin ? ' · ' + escapeHtml(order.customerName) : ''}</p></div><div class="badges"><span class="badge ${order.status === 'CANCELLED' ? 'cancelled' : ''}">${statuses[order.status]}</span><span class="badge">${payments[order.paymentStatus]}</span></div></div>
        ${order.items.map(item => `<div class="order-item"><span>${escapeHtml(item.name)} × ${item.quantity}</span><span>${money(item.unitPrice * item.quantity)}</span></div>`).join('')}
        <div class="order-footer"><span class="price">${money(order.totalAmount)}</span>${actions}</div>${!admin && order.paymentStatus === 'UNPAID' && changeable ? '<p class="payment-note">Демо-режим оплаты · Деньги не списываются</p>' : ''}</article>`;
}

async function renderOrders() {
    if (!currentUser) {
        $('#orders-content').innerHTML = loginState();
        return;
    }
    $('#orders-content').innerHTML = '<p class="loading">Загружаем заказы…</p>';
    const orders = await api('/orders');
    $('#orders-content').innerHTML = orders.length ? orders.map(order => orderCard(order)).join('') : emptyState('Заказов пока нет', 'Когда вы оформите первую покупку, она появится здесь.', '<a class="button dark" href="#catalog">Выбрать товары</a>');
}

async function renderAdmin() {
    if (currentUser?.role !== 'ADMIN') {
        $('#admin-content').innerHTML = emptyState('Доступ для администратора', 'Войдите под учётной записью администратора.', '<button class="button" data-action="login">Войти</button>');
        return;
    }
    const [stats, products, orders] = await Promise.all([api('/admin/statistics'), api('/products'), api('/admin/orders')]);
    await loadCategories();
    adminProducts = products;
    if (chart) chart.destroy();
    $('#admin-content').innerHTML = `<div class="stats-grid"><div class="stat"><p>Всего заказов</p><strong>${stats.orderCount}</strong></div><div class="stat"><p>Товаров в каталоге</p><strong>${stats.productCount}</strong></div><div class="stat"><p>Завершённые продажи</p><strong>${money(stats.revenue)}</strong></div></div>
        <div class="panel"><h3>Продажи за 14 дней</h3><p class="muted">Сумма оплаченных завершённых заказов по дате оформления.</p><div class="chart-wrap"><canvas id="sales-chart" role="img" aria-label="Диаграмма продаж за последние 14 дней"></canvas></div></div>
        <div class="tabs" role="tablist" aria-label="Разделы управления">${[['products', 'Товары'], ['categories', 'Категории'], ['orders', 'Заказы']].map(([key, label]) => `<button role="tab" aria-selected="${adminTab === key}" class="${adminTab === key ? 'active' : ''}" data-action="admin-tab" data-value="${key}">${label}</button>`).join('')}</div>
        <div id="admin-products" ${adminTab !== 'products' ? 'hidden' : ''}><div class="panel"><div class="panel-heading"><h2>Товары</h2><button class="button small dark" data-action="edit-product">+ Добавить товар</button></div><div class="table-wrap"><table><thead><tr><th>Название</th><th>Категория</th><th>Цена</th><th>Остаток</th><th>Действия</th></tr></thead><tbody>${products.map(product => `<tr><td>${escapeHtml(product.name)}</td><td>${escapeHtml(product.categoryName)}</td><td>${money(product.price)}</td><td>${product.stock}</td><td><button class="text-button" data-action="edit-product" data-id="${product.id}">Изменить</button><button class="text-button" data-action="delete-product" data-id="${product.id}">Удалить</button></td></tr>`).join('')}</tbody></table>${products.length ? '' : '<p class="muted">Добавьте первый товар.</p>'}</div></div></div>
        <div id="admin-categories" ${adminTab !== 'categories' ? 'hidden' : ''}><div class="panel"><div class="panel-heading"><h2>Категории</h2><button class="button small dark" data-action="edit-category">+ Добавить</button></div><div class="table-wrap"><table><thead><tr><th>Название</th><th>Действия</th></tr></thead><tbody>${categories.map(category => `<tr><td>${escapeHtml(category.name)}</td><td><button class="text-button" data-action="edit-category" data-id="${category.id}">Изменить</button><button class="text-button" data-action="delete-category" data-id="${category.id}">Удалить</button></td></tr>`).join('')}</tbody></table></div></div></div>
        <div id="admin-orders" ${adminTab !== 'orders' ? 'hidden' : ''}>${orders.length ? orders.map(order => orderCard(order, true)).join('') : emptyState('Заказов пока нет', 'Новые заказы покупателей появятся здесь.')}</div>`;
    chart = new Chart($('#sales-chart'), { type: 'bar', data: { labels: stats.days.map(day => new Date(day.date).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })), datasets: [{ label: 'Продажи, тг', data: stats.days.map(day => day.amount), backgroundColor: '#50755a', borderRadius: 4, maxBarThickness: 35 }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true }, x: { grid: { display: false } } } } });
}

async function route() {
    setMiniCart(false);
    const routeName = location.hash.slice(1);
    currentPage = ['cart', 'orders', 'admin'].includes(routeName) ? routeName : 'catalog';
    document.querySelectorAll('.page').forEach(page => page.hidden = page.id !== currentPage + '-page');
    document.querySelectorAll('[data-nav]').forEach(link => link.classList.toggle('active', link.dataset.nav === currentPage));
    if (routeName !== 'products-anchor') window.scrollTo(0, 0);
    try {
        if (currentPage === 'catalog') await loadCatalog();
        if (currentPage === 'cart') { await loadCart(); renderCart(); }
        if (currentPage === 'orders') await renderOrders();
        if (currentPage === 'admin') await renderAdmin();
    } catch (error) {
        const target = $('#' + currentPage + '-content');
        if (target) target.innerHTML = emptyState('Не удалось загрузить страницу', error.message, '<button class="button" data-action="retry-page">Повторить</button>');
        toast(error.message, true);
    }
}

function showAuth() {
    setMiniCart(false);
    $('#auth-error').textContent = '';
    $('#auth-dialog').showModal();
}

function resetFilters() {
    $('#filter-form').reset();
    $('#search-input').value = '';
    $('#sort-select').value = 'newest';
    loadCatalog();
}

async function action(button) {
    const id = Number(button.dataset.id);
    const value = button.dataset.value;
    const name = button.dataset.action;
    if (name === 'login') return showAuth();
    if (name === 'retry-page') return route();
    if (name === 'reload-catalog') return loadCatalog();
    if (name === 'reset-filters') return resetFilters();
    if (name === 'admin-tab') {
        adminTab = value;
        ['products', 'categories', 'orders'].forEach(tab => $('#admin-' + tab).hidden = tab !== value);
        document.querySelectorAll('[data-action="admin-tab"]').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.value === value);
            tab.setAttribute('aria-selected', String(tab.dataset.value === value));
        });
        return;
    }
    if (name === 'edit-product' || name === 'edit-category') {
        const type = name === 'edit-product' ? 'product' : 'category';
        const form = $('#' + type + '-form');
        form.reset();
        form.elements.id.value = '';
        $('#' + type + '-error').textContent = '';
        const item = (type === 'product' ? adminProducts : categories).find(row => row.id === id);
        if (item) for (const [key, val] of Object.entries(item)) { if (form.elements[key]) form.elements[key].value = val; }
        if (type === 'product') $('#product-dialog-title').textContent = item ? 'Изменить товар' : 'Добавить товар';
        $('#' + type + '-dialog').showModal();
        return;
    }
    if (!currentUser) return showAuth();
    if (['delete-product', 'delete-category', 'cancel'].includes(name) || name === 'status' && value === 'CANCELLED') {
        if (!confirm(name.startsWith('delete') ? 'Удалить эту запись?' : 'Отменить заказ? Товары вернутся на склад.')) return;
    }
    button.disabled = true;
    try {
        if (name === 'add') {
            cart = await api('/cart', { method: 'POST', body: { productId: id, quantity: 1 } });
            updateHeader();
            toast('Товар добавлен в корзину');
        }
        if (name === 'remove' || name === 'quantity') {
            cart = await api('/cart/' + id, { method: name === 'remove' ? 'DELETE' : 'PUT', ...(name === 'quantity' ? { body: { quantity: Number(value) } } : {}) });
            updateHeader(); renderCart();
        }
        if (name === 'checkout') {
            const order = await api('/orders', { method: 'POST' });
            await loadCart();
            setMiniCart(false);
            toast('Заказ № ' + order.id + ' оформлен');
            location.hash = 'orders';
            openPayment(order);
        }
        if (name === 'pay') {
            const orders = await api('/orders');
            const order = orders.find(item => item.id === id);
            if (!order) throw new Error('Заказ не найден');
            openPayment(order);
        }
        if (name === 'cancel') {
            await api('/orders/' + id + '/cancel', { method: 'POST' });
            toast('Заказ отменён');
            await renderOrders();
        }
        if (name === 'status') {
            await api('/admin/orders/' + id + '/status', { method: 'PUT', body: { status: value } });
            toast('Статус обновлён'); await renderAdmin();
        }
        if (name === 'delete-product' || name === 'delete-category') {
            await api('/' + (name === 'delete-product' ? 'products' : 'categories') + '/' + id, { method: 'DELETE' });
            toast('Запись удалена'); await renderAdmin();
        }
    } catch (error) {
        toast(error.message, true);
        if (currentPage === 'cart') { try { await loadCart(); renderCart(); } catch {} }
    } finally {
        button.disabled = false;
    }
}

function renderMiniCart() {
    $('#mini-cart-items-count').textContent = currentUser && cart.count ? '(' + cart.count + ')' : '';
    if (!currentUser) {
        $('#mini-cart-content').innerHTML = '<div class="mini-cart-empty"><p>Войдите, чтобы увидеть свою корзину</p><button class="button dark full" data-action="login">Войти</button></div>';
        return;
    }
    if (!cart.items.length) {
        $('#mini-cart-content').innerHTML = '<div class="mini-cart-empty"><p>Корзина пуста</p><span>Добавьте товары из каталога</span></div>';
        return;
    }
    const unavailable = cart.items.some(item => item.quantity > item.stock);
    $('#mini-cart-content').innerHTML = `<div class="mini-cart-list">${cart.items.map(item => `<div class="mini-cart-row"><img src="${productImage(item.name, item.categoryName)}" alt=""><div class="mini-cart-info"><h3>${escapeHtml(item.name)}</h3><p>${money(item.price)}</p><div class="mini-cart-controls"><div class="quantity"><button type="button" data-action="quantity" data-id="${item.productId}" data-value="${item.quantity - 1}" aria-label="Уменьшить количество ${escapeHtml(item.name)}" ${item.quantity <= 1 ? 'disabled' : ''}>−</button><span>${item.quantity}</span><button type="button" data-action="quantity" data-id="${item.productId}" data-value="${item.quantity + 1}" aria-label="Увеличить количество ${escapeHtml(item.name)}" ${item.quantity >= item.stock || item.quantity >= 10000 ? 'disabled' : ''}>+</button></div><button type="button" class="text-button" data-action="remove" data-id="${item.productId}">Удалить</button></div>${item.quantity > item.stock ? '<p class="form-error">Доступно: ' + item.stock + '</p>' : ''}</div></div>`).join('')}</div><div class="mini-cart-footer"><div><span>Итого</span><strong>${money(cart.total)}</strong></div><button class="button dark full" data-action="checkout" ${unavailable ? 'disabled' : ''}>Оформить заказ</button><a class="mini-cart-link" href="#cart">Открыть корзину</a></div>`;
}

function setMiniCart(open, restoreFocus = false) {
    $('#mini-cart').hidden = !open;
    $('#floating-cart-button').setAttribute('aria-expanded', String(open));
    if (open) $('#mini-cart-close').focus();
    else if (restoreFocus) $('#floating-cart-button').focus();
}

$('#floating-cart-button').addEventListener('click', async () => {
    const opening = $('#mini-cart').hidden;
    setMiniCart(opening);
    if (!opening || !currentUser) return;
    try { await loadCart(); }
    catch (error) { toast(error.message, true); }
});
$('#mini-cart-close').addEventListener('click', () => setMiniCart(false, true));
document.addEventListener('click', event => {
    if (!event.target.closest('.floating-cart') && !$('#mini-cart').hidden) setMiniCart(false);
});
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !$('#mini-cart').hidden) {
        setMiniCart(false, true);
        event.preventDefault();
    }
});

function openPayment(order) {
    setMiniCart(false);
    pendingPayment = { id: order.id, amount: order.totalAmount };
    $('#payment-form').reset();
    $('#payment-form').hidden = false;
    $('#payment-success').hidden = true;
    $('#payment-error').textContent = '';
    $('#payment-order-label').textContent = 'Заказ № ' + order.id;
    $('#payment-amount').textContent = money(order.totalAmount);
    $('#payment-submit').textContent = 'Оплатить ' + money(order.totalAmount);
    $('#payment-submit').disabled = false;
    $('#payment-dialog').showModal();
}

$('#fill-test-card').addEventListener('click', () => {
    const form = $('#payment-form');
    form.elements.cardNumber.value = '4242 4242 4242 4242';
    const expiry = new Date();
    expiry.setFullYear(expiry.getFullYear() + 3);
    form.elements.expiry.value = String(expiry.getMonth() + 1).padStart(2, '0') + '/' + String(expiry.getFullYear()).slice(-2);
    form.elements.cvc.value = '123';
    form.elements.cardholder.value = 'DEMO CUSTOMER';
    $('#payment-error').textContent = '';
});

$('#payment-form').elements.cardNumber.addEventListener('input', event => {
    event.target.value = event.target.value.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim();
});
$('#payment-form').elements.expiry.addEventListener('input', event => {
    const digits = event.target.value.replace(/\D/g, '').slice(0, 4);
    event.target.value = digits.length > 2 ? digits.slice(0, 2) + '/' + digits.slice(2) : digits;
});
$('#payment-form').elements.cvc.addEventListener('input', event => {
    event.target.value = event.target.value.replace(/\D/g, '').slice(0, 3);
});

$('#payment-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const errorElement = $('#payment-error');
    errorElement.textContent = '';
    if (!pendingPayment) return;
    const number = form.elements.cardNumber.value.replace(/\D/g, '');
    if (number !== '4242424242424242') {
        errorElement.textContent = 'В демо-режиме доступна тестовая карта 4242 4242 4242 4242';
        return;
    }
    const parts = form.elements.expiry.value.match(/^(0[1-9]|1[0-2])\/(\d{2})$/);
    const now = new Date();
    if (!parts || new Date(2000 + Number(parts[2]), Number(parts[1]), 1) <= now) {
        errorElement.textContent = 'Проверьте срок действия карты';
        return;
    }
    if (!/^\d{3}$/.test(form.elements.cvc.value) || !form.elements.cardholder.value.trim()) {
        errorElement.textContent = 'Укажите три цифры CVC и имя на карте';
        return;
    }
    const button = $('#payment-submit');
    button.disabled = true;
    button.textContent = 'Обработка оплаты…';
    try {
        const order = await api('/orders/' + pendingPayment.id + '/pay', { method: 'POST' });
        form.reset();
        form.hidden = true;
        $('#payment-success-message').textContent = 'Заказ № ' + order.id + ' · ' + money(order.totalAmount);
        $('#payment-success').hidden = false;
        $('#payment-success button').focus();
        if (currentPage === 'orders') await renderOrders();
    } catch (error) {
        errorElement.textContent = error.message;
    } finally {
        button.disabled = false;
        if (pendingPayment) button.textContent = 'Оплатить ' + money(pendingPayment.amount);
    }
});
$('#payment-dialog').addEventListener('close', () => {
    $('#payment-form').reset();
    pendingPayment = null;
});

document.addEventListener('click', event => {
    const close = event.target.closest('[data-close]');
    if (close) $('#' + close.dataset.close).close();
    const button = event.target.closest('[data-action]');
    if (button && !button.disabled) action(button).catch(error => toast(error.message, true));
});

$('#account-button').addEventListener('click', () => currentUser ? location.hash = 'orders' : showAuth());
$('#auth-switch').addEventListener('click', () => {
    authMode = authMode === 'login' ? 'register' : 'login';
    const register = authMode === 'register';
    $('#auth-title').textContent = register ? 'Создать аккаунт' : 'Вход в аккаунт';
    $('#auth-submit').textContent = register ? 'Зарегистрироваться' : 'Войти';
    $('#auth-switch').textContent = register ? 'Войти' : 'Зарегистрироваться';
    $('#auth-switch-text').textContent = register ? 'Уже есть аккаунт?' : 'Ещё нет аккаунта?';
    $('#name-field').hidden = !register;
    $('#auth-form').elements.name.required = register;
    $('#auth-form').elements.password.autocomplete = register ? 'new-password' : 'current-password';
    $('#auth-error').textContent = '';
});

$('#auth-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    values.email = values.email.trim().toLowerCase();
    $('#auth-submit').disabled = true;
    $('#auth-error').textContent = '';
    try {
        if (authMode === 'register') await api('/auth/register', { method: 'POST', body: values });
        await api('/auth/login', { method: 'POST', form: { email: values.email, password: values.password } });
        await refreshCsrf();
        currentUser = await api('/auth/me');
        await loadCart();
        updateHeader();
        $('#auth-dialog').close();
        form.reset();
        toast('Вы вошли в аккаунт');
        await route();
    } catch (error) {
        $('#auth-error').textContent = error.message;
    } finally { $('#auth-submit').disabled = false; }
});

$('#logout-button').addEventListener('click', async () => {
    try {
        await api('/auth/logout', { method: 'POST' });
        currentUser = null; cart = { items: [], total: 0, count: 0 };
        $('#payment-dialog').close();
        await refreshCsrf(); updateHeader();
        if (location.hash === '#catalog') await route(); else location.hash = 'catalog';
        toast('Вы вышли из аккаунта');
    } catch (error) { toast(error.message, true); }
});

for (const type of ['product', 'category']) {
    $('#' + type + '-form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const values = Object.fromEntries(new FormData(form));
        const id = values.id;
        delete values.id;
        if (type === 'product') {
            values.price = Number(values.price); values.stock = Number(values.stock); values.categoryId = Number(values.categoryId);
        }
        const button = form.querySelector('[type="submit"]');
        button.disabled = true;
        $('#' + type + '-error').textContent = '';
        try {
            await api('/' + (type === 'product' ? 'products' : 'categories') + (id ? '/' + id : ''), { method: id ? 'PUT' : 'POST', body: values });
            $('#' + type + '-dialog').close();
            toast('Изменения сохранены');
            await renderAdmin();
        } catch (error) { $('#' + type + '-error').textContent = error.message; }
        finally { button.disabled = false; }
    });
}

$('#cart-content').addEventListener('change', async event => {
    const input = event.target.closest('[data-quantity-id]');
    if (!input) return;
    if (!input.checkValidity()) { input.reportValidity(); return; }
    input.disabled = true;
    try {
        cart = await api('/cart/' + input.dataset.quantityId, { method: 'PUT', body: { quantity: Number(input.value) } });
        updateHeader(); renderCart();
    } catch (error) { toast(error.message, true); await loadCart(); renderCart(); }
    finally { input.disabled = false; }
});

$('#filter-form').addEventListener('submit', event => { event.preventDefault(); loadCatalog(); });
$('#filter-form').addEventListener('reset', () => setTimeout(loadCatalog, 0));
$('#search-form').addEventListener('submit', event => { event.preventDefault(); loadCatalog(); });
$('#sort-select').addEventListener('change', loadCatalog);
window.addEventListener('hashchange', route);

async function init() {
    await refreshCsrf();
    const assetsResponse = await fetch('/images/catalog.json');
    if (assetsResponse.ok) catalogAssets = await assetsResponse.json();
    try { currentUser = await api('/auth/me'); } catch (error) { if (error.status !== 401) throw error; }
    await Promise.all([loadCategories(), loadCart()]);
    updateHeader();
    await route();
}
init().catch(error => {
    $('#products').innerHTML = emptyState('Не удалось подключиться к магазину', error.message, '<button class="button" id="retry-init">Повторить</button>');
    $('#retry-init').addEventListener('click', () => location.reload());
    toast(error.message, true);
});
