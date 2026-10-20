package igcore

import (
	"context"
	"sync"
	"time"
)

const (
	// watchFlapWindow and watchFlapThreshold bound how many post-subscribe
	// disconnects may happen in a short period before the watchdog brakes.
	watchFlapWindow    = 2 * time.Minute
	watchFlapThreshold = 5

	// A flapping server gets a much longer cool-down than an ordinary
	// transient failure, so a flagged session cannot hammer the endpoint.
	watchFlapBackoff    = 2 * time.Minute
	maxWatchFlapBackoff = 5 * time.Minute
)

// flapDetector cancels an in-flight DGW connect when Instagram repeatedly
// accepts the stream and then closes it. The pinned instameow client
// reconnects immediately after a post-subscribe drop, so without this brake a
// session that is accepted but immediately dropped becomes a hot reconnect
// loop with no delay.
//
// Post-subscribe drops are reported by instameow as Disconnected events with
// FailureCount == 0. Failures before the first successful subscribe already
// have their own exponential backoff upstream.
type flapDetector struct {
	mu        sync.Mutex
	window    time.Duration
	threshold int
	now       func() time.Time
	drops     []time.Time
	cancel    context.CancelFunc
	active    bool
	tripped   bool
}

func newFlapDetector(window time.Duration, threshold int) *flapDetector {
	if window <= 0 {
		window = watchFlapWindow
	}
	if threshold <= 0 {
		threshold = watchFlapThreshold
	}
	return &flapDetector{
		window:    window,
		threshold: threshold,
		now:       time.Now,
	}
}

// begin starts a new connect episode and installs the cancel function used to
// stop the inner instameow reconnect loop when the threshold is reached.
func (d *flapDetector) begin(cancel context.CancelFunc) {
	if d == nil {
		return
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	d.active = true
	d.tripped = false
	d.drops = d.drops[:0]
	d.cancel = cancel
}

// recordDrop records one post-subscribe disconnect and reports whether this
// drop tripped the detector.
func (d *flapDetector) recordDrop() bool {
	if d == nil {
		return false
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	if !d.active || d.tripped {
		return false
	}

	now := d.now()
	cutoff := now.Add(-d.window)
	kept := d.drops[:0]
	for _, at := range d.drops {
		if at.After(cutoff) {
			kept = append(kept, at)
		}
	}
	d.drops = append(kept, now)
	if len(d.drops) < d.threshold {
		return false
	}

	d.tripped = true
	if d.cancel != nil {
		d.cancel()
	}
	return true
}

// finish closes the current episode and reports whether it tripped. The next
// begin starts with an empty window.
func (d *flapDetector) finish() bool {
	if d == nil {
		return false
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	tripped := d.tripped
	d.active = false
	d.tripped = false
	d.drops = d.drops[:0]
	d.cancel = nil
	return tripped
}

func nextFlapBackoff(current time.Duration) time.Duration {
	if current <= 0 {
		return watchFlapBackoff
	}
	next := current * 2
	if next > maxWatchFlapBackoff {
		return maxWatchFlapBackoff
	}
	return next
}
