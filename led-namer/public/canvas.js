class LEDCanvas {
    constructor(canvasElement, options = {}) {
        this.canvas = canvasElement;
        this.ctx = canvasElement.getContext('2d');
        this.leds = []; // {x, y, index, selected}
        this.scale = 1;
        this.offsetX = 0;
        this.offsetY = 0;
        this.selectedIndices = new Set();
        this.selectionMode = 'click'; // 'click', 'box', 'lasso'
        this.isDragging = false;
        this.dragStart = { x: 0, y: 0 };
        this.dragEnd = { x: 0, y: 0 };
        this.boxSelection = false;
        this.lassoPoints = [];

        // Options
        this.ledRadius = options.ledRadius || 6;
        this.ledColor = options.ledColor || '#3498db';
        this.selectedColor = options.selectedColor || '#e74c3c';
        this.boxColor = options.boxColor || '#2eccaa';
        this.backgroundColor = options.backgroundColor || '#1e1e2e';

        // Bind methods
        this.handleMouseDown = this.handleMouseDown.bind(this);
        this.handleMouseMove = this.handleMouseMove.bind(this);
        this.handleMouseUp = this.handleMouseUp.bind(this);
        this.handleTouchStart = this.handleTouchStart.bind(this);
        this.handleTouchMove = this.handleTouchMove.bind(this);
        this.handleTouchEnd = this.handleTouchEnd.bind(this);
        this.handleResize = this.handleResize.bind(this);

        // Event listeners
        this.canvas.addEventListener('mousedown', this.handleMouseDown);
        this.canvas.addEventListener('mousemove', this.handleMouseMove);
        this.canvas.addEventListener('mouseup', this.handleMouseUp);
        this.canvas.addEventListener('touchstart', this.handleTouchStart, { passive: false });
        this.canvas.addEventListener('touchmove', this.handleTouchMove, { passive: false });
        this.canvas.addEventListener('touchend', this.handleTouchEnd);
        window.addEventListener('resize', this.handleResize);

        // Initialize canvas size
        this.resizeCanvas();
    }

    resizeCanvas() {
        const parent = this.canvas.parentElement;
        const width = parent.clientWidth;
        const height = Math.min(500, width * 0.8);
        this.canvas.width = width;
        this.canvas.height = height;
        this.fitToCanvas();
        this.draw();
    }

    // Load LED coordinates from server
    async loadCoords() {
        try {
            const response = await fetch('/api/coords');
            if (!response.ok) throw new Error('Failed to load coordinates');
            const coords = await response.json();
            this.leds = coords.map(c => ({
                x: c.x,
                y: c.y,
                index: c.index,
                selected: false
            }));
            this.fitToCanvas();
            this.draw();
        } catch (err) {
            console.error('Error loading LED coordinates:', err);
        }
    }

    // Scale coordinates to fit canvas with padding
    fitToCanvas() {
        if (this.leds.length === 0) return;
        const padding = 20;
        let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
        this.leds.forEach(led => {
            if (led.x < minX) minX = led.x;
            if (led.x > maxX) maxX = led.x;
            if (led.y < minY) minY = led.y;
            if (led.y > maxY) maxY = led.y;
        });
        const width = maxX - minX;
        const height = maxY - minY;
        const scaleX = (this.canvas.width - padding * 2) / width;
        const scaleY = (this.canvas.height - padding * 2) / height;
        this.scale = Math.min(scaleX, scaleY);
        this.offsetX = padding - minX * this.scale;
        this.offsetY = padding - minY * this.scale;
    }

    // Convert canvas coordinates to LED space
    canvasToLED(x, y) {
        const ledX = (x - this.offsetX) / this.scale;
        const ledY = (y - this.offsetY) / this.scale;
        return { ledX, ledY };
    }

    // Find LED within radius of canvas coordinates
    findLEDAt(x, y, radius = 10) {
        const { ledX, ledY } = this.canvasToLED(x, y);
        let closest = null;
        let minDist = Infinity;
        this.leds.forEach(led => {
            const dx = led.x - ledX;
            const dy = led.y - ledY;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < minDist && dist < radius / this.scale) {
                minDist = dist;
                closest = led;
            }
        });
        return closest;
    }

    // Select LEDs within rectangle (canvas coordinates)
    selectInRect(rect) {
        const { x: x1, y: y1 } = this.canvasToLED(rect.x1, rect.y1);
        const { x: x2, y: y2 } = this.canvasToLED(rect.x2, rect.y2);
        const left = Math.min(x1, x2);
        const right = Math.max(x1, x2);
        const top = Math.min(y1, y2);
        const bottom = Math.max(y1, y2);
        this.leds.forEach(led => {
            if (led.x >= left && led.x <= right && led.y >= top && led.y <= bottom) {
                led.selected = true;
                this.selectedIndices.add(led.index);
            }
        });
        this.draw();
        this.onSelectionChange();
    }

    // Toggle LED selection
    toggleLED(led) {
        led.selected = !led.selected;
        if (led.selected) {
            this.selectedIndices.add(led.index);
        } else {
            this.selectedIndices.delete(led.index);
        }
        this.draw();
        this.onSelectionChange();
    }

    // Clear all selections
    clearSelection() {
        this.leds.forEach(led => led.selected = false);
        this.selectedIndices.clear();
        this.draw();
        this.onSelectionChange();
    }

    // Set selection mode
    setSelectionMode(mode) {
        this.selectionMode = mode;
        this.boxSelection = false;
        this.lassoPoints = [];
    }

    // Draw everything
    draw() {
        const { ctx, canvas } = this;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = this.backgroundColor;
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Draw LEDs
        this.leds.forEach(led => {
            const x = led.x * this.scale + this.offsetX;
            const y = led.y * this.scale + this.offsetY;
            ctx.beginPath();
            ctx.arc(x, y, this.ledRadius, 0, Math.PI * 2);
            ctx.fillStyle = led.selected ? this.selectedColor : this.ledColor;
            ctx.fill();
            ctx.strokeStyle = '#fff';
            ctx.lineWidth = 1;
            ctx.stroke();
        });

        // Draw selection rectangle if dragging
        if (this.isDragging && this.selectionMode === 'box') {
            const { x: x1, y: y1 } = this.dragStart;
            const { x: x2, y: y2 } = this.dragEnd;
            ctx.strokeStyle = this.boxColor;
            ctx.lineWidth = 2;
            ctx.setLineDash([5, 5]);
            ctx.strokeRect(x1, y1, x2 - x1, y2 - y1);
            ctx.setLineDash([]);
        }

        // Draw lasso if dragging
        if (this.isDragging && this.selectionMode === 'lasso' && this.lassoPoints.length > 1) {
            ctx.strokeStyle = this.boxColor;
            ctx.lineWidth = 2;
            ctx.setLineDash([5, 5]);
            ctx.beginPath();
            ctx.moveTo(this.lassoPoints[0].x, this.lassoPoints[0].y);
            for (let i = 1; i < this.lassoPoints.length; i++) {
                ctx.lineTo(this.lassoPoints[i].x, this.lassoPoints[i].y);
            }
            ctx.stroke();
            ctx.setLineDash([]);
        }
    }

    // Event handlers
    handleMouseDown(e) {
        const rect = this.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        this.dragStart = { x, y };
        this.dragEnd = { x, y };

        if (this.selectionMode === 'click') {
            const led = this.findLEDAt(x, y);
            if (led) {
                this.toggleLED(led);
            }
        } else if (this.selectionMode === 'box') {
            this.isDragging = true;
            this.boxSelection = true;
        } else if (this.selectionMode === 'lasso') {
            this.isDragging = true;
            this.lassoPoints = [{ x, y }];
        }
        this.draw();
    }

    handleMouseMove(e) {
        if (!this.isDragging) return;
        const rect = this.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        this.dragEnd = { x, y };
        if (this.selectionMode === 'lasso') {
            this.lassoPoints.push({ x, y });
        }
        this.draw();
    }

    handleMouseUp(e) {
        if (!this.isDragging) return;
        const rect = this.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        this.dragEnd = { x, y };
        if (this.selectionMode === 'box') {
            this.selectInRect({ x1: this.dragStart.x, y1: this.dragStart.y, x2: x, y2: y });
        } else if (this.selectionMode === 'lasso') {
            // Lasso selection not yet implemented
            // For now, treat as box
            this.selectInRect({ x1: this.dragStart.x, y1: this.dragStart.y, x2: x, y2: y });
        }
        this.isDragging = false;
        this.boxSelection = false;
        this.lassoPoints = [];
        this.draw();
    }

    handleTouchStart(e) {
        e.preventDefault();
        if (e.touches.length !== 1) return;
        const rect = this.canvas.getBoundingClientRect();
        const x = e.touches[0].clientX - rect.left;
        const y = e.touches[0].clientY - rect.top;
        this.dragStart = { x, y };
        this.dragEnd = { x, y };
        if (this.selectionMode === 'click') {
            const led = this.findLEDAt(x, y);
            if (led) {
                this.toggleLED(led);
            }
        } else if (this.selectionMode === 'box') {
            this.isDragging = true;
            this.boxSelection = true;
        } else if (this.selectionMode === 'lasso') {
            this.isDragging = true;
            this.lassoPoints = [{ x, y }];
        }
        this.draw();
    }

    handleTouchMove(e) {
        e.preventDefault();
        if (!this.isDragging || e.touches.length !== 1) return;
        const rect = this.canvas.getBoundingClientRect();
        const x = e.touches[0].clientX - rect.left;
        const y = e.touches[0].clientY - rect.top;
        this.dragEnd = { x, y };
        if (this.selectionMode === 'lasso') {
            this.lassoPoints.push({ x, y });
        }
        this.draw();
    }

    handleTouchEnd(e) {
        e.preventDefault();
        if (!this.isDragging) return;
        const rect = this.canvas.getBoundingClientRect();
        const x = e.changedTouches[0].clientX - rect.left;
        const y = e.changedTouches[0].clientY - rect.top;
        this.dragEnd = { x, y };
        if (this.selectionMode === 'box') {
            this.selectInRect({ x1: this.dragStart.x, y1: this.dragStart.y, x2: x, y2: y });
        } else if (this.selectionMode === 'lasso') {
            // Lasso selection not yet implemented
            this.selectInRect({ x1: this.dragStart.x, y1: this.dragStart.y, x2: x, y2: y });
        }
        this.isDragging = false;
        this.boxSelection = false;
        this.lassoPoints = [];
        this.draw();
    }

    handleResize() {
        this.resizeCanvas();
    }

    // Callback when selection changes
    onSelectionChange() {
        const event = new CustomEvent('led-selection-change', {
            detail: { selectedIndices: Array.from(this.selectedIndices) }
        });
        this.canvas.dispatchEvent(event);
    }

    // Convert selected indices to ranges (consecutive)
    getSelectedRanges() {
        const indices = Array.from(this.selectedIndices).sort((a, b) => a - b);
        const ranges = [];
        if (indices.length === 0) return ranges;
        let start = indices[0];
        let end = indices[0];
        for (let i = 1; i < indices.length; i++) {
            if (indices[i] === end + 1) {
                end = indices[i];
            } else {
                ranges.push({ start, end });
                start = indices[i];
                end = indices[i];
            }
        }
        ranges.push({ start, end });
        return ranges;
    }

    // Set selected LEDs from ranges
    setSelectedRanges(ranges) {
        this.clearSelection();
        ranges.forEach(range => {
            for (let i = range.start; i <= range.end; i++) {
                const led = this.leds.find(l => l.index === i);
                if (led) {
                    led.selected = true;
                    this.selectedIndices.add(i);
                }
            }
        });
        this.draw();
        this.onSelectionChange();
    }

    // Set selected LEDs from indices
    setSelectedIndices(indices) {
        this.clearSelection();
        indices.forEach(index => {
            const led = this.leds.find(l => l.index === index);
            if (led) {
                led.selected = true;
                this.selectedIndices.add(index);
            }
        });
        this.draw();
        this.onSelectionChange();
    }
}