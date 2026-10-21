package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"text/tabwriter"
	"time"

	"github.com/goldrift/instadm/go-core/igcore"
)

type outboxRecord struct {
	OTID      string    `json:"otid"`
	Kind      string    `json:"kind"`
	ThreadID  string    `json:"thread_id"`
	Text      string    `json:"text,omitempty"`
	ReplyTo   string    `json:"reply_to,omitempty"`
	FilePath  string    `json:"file_path,omitempty"`
	Filename  string    `json:"filename,omitempty"`
	Voice     bool      `json:"voice,omitempty"`
	Waveform  string    `json:"waveform,omitempty"`
	SizeBytes int64     `json:"size_bytes,omitempty"`
	CreatedAt time.Time `json:"created_at"`
	Attempts  int       `json:"attempts"`
	LastError string    `json:"last_error,omitempty"`
}

type outboxRetryResult struct {
	OTID        string                  `json:"otid"`
	Kind        string                  `json:"kind"`
	ThreadID    string                  `json:"thread_id"`
	Success     bool                    `json:"success"`
	Error       string                  `json:"error,omitempty"`
	TextResult  *igcore.SendResult      `json:"text_result,omitempty"`
	MediaResult *igcore.MediaSendResult `json:"media_result,omitempty"`
}

const maxOutboxErrorLength = 500

func outboxPath(value string) (string, error) {
	path := strings.TrimSpace(value)
	if path == "" {
		return "", errors.New("outbox path is required")
	}
	absolute, err := filepath.Abs(path)
	if err != nil {
		return "", fmt.Errorf("resolve outbox path: %w", err)
	}
	return absolute, nil
}

func addOutboxRecord(path string, record outboxRecord) error {
	if err := validateOutboxRecord(&record); err != nil {
		return err
	}
	if record.CreatedAt.IsZero() {
		record.CreatedAt = time.Now().UTC()
	}
	return withOutboxLock(path, func() error {
		records, err := loadOutboxLocked(path)
		if err != nil {
			return err
		}
		for _, existing := range records {
			if existing.OTID == record.OTID {
				return fmt.Errorf("OTID %s is already pending in the outbox", record.OTID)
			}
		}
		records = append(records, record)
		return saveOutboxLocked(path, records)
	})
}

func listOutboxRecords(path string) ([]outboxRecord, error) {
	var records []outboxRecord
	err := withOutboxLock(path, func() error {
		var err error
		records, err = loadOutboxLocked(path)
		if err != nil {
			return err
		}
		sort.SliceStable(records, func(i, j int) bool {
			return records[i].CreatedAt.Before(records[j].CreatedAt)
		})
		return nil
	})
	if records == nil {
		records = []outboxRecord{}
	}
	return records, err
}

func removeOutboxRecord(path, otid string) (bool, error) {
	var removed bool
	err := withOutboxLock(path, func() error {
		records, err := loadOutboxLocked(path)
		if err != nil {
			return err
		}
		filtered := records[:0]
		for _, record := range records {
			if record.OTID == otid {
				removed = true
				continue
			}
			filtered = append(filtered, record)
		}
		if !removed {
			return nil
		}
		return saveOutboxLocked(path, filtered)
	})
	return removed, err
}

func updateOutboxRecord(path, otid string, update func(*outboxRecord)) (bool, error) {
	var found bool
	err := withOutboxLock(path, func() error {
		records, err := loadOutboxLocked(path)
		if err != nil {
			return err
		}
		for index := range records {
			if records[index].OTID == otid {
				update(&records[index])
				found = true
				break
			}
		}
		if !found {
			return nil
		}
		return saveOutboxLocked(path, records)
	})
	return found, err
}

func markOutboxFailed(path, otid string, cause error) {
	if path == "" {
		return
	}
	_, _ = updateOutboxRecord(path, otid, func(record *outboxRecord) {
		record.Attempts++
		record.LastError = truncateOutboxError(cause.Error())
	})
}

func withOutboxLock(path string, run func() error) error {
	directory := filepath.Dir(path)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		return fmt.Errorf("create outbox directory: %w", err)
	}
	lockFile, err := os.OpenFile(path+".lock", os.O_CREATE|os.O_RDWR, 0o600)
	if err != nil {
		return fmt.Errorf("open outbox lock: %w", err)
	}
	defer lockFile.Close()
	if err := syscall.Flock(int(lockFile.Fd()), syscall.LOCK_EX); err != nil {
		return fmt.Errorf("lock outbox: %w", err)
	}
	defer func() {
		_ = syscall.Flock(int(lockFile.Fd()), syscall.LOCK_UN)
	}()
	return run()
}

