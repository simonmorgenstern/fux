document.addEventListener('DOMContentLoaded', function() {
    // State
    let selectedRanges = [];
    let savedGroups = [];
    let ws = null;
    let previewTimeout = null;

    // DOM Elements
    const startInput = document.getElementById('start');
    const endInput = document.getElementById('end');
    const addRangeBtn = document.getElementById('add-range');
    const rangeList = document.getElementById('range-list');
    const noRanges = document.getElementById('no-ranges');
    const groupNameInput = document.getElementById('group-name');
    const saveGroupBtn = document.getElementById('save-group');
    const previewBtn = document.getElementById('preview');
    const clearBtn = document.getElementById('clear');
    const groupsContainer = document.getElementById('groups-container');

    // API base URL (assuming same origin; adjust if needed)
    const API_BASE = '/api';
    // WebSocket URL - assuming Fux controller is on same host as this page, port 80
    const WS_URL = `ws://${window.location.hostname}/`;

    // Initialize
    loadGroups();
    updateRangeList();

    // Event Listeners
    addRangeBtn.addEventListener('click', addRange);
    saveGroupBtn.addEventListener('click', saveGroup);
    previewBtn.addEventListener('click', previewSelection);
    clearBtn.addEventListener('click', clearSelection);

    // Functions
    function addRange() {
        const start = parseInt(startInput.value);
        const end = parseInt(endInput.value);
        if (isNaN(start) || isNaN(end)) {
            alert('Please enter valid numbers');
            return;
        }
        if (start < 0 || end > 387 || start > end) {
            alert('Start must be 0‑387, end must be ≥ start');
            return;
        }
        // Check for overlapping ranges? For simplicity, allow duplicates.
        selectedRanges.push({ start, end });
        updateRangeList();
        // Reset inputs to next logical start
        startInput.value = end + 1;
        endInput.value = Math.min(end + 11, 387);
    }

    function updateRangeList() {
        rangeList.innerHTML = '';
        if (selectedRanges.length === 0) {
            noRanges.style.display = 'block';
            return;
        }
        noRanges.style.display = 'none';
        selectedRanges.forEach((range, index) => {
            const li = document.createElement('li');
            li.innerHTML = `
                <span class="range-text">LEDs ${range.start} – ${range.end} (${range.end - range.start + 1} LEDs)</span>
                <button class="remove-range" data-index="${index}"><i class="fas fa-times"></i></button>
            `;
            rangeList.appendChild(li);
        });
        // Add event listeners to remove buttons
        document.querySelectorAll('.remove-range').forEach(btn => {
            btn.addEventListener('click', function() {
                const index = parseInt(this.getAttribute('data-index'));
                selectedRanges.splice(index, 1);
                updateRangeList();
            });
        });
    }

    function clearSelection() {
        if (selectedRanges.length === 0 && groupNameInput.value.trim() === '') return;
        if (confirm('Clear current selection and unsaved name?')) {
            selectedRanges = [];
            groupNameInput.value = '';
            updateRangeList();
        }
    }

    async function loadGroups() {
        try {
            const response = await fetch(`${API_BASE}/groups`);
            if (!response.ok) throw new Error('Failed to load groups');
            savedGroups = await response.json();
            renderGroups();
        } catch (error) {
            console.error('Error loading groups:', error);
            groupsContainer.innerHTML = '<p class="loading" style="color: var(--danger);">Failed to load groups.</p>';
        }
    }

    function renderGroups() {
        if (savedGroups.length === 0) {
            groupsContainer.innerHTML = '<p class="loading">No saved groups yet.</p>';
            return;
        }
        groupsContainer.innerHTML = '';
        savedGroups.forEach(group => {
            const card = document.createElement('div');
            card.className = 'group-card';
            const rangesHtml = group.ranges.map(r => `<span>${r.start}‑${r.end}</span>`).join('');
            card.innerHTML = `
                <h3>${escapeHtml(group.name)}</h3>
                <div class="ranges">${rangesHtml}</div>
                <div class="actions">
                    <button class="btn secondary edit-group" data-id="${group.id}"><i class="fas fa-edit"></i> Edit</button>
                    <button class="btn danger delete-group" data-id="${group.id}"><i class="fas fa-trash"></i> Delete</button>
                </div>
            `;
            groupsContainer.appendChild(card);
        });
        // Add event listeners for edit and delete
        document.querySelectorAll('.edit-group').forEach(btn => {
            btn.addEventListener('click', function() {
                const id = this.getAttribute('data-id');
                editGroup(id);
            });
        });
        document.querySelectorAll('.delete-group').forEach(btn => {
            btn.addEventListener('click', function() {
                const id = this.getAttribute('data-id');
                deleteGroup(id);
            });
        });
    }

    async function saveGroup() {
        const name = groupNameInput.value.trim();
        if (!name) {
            alert('Please enter a group name');
            return;
        }
        if (selectedRanges.length === 0) {
            alert('Please add at least one LED range');
            return;
        }
        const group = {
            name,
            ranges: selectedRanges.slice() // copy
        };
        try {
            const response = await fetch(`${API_BASE}/groups`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(group)
            });
            if (!response.ok) throw new Error('Failed to save');
            const saved = await response.json();
            savedGroups.push(saved);
            renderGroups();
            // Reset UI
            selectedRanges = [];
            groupNameInput.value = '';
            updateRangeList();
            alert('Group saved successfully!');
        } catch (error) {
            console.error('Error saving group:', error);
            alert('Failed to save group. Check console.');
        }
    }

    async function editGroup(id) {
        const group = savedGroups.find(g => g.id === id);
        if (!group) return;
        // For simplicity, we'll just load the group into the editor
        if (confirm('Load this group into editor? Current unsaved selection will be lost.')) {
            selectedRanges = group.ranges.map(r => ({ ...r }));
            groupNameInput.value = group.name;
            updateRangeList();
            // Scroll to editor
            document.querySelector('.led-selection').scrollIntoView({ behavior: 'smooth' });
        }
    }

    async function deleteGroup(id) {
        if (!confirm('Are you sure you want to delete this group?')) return;
        try {
            const response = await fetch(`${API_BASE}/groups/${id}`, {
                method: 'DELETE'
            });
            if (!response.ok) throw new Error('Failed to delete');
            savedGroups = savedGroups.filter(g => g.id !== id);
            renderGroups();
        } catch (error) {
            console.error('Error deleting group:', error);
            alert('Failed to delete group.');
        }
    }

    function previewSelection() {
        if (selectedRanges.length === 0) {
            alert('Please select some LEDs first.');
            return;
        }
        // Update button state
        const originalText = previewBtn.innerHTML;
        previewBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Previewing...';
        previewBtn.disabled = true;

        // Build pixel array
        const pixels = [];
        // Limit total LEDs to avoid huge message (max 200 LEDs for preview)
        let totalLEDs = 0;
        for (const range of selectedRanges) {
            const count = range.end - range.start + 1;
            totalLEDs += count;
        }
        if (totalLEDs > 200) {
            alert(`Preview limited to 200 LEDs (you selected ${totalLEDs}). Truncating.`);
        }
        let added = 0;
        for (const range of selectedRanges) {
            for (let i = range.start; i <= range.end; i++) {
                if (added >= 200) break;
                pixels.push({ i, r: 100, g: 100, b: 100 }); // dim white
                added++;
            }
            if (added >= 200) break;
        }

        // Send via WebSocket
        sendPreviewCommand(pixels)
            .then(() => {
                // Restore button after 2 seconds
                previewTimeout = setTimeout(() => {
                    previewBtn.innerHTML = originalText;
                    previewBtn.disabled = false;
                }, 2000);
            })
            .catch(err => {
                console.error('Preview failed:', err);
                alert('Preview failed. Is the Fux controller running?');
                previewBtn.innerHTML = originalText;
                previewBtn.disabled = false;
            });
    }

    function sendPreviewCommand(pixels) {
        return new Promise((resolve, reject) => {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.close();
            }
            ws = new WebSocket(WS_URL);
            ws.onopen = () => {
                const command = {
                    type: 'setPixels',
                    pixels: pixels
                };
                ws.send(JSON.stringify(command));
                // Schedule turning off after 1.5 seconds
                setTimeout(() => {
                    const offCommand = {
                        type: 'setPixels',
                        pixels: pixels.map(p => ({ i: p.i, r: 0, g: 0, b: 0 }))
                    };
                    if (ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify(offCommand));
                    }
                    ws.close();
                    resolve();
                }, 1500);
            };
            ws.onerror = (err) => {
                reject(err);
            };
            ws.onclose = () => {
                // nothing
            };
        });
    }

    // Helper to escape HTML
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
});