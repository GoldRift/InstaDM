package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"text/tabwriter"
	"time"

	"github.com/rs/zerolog"
	"go.mau.fi/mautrix-meta/pkg/messagix/httpclient"

	"github.com/goldrift/instadm/go-core/igcore"
)

const usage = `igcli - Instagram DM protocol lab

Usage:
  igcli mailbox --cookies <path> [flags]
  igcli thread <thread-id> --cookies <path> [flags]
  igcli send <thread-id> "<text>" --cookies <path> [flags]
  igcli send-media <thread-id> <file> --cookies <path> [flags]
  igcli mark-read <thread-id> --cookies <path> [flags]
  igcli react <thread-id> <message-id> <emoji> --cookies <path> [flags]
  igcli watch --cookies <path> [flags]
  igcli help

Commands:
  mailbox    Validate the session and list DM threads
  thread     Fetch a thread and print its most recent messages
  send       Send a text message to an existing thread
  send-media Upload and send a file to an existing thread
  mark-read  Mark a thread read up to the newest or a specific message
  react      Add or remove a reaction on a message
  watch      Connect to DGW and print realtime events until interrupted

Common flags:
  --cookies <path>        Cookie JSON file, or - to read from stdin
  --cookie-header <raw>   Raw "name=value; name=value" Cookie header
  --json                  Print normalized JSON instead of a table
  --log-level <level>     error, warn, info, debug (default: warn)
  --timeout <duration>    Network timeout (default: 90s); bootstrap timeout for watch

Thread flags:
  --limit <n>             Messages to fetch, 1-100 (default: 20)
  --after <cursor>        Load older messages after this cursor

Send flags:
  --reply-to <message-id> Reply to a message ID
  --otid <otid>           Reuse a specific offline threading ID
  --retry <n>             Retry transient failures with the same OTID (default: 0)

Send-media flags:
  --reply-to <message-id> Reply to a message ID
  --otid <otid>           Reuse a specific offline threading ID

Mark-read flags:
  --message-id <id>       Mark read up to this message (default: newest message)

React flags:
  --remove                Remove the reaction instead of adding it

Watch flags:
  --duration <duration>   Stop after this duration (0 = until interrupted)
  --thread <thread-id>    Only print events for this thread

Cookie input can also be supplied through IG_COOKIES (raw header or JSON).

Required cookies: sessionid, csrftoken, ds_user_id
Optional cookies: mid, ig_did, rur, shbid, shbts

Use the short thread ID from "igcli mailbox --json" (the "id" field) as <thread-id>.
For thread, send, send-media, mark-read, and react, put all flags before or after the
positional arguments; do not split them across both positions.
`

