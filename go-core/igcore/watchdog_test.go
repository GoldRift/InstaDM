package igcore

import (
	"testing"
	"time"

	"go.mau.fi/mautrix-meta/pkg/instameow/slidetypes"
)

func TestFlapDetectorTripsAfterThreshold(t *testing.T) {
	detector := newFlapDetector(time.Minute, 3)
	cancelled := 0
	detector.begin(func() { cancelled++ })

	if detector.recordDrop() {
		t.Fatal("first drop tripped the detector")
	}
	if detector.recordDrop() {
		t.Fatal("second drop tripped the detector")
	}
	if !detector.recordDrop() {
		t.Fatal("third drop did not trip the detector")
	}
	if cancelled != 1 {
		t.Errorf("cancel called %d times, want 1", cancelled)
	}
	if detector.recordDrop() {
		t.Error("a drop after tripping tripped again")
	}
	if cancelled != 1 {
		t.Errorf("cancel called %d times after extra drop, want 1", cancelled)
	}
	if !detector.finish() {
		t.Error("finish did not report the trip")
	}
}

func TestFlapDetectorSlidesWindow(t *testing.T) {
	base := time.Unix(1_700_000_000, 0)
	current := base
	detector := newFlapDetector(time.Minute, 3)
	detector.now = func() time.Time { return current }
	detector.begin(func() {})

	detector.recordDrop()
	current = base.Add(2 * time.Minute)
	detector.recordDrop() // the first drop is outside the window
	current = base.Add(2*time.Minute + time.Second)
	if detector.recordDrop() {
		t.Fatal("drops outside the window counted toward the threshold")
	}
	current = base.Add(2*time.Minute + 2*time.Second)
	if !detector.recordDrop() {
		t.Fatal("third drop inside the window did not trip the detector")
	}
}

func TestFlapDetectorIgnoresDropsOutsideEpisode(t *testing.T) {
	detector := newFlapDetector(time.Minute, 2)
	detector.begin(func() {})
	if detector.finish() {
		t.Fatal("clean episode reported a trip")
	}

	for i := 0; i < 5; i++ {
		if detector.recordDrop() {
			t.Fatal("drop recorded after the episode finished")
		}
	}
}

func TestFlapDetectorBeginClearsWindow(t *testing.T) {
	detector := newFlapDetector(time.Minute, 2)
	detector.begin(func() {})
	detector.recordDrop()
	detector.finish()

	detector.begin(func() {})
	if detector.recordDrop() {
		t.Fatal("previous episode's drop survived begin")
	}
}

func TestNextFlapBackoff(t *testing.T) {
	for _, test := range []struct {
		current time.Duration
		want    time.Duration
	}{
		{current: 0, want: watchFlapBackoff},
		{current: watchFlapBackoff, want: 4 * time.Minute},
		{current: 4 * time.Minute, want: maxWatchFlapBackoff},
		{current: maxWatchFlapBackoff, want: maxWatchFlapBackoff},
		{current: 45 * time.Minute, want: maxWatchFlapBackoff},
	} {
		if got := nextFlapBackoff(test.current); got != test.want {
			t.Errorf("nextFlapBackoff(%s) = %s, want %s", test.current, got, test.want)
		}
	}
}

func TestHandleEventFeedsPostSubscribeDrops(t *testing.T) {
	cancelled := 0
	client := &Client{
		flap:    newFlapDetector(time.Minute, 2),
		onEvent: func(Event) {},
	}
	client.flap.begin(func() { cancelled++ })

	client.handleEvent(&slidetypes.Disconnected{FailureCount: 0})
	if cancelled != 0 {
		t.Fatal("watchdog tripped before the threshold")
	}
	client.handleEvent(&slidetypes.Disconnected{FailureCount: 0})
	if cancelled != 1 {
		t.Fatalf("watchdog did not cancel after two post-subscribe drops (cancelled=%d)", cancelled)
	}
}

func TestHandleEventIgnoresPreSubscribeFailures(t *testing.T) {
	cancelled := 0
	client := &Client{
		flap:    newFlapDetector(time.Minute, 2),
		onEvent: func(Event) {},
	}
	client.flap.begin(func() { cancelled++ })

	for i := 0; i < 5; i++ {
		client.handleEvent(&slidetypes.Disconnected{FailureCount: 1})
	}
	if cancelled != 0 {
		t.Fatal("pre-subscribe failures tripped the post-subscribe watchdog")
	}
	if client.flap.finish() {
		t.Fatal("pre-subscribe failures reported a trip")
	}
}
