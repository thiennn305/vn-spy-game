/**
 * ============================================================================
 * 📚 CatDeck — Custom Deck Manager & Real-Time Word-Count Parity Validation
 * Handles Zero-Friction Creator Token, 3-Tab Deck Creation, and DB Sync.
 * Conforms to .antigravity/rules.md (Section 3 & 4).
 * ============================================================================
 */
(function(window) {
  'use strict';

  // --- 1. Zero-Friction User Ownership: Creator Token ---
  const TOKEN_KEY = 'cat_creator_token';

  function getOrCreateCreatorToken() {
    let token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      // Check cookies
      const match = document.cookie.match(new RegExp('(^| )' + TOKEN_KEY + '=([^;]+)'));
      if (match) {
        token = match[2];
      }
    }
    if (!token) {
      token = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : ('cat_' + Math.random().toString(36).substring(2, 15));
    }
    localStorage.setItem(TOKEN_KEY, token);
    document.cookie = `${TOKEN_KEY}=${token}; max-age=31536000; path=/; SameSite=Lax`;
    return token;
  }

  const creatorToken = getOrCreateCreatorToken();

  // --- 2. Word-Count Helper ---
  function countWords(str) {
    if (!str || !str.trim()) return 0;
    return str.trim().split(/\s+/).length;
  }

  // --- 3. DOM Elements & State ---
  let dynamicRows = [];
  let parsedFilePairs = [];

  function getBasePath() {
    if (window.__contextPath) {
      return window.__contextPath.endsWith('/') ? window.__contextPath : (window.__contextPath + '/');
    }
    const path = window.location.pathname;
    const match = path.match(/^(\/[^\/]+)\//);
    if (match && match[1] === '/spy-game') {
      return '/spy-game/';
    }
    return '/';
  }

  // --- 4. Dynamic Row Generator with Real-Time Parity Check ---
  function createRowElement(topic = '', civilianWord = '', spyWord = '') {
    const rowId = 'row_' + Math.random().toString(36).substring(2, 9);
    const rowDiv = document.createElement('div');
    rowDiv.className = 'cat-deck-row p-2 rounded-3 border border-dark bg-white';
    rowDiv.id = rowId;

    rowDiv.innerHTML = `
      <div class="row g-2 align-items-center">
        <div class="col-12 col-md-3">
          <input type="text" class="form-control form-control-sm cat-row-topic" placeholder="Chủ đề" value="${escapeHtml(topic)}">
        </div>
        <div class="col-5 col-md-4 position-relative">
          <div class="input-group input-group-sm">
            <input type="text" class="form-control cat-row-civ" placeholder="Từ Dân thường" value="${escapeHtml(civilianWord)}">
            <span class="input-group-text small px-1 civ-count-badge">0 từ</span>
          </div>
        </div>
        <div class="col-5 col-md-4 position-relative">
          <div class="input-group input-group-sm">
            <input type="text" class="form-control cat-row-spy" placeholder="Từ Gián điệp" value="${escapeHtml(spyWord)}">
            <span class="input-group-text small px-1 spy-count-badge">0 từ</span>
          </div>
        </div>
        <div class="col-2 col-md-1 text-end">
          <button type="button" class="btn btn-outline-danger btn-sm p-1 btn-delete-row" title="Xóa cặp từ này">
            🗑️
          </button>
        </div>
      </div>
      <!-- Soft Parity Warning Badge -->
      <div class="cat-warning-badge d-none mt-1 py-1 px-2 row-warning-msg">
        ⚠️ <span>Lưu ý các cặp từ có cùng số lượng chữ sẽ dễ chơi hơn nha!</span>
      </div>
    `;

    const civInput = rowDiv.querySelector('.cat-row-civ');
    const spyInput = rowDiv.querySelector('.cat-row-spy');
    const civBadge = rowDiv.querySelector('.civ-count-badge');
    const spyBadge = rowDiv.querySelector('.spy-count-badge');
    const warningMsg = rowDiv.querySelector('.row-warning-msg');
    const deleteBtn = rowDiv.querySelector('.btn-delete-row');

    function updateRowParity() {
      const c1 = countWords(civInput.value);
      const c2 = countWords(spyInput.value);

      civBadge.textContent = c1 + ' từ';
      spyBadge.textContent = c2 + ' từ';

      // Parity check
      if (c1 > 0 && c2 > 0 && c1 !== c2) {
        // Mismatch: add yellow border and show gentle warning
        civInput.classList.add('cat-warning-border');
        spyInput.classList.add('cat-warning-border');
        warningMsg.classList.remove('d-none');
      } else {
        civInput.classList.remove('cat-warning-border');
        spyInput.classList.remove('cat-warning-border');
        warningMsg.classList.add('d-none');
      }
      updateTotalCounter();
    }

    civInput.addEventListener('input', updateRowParity);
    spyInput.addEventListener('input', updateRowParity);

    deleteBtn.addEventListener('click', () => {
      rowDiv.remove();
      updateTotalCounter();
    });

    // Initial check
    setTimeout(updateRowParity, 0);

    return rowDiv;
  }

  function updateTotalCounter() {
    const container = document.getElementById('dynamic-rows-container');
    const counter = document.getElementById('manual-pair-counter');
    if (container && counter) {
      const rows = container.querySelectorAll('.cat-deck-row');
      counter.textContent = rows.length + ' cặp';
    }
  }

  function addRow(topic = '', civ = '', spy = '') {
    const container = document.getElementById('dynamic-rows-container');
    if (container) {
      const row = createRowElement(topic, civ, spy);
      container.appendChild(row);
      updateTotalCounter();
    }
  }

  function clearAllRows() {
    const container = document.getElementById('dynamic-rows-container');
    if (container) {
      container.innerHTML = '';
      updateTotalCounter();
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // --- 5. Tab 2: Quick Paste Parser ---
  function parsePasteText(text) {
    if (!text || !text.trim()) return [];
    const lines = text.split('\n');
    const results = [];

    lines.forEach(line => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) return;

      let topic = 'Tự do';
      let content = trimmed;

      // Extract topic prefix: "Topic: Word1 | Word2"
      const colonIdx = trimmed.indexOf(':');
      if (colonIdx > 0 && colonIdx < 30) {
        topic = trimmed.substring(0, colonIdx).trim();
        content = trimmed.substring(colonIdx + 1).trim();
      }

      // Detect separator: '|', '-', or ','
      let civ = '';
      let spy = '';
      if (content.includes('|')) {
        const parts = content.split('|');
        civ = parts[0].trim();
        spy = parts[1].trim();
      } else if (content.includes('-')) {
        const parts = content.split('-');
        civ = parts[0].trim();
        spy = parts[1].trim();
      } else if (content.includes(',')) {
        const parts = content.split(',');
        civ = parts[0].trim();
        spy = parts[1].trim();
      }

      if (civ && spy) {
        results.push({ topic, civilianWord: civ, spyWord: spy });
      }
    });

    return results;
  }

  // --- 6. Tab 3: File Upload Parser (<= 100KB) ---
  function handleFileUpload(file) {
    if (!file) return;

    if (file.size > 100 * 1024) {
      if (window.CatAudio) CatAudio.playHiss();
      alert('File vượt quá giới hạn 100KB! Vui lòng chọn file nhẹ hơn.');
      return;
    }

    const reader = new FileReader();
    reader.onload = function(e) {
      const content = e.target.result;
      const fileName = file.name.toLowerCase();
      parsedFilePairs = [];

      try {
        if (fileName.endsWith('.json')) {
          const json = JSON.parse(content);
          const list = Array.isArray(json) ? json : (json.pairs || json.wordPairs || []);
          list.forEach(item => {
            if (item.civilianWord && item.spyWord) {
              parsedFilePairs.push({
                topic: item.topic || 'Tự do',
                civilianWord: item.civilianWord,
                spyWord: item.spyWord
              });
            }
          });
        } else if (fileName.endsWith('.csv')) {
          const lines = content.split(/\r?\n/);
          lines.forEach((line, idx) => {
            if (!line.trim()) return;
            const cols = line.split(',').map(s => s.trim().replace(/^"|"$/g, ''));
            if (idx === 0 && (cols[0].toLowerCase().includes('topic') || cols[1].toLowerCase().includes('civ'))) {
              return; // Skip CSV header
            }
            if (cols.length >= 3) {
              parsedFilePairs.push({ topic: cols[0], civilianWord: cols[1], spyWord: cols[2] });
            } else if (cols.length === 2) {
              parsedFilePairs.push({ topic: 'Tự do', civilianWord: cols[0], spyWord: cols[1] });
            }
          });
        }

        const previewBox = document.getElementById('file-upload-preview');
        const fileNameDisp = document.getElementById('file-name-display');
        const fileCountDisp = document.getElementById('file-pair-count');
        const applyBtn = document.getElementById('btn-apply-file');

        if (parsedFilePairs.length > 0) {
          if (previewBox) previewBox.classList.remove('d-none');
          if (fileNameDisp) fileNameDisp.textContent = file.name;
          if (fileCountDisp) fileCountDisp.textContent = parsedFilePairs.length + ' cặp từ';
          if (applyBtn) applyBtn.disabled = false;
        } else {
          alert('Không tìm thấy cặp từ hợp lệ trong file!');
        }
      } catch (err) {
        if (window.CatAudio) CatAudio.playHiss();
        alert('Lỗi đọc file: ' + err.message);
      }
    };
    reader.readAsText(file);
  }

  // --- 7. API Communication: Load, Save, Delete, Select ---

  async function loadSavedDecks() {
    const savedList = document.getElementById('saved-decks-list');
    const selectActive = document.getElementById('select-active-deck');
    const basePath = getBasePath();
    const lang = document.documentElement.lang || 'vi';

    try {
      const res = await fetch(`${basePath}api/decks?lang=${encodeURIComponent(lang)}&creatorToken=${encodeURIComponent(creatorToken)}`);
      const decks = await res.json();

      // Populate Saved Decks List in Modal
      if (savedList) {
        const customDecks = decks.filter(d => d.custom);
        if (customDecks.length === 0) {
          savedList.innerHTML = '<div class="text-center py-2 text-muted small">Chưa có bộ từ cá nhân nào. Hãy tạo bộ từ đầu tiên của bạn ở trên!</div>';
        } else {
          savedList.innerHTML = customDecks.map(d => `
            <div class="d-flex justify-content-between align-items-center p-2 rounded-3 border border-dark bg-white">
              <div>
                <span class="fw-bold text-dark">${escapeHtml(d.name)}</span>
                <span class="badge cat-badge-gold py-0 px-2 ms-1">${d.pairCount} cặp</span>
              </div>
              <div class="d-flex gap-1">
                <button type="button" class="btn btn-sm btn-outline-success py-0 px-2 btn-use-deck" data-id="${d.id}" data-name="${escapeHtml(d.name)}">
                  Dùng
                </button>
                <button type="button" class="btn btn-sm btn-outline-danger py-0 px-1 btn-delete-deck" data-id="${d.id}">
                  🗑️
                </button>
              </div>
            </div>
          `).join('');

          // Bind delete & use buttons
          savedList.querySelectorAll('.btn-delete-deck').forEach(btn => {
            btn.addEventListener('click', () => deleteDeck(btn.dataset.id));
          });
          savedList.querySelectorAll('.btn-use-deck').forEach(btn => {
            btn.addEventListener('click', () => {
              applyDeckToRoom(btn.dataset.id, btn.dataset.name);
            });
          });
        }
      }

      // Populate Deck Selector Dropdown in Host Settings
      if (selectActive) {
        const currentVal = window.__selectedDeckId || selectActive.value;
        selectActive.innerHTML = decks.map(d => `
          <option value="${d.id}" ${d.id === currentVal ? 'selected' : ''}>
            ${d.custom ? '⭐ [Cá nhân] ' : '🍜 '} ${escapeHtml(d.name)} (${d.pairCount} cặp)
          </option>
        `).join('');
      }

    } catch (err) {
      console.error('Failed to load decks:', err);
    }
  }

  async function saveDeck() {
    const nameInput = document.getElementById('deck-name-input');
    const pinInput = document.getElementById('deck-pin-input');
    const editingId = document.getElementById('editing-deck-id').value;
    const basePath = getBasePath();

    const name = nameInput ? nameInput.value.trim() : '';
    const pin = pinInput ? pinInput.value.trim() : '';

    if (name.length < 3 || name.length > 30) {
      if (nameInput) nameInput.classList.add('is-invalid');
      if (window.CatAudio) CatAudio.playHiss();
      return;
    } else {
      if (nameInput) nameInput.classList.remove('is-invalid');
    }

    // Collect pairs from dynamic rows
    const rows = document.querySelectorAll('#dynamic-rows-container .cat-deck-row');
    const wordPairs = [];

    rows.forEach(r => {
      const topic = r.querySelector('.cat-row-topic').value.trim();
      const civ = r.querySelector('.cat-row-civ').value.trim();
      const spy = r.querySelector('.cat-row-spy').value.trim();
      if (civ && spy) {
        wordPairs.push({ topic: topic || 'Tự do', civilianWord: civ, spyWord: spy });
      }
    });

    if (wordPairs.length < 3) {
      if (window.CatAudio) CatAudio.playHiss();
      alert('Vui lòng nhập ít nhất 3 cặp từ hợp lệ!');
      return;
    }

    const payload = {
      id: editingId || null,
      name: name,
      recoveryPin: pin || null,
      wordPairs: wordPairs
    };

    try {
      const res = await fetch(`${basePath}api/decks?creatorToken=${encodeURIComponent(creatorToken)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const data = await res.json();

      if (res.ok) {
        if (window.CatAudio) CatAudio.playMeow();
        alert('🎉 Đã lưu bộ từ thành công vào Database!');
        nameInput.value = '';
        if (pinInput) pinInput.value = '';
        document.getElementById('editing-deck-id').value = '';
        clearAllRows();
        initDefaultRows();
        loadSavedDecks();

        // Switch to Tab 1
        const tabBtn = document.getElementById('tab-manual-btn');
        if (tabBtn && window.bootstrap) new bootstrap.Tab(tabBtn).show();
      } else {
        if (window.CatAudio) CatAudio.playHiss();
        alert('Lỗi lưu bộ từ: ' + (data.error || 'Vui lòng kiểm tra lại dữ liệu'));
      }
    } catch (err) {
      if (window.CatAudio) CatAudio.playHiss();
      alert('Không thể kết nối đến máy chủ!');
    }
  }

  async function deleteDeck(deckId) {
    if (!confirm('Hoàng thượng thật sự muốn xóa bộ từ này khỏi Database sao? 😿')) return;

    const rawId = deckId.replace('custom_', '');
    const basePath = getBasePath();

    try {
      const res = await fetch(`${basePath}api/decks/${rawId}?creatorToken=${encodeURIComponent(creatorToken)}`, {
        method: 'DELETE'
      });
      if (res.ok) {
        if (window.CatAudio) CatAudio.playMeow();
        loadSavedDecks();
      } else {
        alert('Không thể xóa bộ từ này!');
      }
    } catch (err) {
      alert('Lỗi kết nối khi xóa bộ từ!');
    }
  }

  async function applyDeckToRoom(deckId, deckName) {
    const roomCode = window.__roomCode;
    const hostId = window.__playerId;
    const basePath = getBasePath();

    if (!roomCode || !hostId) return;

    try {
      const res = await fetch(`${basePath}api/decks/select-room-deck?roomCode=${encodeURIComponent(roomCode)}&deckId=${encodeURIComponent(deckId)}&deckName=${encodeURIComponent(deckName)}&hostId=${encodeURIComponent(hostId)}`, {
        method: 'POST'
      });
      if (res.ok) {
        if (window.CatAudio) CatAudio.playMeow();
        // Update select box in UI
        const selectActive = document.getElementById('select-active-deck');
        if (selectActive) selectActive.value = deckId;

        // Close modal
        const modalEl = document.getElementById('customDeckModal');
        if (modalEl && window.bootstrap) {
          bootstrap.Modal.getInstance(modalEl).hide();
        }
        alert(`🐾 Đã chọn bộ từ "${deckName}" cho phòng này!`);
      }
    } catch (err) {
      console.error(err);
    }
  }

  function initDefaultRows() {
    clearAllRows();
    addRow('Ẩm thực', 'Bánh mì', 'Xôi xéo');
    addRow('Đồ uống', 'Cà phê', 'Trà đá');
    addRow('Động vật', 'Mèo', 'Chó');
  }

  // --- 8. Event Bindings on DOMContentLoaded ---
  document.addEventListener('DOMContentLoaded', function() {
    initDefaultRows();
    loadSavedDecks();

    // Add Row Button
    const addRowBtn = document.getElementById('btn-add-deck-row');
    if (addRowBtn) {
      addRowBtn.addEventListener('click', () => addRow('', '', ''));
    }

    // Quick Paste Apply Button
    const pasteInput = document.getElementById('quick-paste-input');
    const pasteSummary = document.getElementById('paste-parsed-summary');
    const applyPasteBtn = document.getElementById('btn-apply-paste');

    if (pasteInput) {
      pasteInput.addEventListener('input', () => {
        const pairs = parsePasteText(pasteInput.value);
        if (pasteSummary) {
          pasteSummary.textContent = pairs.length > 0 ? `Đã nhận diện: ${pairs.length} cặp từ` : 'Chưa nhận diện cặp từ nào';
        }
      });
    }

    if (applyPasteBtn && pasteInput) {
      applyPasteBtn.addEventListener('click', () => {
        const pairs = parsePasteText(pasteInput.value);
        if (pairs.length === 0) {
          alert('Không nhận diện được cặp từ nào từ văn bản đã dán!');
          return;
        }
        clearAllRows();
        pairs.forEach(p => addRow(p.topic, p.civilianWord, p.spyWord));

        // Switch to manual tab
        const tabBtn = document.getElementById('tab-manual-btn');
        if (tabBtn && window.bootstrap) new bootstrap.Tab(tabBtn).show();
        if (window.CatAudio) CatAudio.playMeow();
      });
    }

    // File Upload Handler
    const fileUpload = document.getElementById('deck-file-upload');
    const applyFileBtn = document.getElementById('btn-apply-file');

    if (fileUpload) {
      fileUpload.addEventListener('change', (e) => {
        if (e.target.files && e.target.files[0]) {
          handleFileUpload(e.target.files[0]);
        }
      });
    }

    if (applyFileBtn) {
      applyFileBtn.addEventListener('click', () => {
        if (parsedFilePairs.length === 0) return;
        clearAllRows();
        parsedFilePairs.forEach(p => addRow(p.topic, p.civilianWord, p.spyWord));

        // Switch to manual tab
        const tabBtn = document.getElementById('tab-manual-btn');
        if (tabBtn && window.bootstrap) new bootstrap.Tab(tabBtn).show();
        if (window.CatAudio) CatAudio.playMeow();
      });
    }

    // Save Custom Deck Button
    const saveBtn = document.getElementById('btn-save-custom-deck');
    if (saveBtn) {
      saveBtn.addEventListener('click', saveDeck);
    }

    // Recovery PIN Toggle & Submit
    const showPinBtn = document.getElementById('btn-show-recover-pin');
    const pinBox = document.getElementById('pin-recover-box');
    const submitPinBtn = document.getElementById('btn-submit-recover-pin');

    if (showPinBtn && pinBox) {
      showPinBtn.addEventListener('click', () => pinBox.classList.toggle('d-none'));
    }

    if (submitPinBtn) {
      submitPinBtn.addEventListener('click', async () => {
        const pinVal = document.getElementById('recover-pin-input').value.trim();
        if (pinVal.length < 4) {
          alert('Mã PIN tối thiểu 4 ký tự!');
          return;
        }
        const basePath = getBasePath();
        try {
          const res = await fetch(`${basePath}api/decks/recover?pin=${encodeURIComponent(pinVal)}&creatorToken=${encodeURIComponent(creatorToken)}`, {
            method: 'POST'
          });
          const data = await res.json();
          alert(`Đã khôi phục thành công ${data.recoveredCount} bộ từ!`);
          loadSavedDecks();
        } catch (e) {
          alert('Lỗi khôi phục qua PIN!');
        }
      });
    }

    // Room Deck Selector change listener
    const selectActive = document.getElementById('select-active-deck');
    if (selectActive) {
      selectActive.addEventListener('change', (e) => {
        const selOption = selectActive.options[selectActive.selectedIndex];
        applyDeckToRoom(e.target.value, selOption ? selOption.text : '');
      });
    }
  });

  // Export to global
  window.CatDeck = {
    getCreatorToken: () => creatorToken,
    loadSavedDecks,
    addRow,
    clearAllRows
  };

})(window);