func loadOutboxLocked(path string) ([]outboxRecord, error) {
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read outbox: %w", err)
	}
	if len(strings.TrimSpace(string(data))) == 0 {
		return nil, nil
	}

	var records []outboxRecord
	if err := json.Unmarshal(data, &records); err != nil {
		return nil, fmt.Errorf("parse outbox: %w", err)
	}
	seen := make(map[string]bool, len(records))
	for index := range records {
		if err := validateOutboxRecord(&records[index]); err != nil {
			return nil, fmt.Errorf("outbox record %d: %w", index, err)
		}
		if seen[records[index].OTID] {
			return nil, fmt.Errorf("duplicate outbox OTID %q", records[index].OTID)
		}
		seen[records[index].OTID] = true
	}
	return records, nil
}

func saveOutboxLocked(path string, records []outboxRecord) error {
	if len(records) == 0 {
		if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("remove empty outbox: %w", err)
		}
		return nil
	}

	data, err := json.MarshalIndent(records, "", "  ")
	if err != nil {
		return fmt.Errorf("encode outbox: %w", err)
	}
	data = append(data, '\n')

	temp, err := os.CreateTemp(filepath.Dir(path), ".outbox-*.tmp")
	if err != nil {
		return fmt.Errorf("create outbox temp file: %w", err)
	}
	tempName := temp.Name()
	defer os.Remove(tempName)
	if _, err := temp.Write(data); err != nil {
		_ = temp.Close()
		return fmt.Errorf("write outbox temp file: %w", err)
	}
	if err := temp.Sync(); err != nil {
		_ = temp.Close()
		return fmt.Errorf("sync outbox temp file: %w", err)
	}
	if err := temp.Close(); err != nil {
		return fmt.Errorf("close outbox temp file: %w", err)
	}
	if err := os.Chmod(tempName, 0o600); err != nil {
		return fmt.Errorf("chmod outbox temp file: %w", err)
	}
	if err := os.Rename(tempName, path); err != nil {
		return fmt.Errorf("replace outbox: %w", err)
	}
	return nil
}

func validateOutboxRecord(record *outboxRecord) error {
	record.OTID = strings.TrimSpace(record.OTID)
	record.Kind = strings.TrimSpace(record.Kind)
	record.ThreadID = strings.TrimSpace(record.ThreadID)
	if record.OTID == "" {
		return errors.New("OTID is empty")
	}
	if record.ThreadID == "" {
		return errors.New("thread ID is empty")
	}
	switch record.Kind {
	case "text":
		if strings.TrimSpace(record.Text) == "" {
			return errors.New("text is empty")
		}
	case "media":
		if strings.TrimSpace(record.FilePath) == "" {
			return errors.New("media file path is empty")
		}
	default:
		return fmt.Errorf("unknown kind %q", record.Kind)
	}
	return nil
}

func truncateOutboxError(message string) string {
	message = strings.TrimSpace(message)
	if len(message) <= maxOutboxErrorLength {
		return message
	}
	return message[:maxOutboxErrorLength] + "…"
}

func printOutbox(w *tabwriter.Writer, path string, records []outboxRecord) {
	fmt.Fprintf(w, "Outbox: %s\n", path)
	if len(records) == 0 {
		fmt.Fprintln(w, "No pending sends.")
		return
	}
	fmt.Fprintf(w, "Pending sends: %d\n\n", len(records))
	fmt.Fprintln(w, "OTID\tKIND\tTHREAD\tCREATED\tATTEMPTS\tDETAIL")
	for _, record := range records {
		detail := record.Text
		if record.Kind == "media" {
			detail = record.Filename
			if detail == "" {
				detail = record.FilePath
			}
		}
		if record.LastError != "" {
			detail += " | " + record.LastError
		}
		created := "-"
		if !record.CreatedAt.IsZero() {
			created = record.CreatedAt.Local().Format("2006-01-02 15:04:05")
		}
		fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%d\t%s\n",
			record.OTID,
			record.Kind,
			record.ThreadID,
			created,
			record.Attempts,
			truncate(singleLine(detail), 80),
		)
	}
}

func printOutboxRetryResults(w *tabwriter.Writer, results []outboxRetryResult) {
	succeeded, failed := 0, 0
	for _, result := range results {
		if result.Success {
			succeeded++
		} else {
			failed++
		}
	}
	fmt.Fprintf(w, "Retried %d pending send(s): %d succeeded, %d failed\n", len(results), succeeded, failed)
	for _, result := range results {
		if result.Success {
			messageID := ""
			if result.TextResult != nil {
				messageID = firstNonEmpty(result.TextResult.MessageID, result.TextResult.ID)
			}
			if result.MediaResult != nil {
				messageID = firstNonEmpty(result.MediaResult.MessageID, result.MediaResult.ID)
			}
			fmt.Fprintf(w, "  OK   %s %s -> %s\n", result.Kind, result.OTID, valueOrDash(messageID))
		} else {
			fmt.Fprintf(w, "  FAIL %s %s: %s\n", result.Kind, result.OTID, result.Error)
		}
	}
}