func main() {
	if err := run(os.Args[1:], os.Stdout, os.Stderr); err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

func run(args []string, stdout, stderr io.Writer) error {
	if len(args) == 0 {
		fmt.Fprint(stderr, usage)
		return errors.New("no command specified")
	}

	switch args[0] {
	case "mailbox":
		return runMailbox(args[1:], stdout, stderr)
	case "thread":
		return runThread(args[1:], stdout, stderr)
	case "send":
		return runSend(args[1:], stdout, stderr)
	case "send-media":
		return runSendMedia(args[1:], stdout, stderr)
	case "mark-read":
		return runMarkRead(args[1:], stdout, stderr)
	case "react":
		return runReact(args[1:], stdout, stderr)
	case "watch":
		return runWatch(args[1:], stdout, stderr)
	case "help", "-h", "--help":
		fmt.Fprint(stdout, usage)
		return nil
	default:
		fmt.Fprint(stderr, usage)
		return fmt.Errorf("unknown command %q", args[0])
	}
}

type commonOptions struct {
	cookiesPath  string
	cookieHeader string
	jsonOutput   bool
	logLevel     string
	timeout      time.Duration
}

func addCommonFlags(flags *flag.FlagSet, options *commonOptions) {
	flags.StringVar(&options.cookiesPath, "cookies", "", "cookie JSON file, or - for stdin")
	flags.StringVar(&options.cookieHeader, "cookie-header", "", "raw Cookie header")
	flags.BoolVar(&options.jsonOutput, "json", false, "print normalized JSON")
	flags.StringVar(&options.logLevel, "log-level", "warn", "error, warn, info, or debug")
	flags.DurationVar(&options.timeout, "timeout", 90*time.Second, "network timeout")
}

func runMailbox(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("mailbox", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() > 0 {
		return fmt.Errorf("unexpected arguments: %s", strings.Join(flags.Args(), " "))
	}

	client, err := newClient(options, stderr, nil)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, cancel := commandContext(options.timeout)
	defer cancel()

	mailbox, err := client.LoadMailbox(ctx)
	if err != nil {
		return describeError(err)
	}

	if options.jsonOutput {
		return encodeJSON(stdout, mailbox)
	}
	printMailbox(stdout, mailbox)
	return nil
}

func runThread(args []string, stdout, stderr io.Writer) error {
	// Go's flag package stops parsing at the first non-flag argument. Accept
	// the natural "thread <id> --flags" form by moving the leading positional
	// to the end. Mixed order ("--limit 5 <id> --cookies ...") is not supported.
	if len(args) > 1 && !strings.HasPrefix(args[0], "-") {
		args = append(append([]string{}, args[1:]...), args[0])
	}

	flags := flag.NewFlagSet("thread", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	limit := flags.Int("limit", 20, "recent messages to fetch (1-100)")
	after := flags.String("after", "", "load older messages after this cursor")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 1 {
		return errors.New("usage: igcli thread <thread-id> --cookies <path>")
	}
	threadID := strings.TrimSpace(flags.Arg(0))
	if threadID == "" {
		return errors.New("thread ID is empty")
	}
	if *limit < 1 || *limit > 100 {
		return errors.New("--limit must be between 1 and 100")
	}

	client, err := newClient(options, stderr, nil)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, cancel := commandContext(options.timeout)
	defer cancel()

	if strings.TrimSpace(*after) != "" {
		page, err := client.LoadOlderMessages(ctx, threadID, *after, *limit)
		if err != nil {
			return describeError(err)
		}
		if options.jsonOutput {
			return encodeJSON(stdout, page)
		}
		printThreadPage(stdout, page)
		return nil
	}

	detail, err := client.LoadThread(ctx, threadID, *limit)
	if err != nil {
		return describeError(err)
	}

	if options.jsonOutput {
		return encodeJSON(stdout, detail)
	}
	printThread(stdout, detail)
	return nil
}

func runSend(args []string, stdout, stderr io.Writer) error {
	// Accept "send <id> <text> --flags" by moving the two leading positionals
	// after the flags. Go's flag package otherwise stops at <id>.
	if len(args) > 2 && !strings.HasPrefix(args[0], "-") && !strings.HasPrefix(args[1], "-") {
		args = append(append([]string{}, args[2:]...), args[0], args[1])
	}

	flags := flag.NewFlagSet("send", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	replyTo := flags.String("reply-to", "", "message ID to reply to")
	otid := flags.String("otid", "", "reuse a specific offline threading ID")
	retries := flags.Int("retry", 0, "retry transient send failures with the same OTID")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 2 {
		return errors.New(`usage: igcli send <thread-id> "<text>" --cookies <path>`)
	}

	threadID := strings.TrimSpace(flags.Arg(0))
	text := flags.Arg(1)
	if threadID == "" {
		return errors.New("thread ID is empty")
	}
	if strings.TrimSpace(text) == "" {
		return errors.New("text is required")
	}
	if length := len([]rune(text)); length > igcore.MaxTextLength {
		return fmt.Errorf("text is %d characters; the maximum is %d", length, igcore.MaxTextLength)
	}
	if *otid != "" {
		if _, err := strconv.ParseInt(*otid, 10, 64); err != nil {
			return errors.New("--otid must be a numeric offline threading ID")
		}
	}
	if *retries < 0 {
		return errors.New("--retry must be zero or greater")
	}

	otidValue := *otid
	if otidValue == "" {
		otidValue = igcore.NewOTID()
	}

	client, err := newClient(options, stderr, nil)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, cancel := commandContext(options.timeout)
	defer cancel()

	var result *igcore.SendResult
	for attempt := 0; ; attempt++ {
		result, err = client.SendText(ctx, threadID, text, *replyTo, otidValue)
		if err == nil {
			break
		}
		if attempt >= *retries || httpclient.IsPermanentRequestError(err) {
			return fmt.Errorf("%w (otid %s)", describeError(err), otidValue)
		}

		delay := time.Duration(1<<min(attempt, 3)) * time.Second
		fmt.Fprintf(stderr, "send failed (attempt %d); retrying with otid %s in %s: %v\n", attempt+1, otidValue, delay, err)
		select {
		case <-ctx.Done():
			return fmt.Errorf("%w (otid %s)", describeError(ctx.Err()), otidValue)
		case <-time.After(delay):
		}
	}

	if options.jsonOutput {
		return encodeJSON(stdout, result)
	}
	printSendResult(stdout, result)
	return nil
}

func runSendMedia(args []string, stdout, stderr io.Writer) error {
	// Accept "send-media <id> <file> --flags" by moving the two leading
	// positionals after the flags.
	if len(args) > 2 && !strings.HasPrefix(args[0], "-") && !strings.HasPrefix(args[1], "-") {
		args = append(append([]string{}, args[2:]...), args[0], args[1])
	}

	flags := flag.NewFlagSet("send-media", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	replyTo := flags.String("reply-to", "", "message ID to reply to")
	otid := flags.String("otid", "", "reuse a specific offline threading ID")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 2 {
		return errors.New(`usage: igcli send-media <thread-id> <file> --cookies <path>`)
	}

	threadID := strings.TrimSpace(flags.Arg(0))
	filePath := strings.TrimSpace(flags.Arg(1))
	if threadID == "" {
		return errors.New("thread ID is empty")
	}
	if filePath == "" {
		return errors.New("file path is empty")
	}
	if info, err := os.Stat(filePath); err != nil {
		return fmt.Errorf("media file: %w", err)
	} else if info.IsDir() {
		return errors.New("media path is a directory")
	}
	if *otid != "" {
		if _, err := strconv.ParseInt(*otid, 10, 64); err != nil {
			return errors.New("--otid must be a numeric offline threading ID")
		}
	}

	otidValue := *otid
	if otidValue == "" {
		otidValue = igcore.NewOTID()
	}

	client, err := newClient(options, stderr, nil)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, cancel := commandContext(options.timeout)
	defer cancel()

	result, err := client.SendMedia(ctx, threadID, filePath, *replyTo, otidValue)
	if err != nil {
		return describeError(err)
	}

	if options.jsonOutput {
		return encodeJSON(stdout, result)
	}
	printMediaSendResult(stdout, result)
	return nil
}

func runMarkRead(args []string, stdout, stderr io.Writer) error {
	// Accept "mark-read <id> --flags" by moving the leading positional after
	// the flags.
	if len(args) > 1 && !strings.HasPrefix(args[0], "-") {
		args = append(append([]string{}, args[1:]...), args[0])
	}

	flags := flag.NewFlagSet("mark-read", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	messageID := flags.String("message-id", "", "mark read up to this message ID")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 1 {
		return errors.New("usage: igcli mark-read <thread-id> --cookies <path>")
	}

	threadID := strings.TrimSpace(flags.Arg(0))
	if threadID == "" {
		return errors.New("thread ID is empty")
	}

	client, err := newClient(options, stderr, nil)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, cancel := commandContext(options.timeout)
	defer cancel()

	result, err := client.MarkThreadRead(ctx, threadID, *messageID)
	if err != nil {
		return describeError(err)
	}

	if options.jsonOutput {
		return encodeJSON(stdout, result)
	}
	printMarkReadResult(stdout, result)
	return nil
}

func runReact(args []string, stdout, stderr io.Writer) error {
	// Accept "react <thread> <message> <emoji> --flags" by moving the three
	// leading positionals after the flags.
	if len(args) > 3 &&
		!strings.HasPrefix(args[0], "-") &&
		!strings.HasPrefix(args[1], "-") &&
		!strings.HasPrefix(args[2], "-") {
		args = append(append([]string{}, args[3:]...), args[0], args[1], args[2])
	}

	flags := flag.NewFlagSet("react", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	remove := flags.Bool("remove", false, "remove the reaction instead of adding it")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() != 3 {
		return errors.New(`usage: igcli react <thread-id> <message-id> <emoji> --cookies <path>`)
	}

	threadID := strings.TrimSpace(flags.Arg(0))
	messageID := strings.TrimSpace(flags.Arg(1))
	emoji := strings.TrimSpace(flags.Arg(2))
	if threadID == "" {
		return errors.New("thread ID is empty")
	}
	if messageID == "" {
		return errors.New("message ID is empty")
	}
	if emoji == "" {
		return errors.New("emoji is empty")
	}

	client, err := newClient(options, stderr, nil)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, cancel := commandContext(options.timeout)
	defer cancel()

	result, err := client.SendReaction(ctx, threadID, messageID, emoji, *remove)
	if err != nil {
		return describeError(err)
	}

	if options.jsonOutput {
		return encodeJSON(stdout, result)
	}
	printReactionResult(stdout, result)
	return nil
}

func runWatch(args []string, stdout, stderr io.Writer) error {
	flags := flag.NewFlagSet("watch", flag.ContinueOnError)
	flags.SetOutput(stderr)
	var options commonOptions
	addCommonFlags(flags, &options)
	duration := flags.Duration("duration", 0, "stop after this duration (0 = until interrupted)")
	threadFilter := flags.String("thread", "", "only print events for this thread")
	flags.Usage = func() {
		fmt.Fprint(stderr, usage)
	}

	if err := flags.Parse(args); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return nil
		}
		return err
	}
	if flags.NArg() > 0 {
		return fmt.Errorf("unexpected arguments: %s", strings.Join(flags.Args(), " "))
	}

	printer := &eventPrinter{
		out:          stdout,
		threadFilter: strings.TrimSpace(*threadFilter),
		jsonOutput:   options.jsonOutput,
	}

	client, err := newClient(options, stderr, printer.print)
	if err != nil {
		return err
	}
	defer client.Close()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	bootstrapCtx, cancelBootstrap := context.WithTimeout(ctx, options.timeout)
	err = client.Bootstrap(bootstrapCtx)
	cancelBootstrap()
	if err != nil {
		return describeError(err)
	}

	watchCtx := ctx
	if *duration > 0 {
		var cancel context.CancelFunc
		watchCtx, cancel = context.WithTimeout(ctx, *duration)
		defer cancel()
	}

	err = client.Watch(watchCtx)
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return nil
	}
	if err != nil {
		return describeError(err)
	}
	return nil
}

func newClient(options commonOptions, stderr io.Writer, onEvent func(igcore.Event)) (*igcore.Client, error) {
	rawCookies, err := loadCookieInput(options.cookiesPath, options.cookieHeader)
	if err != nil {
		return nil, err
	}
	warnIfCookieFileIsReadable(options.cookiesPath, stderr)

	cs, err := igcore.ParseCookies(rawCookies)
	if err != nil {
		return nil, err
	}

	level, err := parseLogLevel(options.logLevel)
	if err != nil {
		return nil, err
	}

	return igcore.New(cs, igcore.Options{
		LogLevel:  level,
		LogOutput: stderr,
		OnEvent:   onEvent,
	})
}

func commandContext(timeout time.Duration) (context.Context, context.CancelFunc) {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	ctx, cancel := context.WithTimeout(ctx, timeout)
	return ctx, func() {
		cancel()
		stop()
	}
}

func encodeJSON(w io.Writer, value any) error {
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	return encoder.Encode(value)
}

func loadCookieInput(cookiesPath, cookieHeader string) ([]byte, error) {
	sources := 0
	if cookiesPath != "" {
		sources++
	}
	if cookieHeader != "" {
		sources++
	}
	if sources > 1 {
		return nil, errors.New("use only one of --cookies and --cookie-header")
	}

	if cookieHeader != "" {
		return []byte(cookieHeader), nil
	}

	if cookiesPath == "" {
		if env := strings.TrimSpace(os.Getenv("IG_COOKIES")); env != "" {
			return []byte(env), nil
		}
		return nil, errors.New("no cookie input: use --cookies, --cookie-header, or IG_COOKIES")
	}

	if cookiesPath == "-" {
		data, err := io.ReadAll(os.Stdin)
		if err != nil {
			return nil, fmt.Errorf("read cookies from stdin: %w", err)
		}
		return data, nil
	}

	data, err := os.ReadFile(cookiesPath)
	if err != nil {
		return nil, fmt.Errorf("read cookies file: %w", err)
	}
	return data, nil
}

func warnIfCookieFileIsReadable(path string, stderr io.Writer) {
	if path == "" || path == "-" {
		return
	}
	info, err := os.Stat(path)
	if err != nil {
		return
	}
	if info.Mode().Perm()&0o077 != 0 {
		fmt.Fprintf(stderr, "warning: %s is readable by other users; run: chmod 600 %s\n", path, path)
	}
}

func parseLogLevel(raw string) (zerolog.Level, error) {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "error":
		return zerolog.ErrorLevel, nil
	case "warn", "warning":
		return zerolog.WarnLevel, nil
	case "info":
		return zerolog.InfoLevel, nil
	case "debug":
		return zerolog.DebugLevel, nil
	default:
		return zerolog.NoLevel, fmt.Errorf("invalid log level %q (use error, warn, info, or debug)", raw)
	}
}

func describeError(err error) error {
	redirect := httpclient.GetErrorRedirectURL(err)
	withRedirect := func(message string) error {
		if redirect != "" {
			return fmt.Errorf("%s (redirected to %s): %w", message, redirect, err)
		}
		return fmt.Errorf("%s: %w", message, err)
	}

	switch {
	case errors.Is(err, httpclient.ErrRateLimited):
		return withRedirect("Instagram rate limited this request; wait before retrying")
	case errors.Is(err, httpclient.ErrTokenInvalidated):
		return withRedirect("Instagram session is invalid or expired; export fresh cookies and try again")
	case errors.Is(err, httpclient.ErrChallengeRequired):
		return withRedirect("Instagram requires a challenge; complete it in the Instagram app or browser, then export fresh cookies")
	case errors.Is(err, httpclient.ErrCheckpointRequired):
		return withRedirect("Instagram requires a checkpoint; complete it in the Instagram app or browser, then export fresh cookies")
	case errors.Is(err, httpclient.ErrConsentRequired):
		return withRedirect("Instagram requires a consent step; complete it in the Instagram app or browser, then export fresh cookies")
	case errors.Is(err, httpclient.ErrAccountSuspended):
		return withRedirect("the Instagram account is suspended")
	case errors.Is(err, context.DeadlineExceeded):
		return errors.New("request timed out; check the network or raise --timeout")
	case errors.Is(err, context.Canceled):
		return errors.New("request canceled")
	default:
		return err
	}
}

func printMailbox(w io.Writer, mailbox *igcore.MailboxView) {
	viewer := mailbox.Viewer
	identity := viewer.Name
	if viewer.Username != "" {
		if identity != "" {
			identity += " (@" + viewer.Username + ")"
		} else {
			identity = "@" + viewer.Username
		}
	}
	if identity == "" {
		identity = "(unknown)"
	}

	fmt.Fprintf(w, "Logged in as %s\n", identity)
	fmt.Fprintf(w, "IG ID: %s   FBID: %d\n", valueOrDash(viewer.IGID), viewer.FBID)
	fmt.Fprintf(w, "seq_id: %d\n", mailbox.SeqID)
	fmt.Fprintf(w, "threads: %d", len(mailbox.Threads))
	if mailbox.HasNextPage {
		fmt.Fprint(w, " (more available)")
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w)

	table := tabwriter.NewWriter(w, 0, 4, 2, ' ', 0)
	fmt.Fprintln(table, "ID\tTHREAD FBID\tTITLE\tLAST ACTIVITY\tFOLDER\tFLAGS")
	for _, thread := range mailbox.Threads {
		fmt.Fprintf(
			table,
			"%s\t%s\t%s\t%s\t%s\t%s\n",
			valueOrDash(thread.ID),
			valueOrDash(thread.ThreadFBID),
			truncate(thread.Title, 48),
			formatTimestamp(thread.LastActivityMS),
			valueOrDash(firstNonEmpty(thread.Folder, thread.SystemFolder)),
			threadFlags(thread),
		)
	}
	_ = table.Flush()

	fmt.Fprintln(w)
	fmt.Fprintln(w, "Tip: use --json for full thread IDs and participant details.")
}

func printThread(w io.Writer, detail *igcore.ThreadDetailView) {
	thread := detail.Thread
	fmt.Fprintf(w, "Thread: %s\n", valueOrDash(thread.Title))
	fmt.Fprintf(
		w,
		"ID: %s   Thread FBID: %s   Folder: %s   Subtype: %s\n",
		valueOrDash(thread.ID),
		valueOrDash(thread.ThreadFBID),
		valueOrDash(firstNonEmpty(thread.Folder, thread.SystemFolder)),
		valueOrDash(thread.Subtype),
	)
	if len(thread.Participants) > 0 {
		names := make([]string, 0, len(thread.Participants))
		for _, participant := range thread.Participants {
			name := firstNonEmpty(participant.Name, participant.Username)
			if name == "" {
				name = fmt.Sprintf("fbid:%d", participant.FBID)
			}
			names = append(names, name)
		}
		fmt.Fprintf(w, "Participants: %s\n", strings.Join(names, ", "))
	}
	fmt.Fprintf(w, "Messages: %d", len(detail.Messages))
	if detail.HasMoreOlder {
		fmt.Fprint(w, " (older messages available)")
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w)

	if len(detail.Messages) == 0 {
		fmt.Fprintln(w, "No messages returned.")
		return
	}

	printMessageTable(w, detail.Messages)

	fmt.Fprintln(w)
	fmt.Fprintln(w, "Tip: use --json for message IDs and full metadata. Message text is sensitive.")
}

func printThreadPage(w io.Writer, page *igcore.ThreadPageView) {
	fmt.Fprintf(w, "Thread: %s (older page)\n", valueOrDash(page.ThreadID))
	fmt.Fprintf(w, "Messages: %d", len(page.Messages))
	if page.HasMoreOlder {
		fmt.Fprint(w, " (older messages available)")
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w)

	if len(page.Messages) == 0 {
		fmt.Fprintln(w, "No messages returned.")
		return
	}

	printMessageTable(w, page.Messages)

	fmt.Fprintln(w)
	if page.OldestCursor != "" {
		fmt.Fprintf(w, "Next cursor: %s\n", page.OldestCursor)
	}
	fmt.Fprintln(w, "Tip: use --json for message IDs and full metadata. Message text is sensitive.")
}

func printMessageTable(w io.Writer, messages []igcore.MessageView) {
	table := tabwriter.NewWriter(w, 0, 4, 2, ' ', 0)
	fmt.Fprintln(table, "TIME\tFROM\tTYPE\tTEXT")
	for _, message := range messages {
		fmt.Fprintf(
			table,
			"%s\t%s\t%s\t%s\n",
			formatMessageTimestamp(message.TimestampMS),
			senderLabel(message),
			valueOrDash(message.Kind),
			truncate(singleLine(displayMessageText(message)), 100),
		)
	}
	_ = table.Flush()
}

func printSendResult(w io.Writer, result *igcore.SendResult) {
	fmt.Fprintln(w, "Sent message")
	fmt.Fprintf(w, "  thread: %s\n", valueOrDash(result.ThreadID))
	fmt.Fprintf(w, "  message id: %s\n", valueOrDash(firstNonEmpty(result.MessageID, result.ID)))
	if result.ID != "" && result.ID != result.MessageID {
		fmt.Fprintf(w, "  id: %s\n", result.ID)
	}
	fmt.Fprintf(w, "  otid: %s\n", valueOrDash(result.OfflineThreadingID))
	fmt.Fprintf(w, "  timestamp: %s\n", formatMessageTimestamp(result.TimestampMS))
	fmt.Fprintf(w, "  text: %s\n", truncate(singleLine(result.Text), 100))
}

func printMediaSendResult(w io.Writer, result *igcore.MediaSendResult) {
	fmt.Fprintln(w, "Sent media")
	fmt.Fprintf(w, "  thread: %s\n", valueOrDash(result.ThreadID))
	fmt.Fprintf(w, "  message id: %s\n", valueOrDash(firstNonEmpty(result.MessageID, result.ID)))
	if result.ID != "" && result.ID != result.MessageID {
		fmt.Fprintf(w, "  id: %s\n", result.ID)
	}
	fmt.Fprintf(w, "  attachment: %s\n", valueOrDash(result.AttachmentFBID))
	fmt.Fprintf(w, "  file: %s (%s, %d bytes)\n", truncate(result.Filename, 80), valueOrDash(result.MimeType), result.SizeBytes)
	fmt.Fprintf(w, "  otid: %s\n", valueOrDash(result.OfflineThreadingID))
	fmt.Fprintf(w, "  timestamp: %s\n", formatMessageTimestamp(result.TimestampMS))
}

func printMarkReadResult(w io.Writer, result *igcore.MarkReadResult) {
	fmt.Fprintln(w, "Marked read")
	fmt.Fprintf(w, "  thread: %s\n", valueOrDash(result.ThreadID))
	fmt.Fprintf(w, "  long thread: %s\n", valueOrDash(result.LongThreadID))
	fmt.Fprintf(w, "  message: %s\n", valueOrDash(result.MessageID))
	fmt.Fprintf(w, "  timestamp: %s\n", formatMessageTimestamp(result.TimestampMS))
	fmt.Fprintf(w, "  validated: %t\n", result.Validated)
}

func printReactionResult(w io.Writer, result *igcore.ReactionResult) {
	action := "added"
	if result.Removed {
		action = "removed"
	}
	fmt.Fprintf(w, "Reaction %s\n", action)
	fmt.Fprintf(w, "  thread: %s\n", valueOrDash(result.ThreadID))
	fmt.Fprintf(w, "  message: %s\n", valueOrDash(result.MessageID))
	fmt.Fprintf(w, "  emoji: %s\n", valueOrDash(result.Emoji))
	if result.TimestampMS != 0 {
		fmt.Fprintf(w, "  timestamp: %s\n", formatMessageTimestamp(result.TimestampMS))
	}
}

type eventPrinter struct {
	mu           sync.Mutex
	out          io.Writer
	threadFilter string
	jsonOutput   bool
}

func (p *eventPrinter) print(event igcore.Event) {
	if p.threadFilter != "" && event.ThreadID != "" && event.ThreadID != p.threadFilter {
		return
	}

	if p.jsonOutput {
		data, err := json.Marshal(event)
		if err != nil {
			return
		}
		p.mu.Lock()
		defer p.mu.Unlock()
		fmt.Fprintln(p.out, string(data))
		return
	}

	line := formatEvent(event)
	if line == "" {
		return
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	fmt.Fprintln(p.out, line)
}

func formatEvent(event igcore.Event) string {
	timestamp := formatMessageTimestamp(eventTimestamp(event))
	thread := ""
	if event.ThreadID != "" {
		thread = "[" + event.ThreadID + "]"
	}

	switch event.Type {
	case "connected":
		return fmt.Sprintf("%s connected (seq_id=%d)", timestamp, event.SeqID)
	case "disconnected":
		return fmt.Sprintf("%s disconnected: %s (failures=%d)", timestamp, truncate(singleLine(event.Error), 200), event.FailureCount)
	case "resnapshot":
		return fmt.Sprintf("%s resnapshot required; reconnecting", timestamp)
	case "auth.error":
		return fmt.Sprintf("%s auth error: %s", timestamp, truncate(singleLine(event.Error), 200))
	case "seq_id":
		return ""
	case "message.new", "message.admin":
		if event.Message == nil {
			return fmt.Sprintf("%s %s %s (no message payload)", timestamp, thread, event.Type)
		}
		return fmt.Sprintf(
			"%s %s %s: %s [%s]",
			timestamp,
			thread,
			senderLabel(*event.Message),
			truncate(singleLine(displayMessageText(*event.Message)), 120),
			event.Message.Kind,
		)
	case "message.edit":
		if event.Message == nil {
			return fmt.Sprintf("%s %s edited message", timestamp, thread)
		}
		return fmt.Sprintf("%s %s edited %s: %s", timestamp, thread, valueOrDash(event.Message.ID), truncate(singleLine(event.Message.Text), 120))
	case "message.delete":
		if event.Message == nil {
			return fmt.Sprintf("%s %s deleted message", timestamp, thread)
		}
		return fmt.Sprintf("%s %s deleted %s", timestamp, thread, valueOrDash(event.Message.ID))
	case "reaction.add", "reaction.remove":
		action := "added"
		if event.Type == "reaction.remove" {
			action = "removed"
		}
		messageID := ""
		if event.Message != nil {
			messageID = event.Message.ID
		}
		return fmt.Sprintf("%s %s reaction %s %s on %s", timestamp, thread, action, valueOrDash(event.Reaction), valueOrDash(messageID))
	case "mark.read", "mark.unread", "read.receipt":
		return fmt.Sprintf("%s %s %s", timestamp, thread, event.Type)
	case "unknown", "delta":
		return fmt.Sprintf("%s %s %s", timestamp, thread, valueOrDash(firstNonEmpty(event.RawType, event.Type)))
	default:
		return fmt.Sprintf("%s %s %s", timestamp, thread, valueOrDash(event.Type))
	}
}

func eventTimestamp(event igcore.Event) int64 {
	if event.TimestampMS != 0 {
		return event.TimestampMS
	}
	if event.Message != nil && event.Message.TimestampMS != 0 {
		return event.Message.TimestampMS
	}
	return time.Now().UnixMilli()
}

func senderLabel(message igcore.MessageView) string {
	if message.IsFromViewer {
		return "me"
	}
	if message.SenderName != "" {
		return truncate(message.SenderName, 24)
	}
	if message.SenderFBID != 0 {
		return fmt.Sprintf("fbid:%d", message.SenderFBID)
	}
	return "unknown"
}

func displayMessageText(message igcore.MessageView) string {
	if message.Text != "" {
		return message.Text
	}
	if message.MediaCount > 0 {
		return fmt.Sprintf("[%s x%d]", message.Kind, message.MediaCount)
	}
	if message.Kind == "unknown" && message.ContentType != "" {
		return "[" + message.ContentType + "]"
	}
	if message.Kind != "" && message.Kind != "text" {
		return "[" + message.Kind + "]"
	}
	return "(empty)"
}

func singleLine(value string) string {
	return strings.NewReplacer("\r\n", " ", "\n", " ", "\r", " ", "\t", " ").Replace(value)
}

func valueOrDash(value string) string {
	if value == "" {
		return "-"
	}
	return value
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

func formatTimestamp(milliseconds int64) string {
	if milliseconds == 0 {
		return "-"
	}
	return time.UnixMilli(milliseconds).Local().Format("2006-01-02 15:04")
}

func formatMessageTimestamp(milliseconds int64) string {
	if milliseconds == 0 {
		return "-"
	}
	return time.UnixMilli(milliseconds).Local().Format("2006-01-02 15:04:05")
}

func truncate(value string, limit int) string {
	runes := []rune(value)
	if len(runes) <= limit {
		return value
	}
	if limit <= 1 {
		return string(runes[:limit])
	}
	return string(runes[:limit-1]) + "…"
}

func threadFlags(thread igcore.ThreadView) string {
	flags := make([]string, 0, 4)
	if thread.MarkedAsUnread {
		flags = append(flags, "unread")
	}
	if thread.IsMuted {
		flags = append(flags, "muted")
	}
	if thread.IsPinned {
		flags = append(flags, "pinned")
	}
	if thread.IsGroup {
		flags = append(flags, "group")
	}
	if len(flags) == 0 {
		return "-"
	}
	return strings.Join(flags, ",")
}