func runOutbox(args []string, stdout, stderr io.Writer) error {
	if len(args) == 0 {
		return errors.New("usage: igcli outbox list|retry|remove --path <outbox.json>")
	}
	command := strings.ToLower(strings.TrimSpace(args[0]))
	args = args[1:]
	switch command {
	case "list", "retry", "remove":
	default:
		return fmt.Errorf("unknown outbox command %q", command)
	}

	flags := flag.NewFlagSet("outbox "+command, flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	pathFlag := flags.String("path", "", "outbox JSON file")
	idFlag := flags.String("id", "", "pending OTID to retry or remove")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}
	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 0 {
		return errors.New("usage: igcli outbox list|retry|remove --path <outbox.json>")
	}

	path, err := outboxPath(*pathFlag)
	if err != nil {
		return err
	}

	switch command {
	case "list":
		records, err := listOutboxRecords(path)
		if err != nil {
			return err
		}
		if options.jsonOutput {
			return encodeJSON(stdout, records)
		}
		writer := tabwriter.NewWriter(stdout, 0, 4, 2, ' ', 0)
		printOutbox(writer, path, records)
		return writer.Flush()

	case "remove":
		otid := strings.TrimSpace(*idFlag)
		if otid == "" {
			return errors.New("--id is required for outbox remove")
		}
		removed, err := removeOutboxRecord(path, otid)
		if err != nil {
			return err
		}
		if !removed {
			return fmt.Errorf("no pending send with OTID %s", otid)
		}
		if options.jsonOutput {
			return encodeJSON(stdout, struct {
				OTID    string `json:"otid"`
				Removed bool   `json:"removed"`
			}{OTID: otid, Removed: true})
		}
		fmt.Fprintf(stdout, "Removed pending send %s\n", otid)
		return nil

	case "retry":
		records, err := listOutboxRecords(path)
		if err != nil {
			return err
		}
		otid := strings.TrimSpace(*idFlag)
		if otid != "" {
			filtered := records[:0]
			for _, record := range records {
				if record.OTID == otid {
					filtered = append(filtered, record)
				}
			}
			records = filtered
		}
		if len(records) == 0 {
			if otid != "" {
				return fmt.Errorf("no pending send with OTID %s", otid)
			}
			return errors.New("no pending sends in the outbox")
		}

		client, err := newClient(options, stderr, nil)
		if err != nil {
			return err
		}
		defer client.Close()

		results := make([]outboxRetryResult, 0, len(records))
		failures := 0
		for _, record := range records {
			result := outboxRetryResult{
				OTID:     record.OTID,
				Kind:     record.Kind,
				ThreadID: record.ThreadID,
			}
			ctx, cancel := commandContext(options.timeout)
			var sendErr error
			switch record.Kind {
			case "text":
				result.TextResult, sendErr = client.SendText(ctx, record.ThreadID, record.Text, record.ReplyTo, record.OTID)
			case "media":
				if record.Voice {
					if record.Waveform != "" {
						data, readErr := os.ReadFile(record.FilePath)
						if readErr != nil {
							sendErr = fmt.Errorf("media file: %w", readErr)
						} else {
							result.MediaResult, sendErr = client.SendVoiceBytes(
								ctx, record.ThreadID, record.Filename, "", data,
								record.Waveform, record.ReplyTo, record.OTID,
							)
						}
					} else {
						result.MediaResult, sendErr = client.SendVoice(ctx, record.ThreadID, record.FilePath, record.ReplyTo, record.OTID)
					}
				} else {
					result.MediaResult, sendErr = client.SendMedia(ctx, record.ThreadID, record.FilePath, record.ReplyTo, record.OTID)
				}
			}
			cancel()

			if sendErr != nil {
				failures++
				result.Error = describeError(sendErr).Error()
				markOutboxFailed(path, record.OTID, sendErr)
			} else {
				result.Success = true
				if _, err := removeOutboxRecord(path, record.OTID); err != nil {
					fmt.Fprintf(stderr, "warning: remove outbox record %s: %v\n", record.OTID, err)
				}
			}
			results = append(results, result)
		}

		if options.jsonOutput {
			if err := encodeJSON(stdout, results); err != nil {
				return err
			}
		} else {
			writer := tabwriter.NewWriter(stdout, 0, 4, 2, ' ', 0)
			printOutboxRetryResults(writer, results)
			if err := writer.Flush(); err != nil {
				return err
			}
		}
		if failures > 0 {
			return fmt.Errorf("%d pending send(s) failed", failures)
		}
		return nil
	}
	return nil
}
