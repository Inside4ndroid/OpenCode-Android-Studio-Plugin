# OpenCode Server API Notes

> References:
> - Official documentation: https://opencode.ai/docs/server/
> - JavaScript SDK: `sdk/js/src/gen/types.gen.ts`, `sdk/js/src/gen/sdk.gen.ts`

---

## Overview

- The server exposes an OpenAPI 3.1 specification at `/doc` and generates a JavaScript SDK from it.
- Subscribe to SSE events through `/event?directory=` or receive global events through `/global/event`.
- Session APIs are under `/session`. `messageID` is an important correlation key for Diff and session operations.

---

## Sessions API (Core)

### Session List and Status

- `GET /session?directory=`
  - Returns `Session[]`, including `time.updated`, which can be used to find the most recent session.
- `GET /session/status?directory=`
  - Returns `{ [sessionID]: SessionStatus }`, which can be used to determine whether sessions are Busy or Idle.
- `GET /session/:id?directory=`
  - Retrieves details for an individual session.

### Diff

- `GET /session/:id/diff?directory=&messageID?`
  - `messageID` is **optional but important** for identifying the Diff produced by a particular message.
  - Without `messageID`, the endpoint may return historical Diffs for the session, causing stale changes to be reported again.

### Other Important Session Operations

- `POST /session`: Create a session.
- `PATCH /session/:id`: Update the session title.
- `DELETE /session/:id`: Delete a session.
- `POST /session/:id/abort`: Abort a session.
- `POST /session/:id/revert` and `POST /session/:id/unrevert`.
- `POST /session/:id/permissions/:permissionID`.

---

## Messages and Commands API (`messageID` Correlation)

- `POST /session/:id/command`
  - Executes commands such as `/mystatus` and returns a Message containing an `id`.
- `GET /session/:id/message?limit?`
  - Returns `{ info: Message, parts: Part[] }[]`.
- `POST /session/:id/message`
  - Returns `{ info: Message, parts: Part[] }`.

**Message structure (SDK)**:

- `Message.id` is the `messageID`.
- `Message.sessionID` identifies the session.
- `Message.role` is `user` or `assistant`.

---

## SSE Events (SDK Reference)

### Event Subscriptions

- `/event?directory=` (corresponds to SDK `EventSubscribeData`)
- `/global/event` (global SDK event stream)

### Events Used by This Plugin

- `session.status` → `{ sessionID, status }`
- `session.idle` → `{ sessionID }`
- `session.diff` → `{ sessionID, diff: FileDiff[] }`
- `file.edited` → `{ file }`
- `message.updated` → `{ info: Message }` (the Message includes `id`, `sessionID`, and `role`)
- `command.executed` → `{ name, sessionID, arguments, messageID }`

**SDK definition details**:

- `EventMessageUpdated.properties.info` contains the message; `messageID` is not a direct property.
- `EventCommandExecuted.properties.name/arguments/messageID` are the relevant fields; there is no `command` field.

---

## Important Findings and Recommendations

1. **Include `messageID` when fetching a Diff whenever possible.**
   - Without it, historical Diffs may be returned, making previously deleted files appear as new changes.
2. **Parse `info.id` from `message.updated` as the message ID.**
   - The SDK defines the ID inside `info`, not directly on `properties`.
3. **Use `command.executed` as a reliable source of `messageID`.**
   - This is particularly useful for commands such as `/mystatus`.
4. **Use `message.part.updated` as a fallback source.**
   - A Part contains `sessionID` and `messageID`, which can fill gaps when `message.updated` is missing.
5. **Make Busy-state cleanup idempotent.**
   - Clear state such as `turnMessageIds` only when `status.isBusy() && changed` is true, preventing a repeated Busy event from erasing a `file.edited` event that arrived mid-turn.
6. **Use a Diff fetch priority order.**
   - 1. API result associated with `messageID` (most accurate); 2. SSE `session.diff` payload (fallback); 3. Session summary (last resort).

---

## API Endpoints Currently Used by This Plugin

- `/event?directory=`: SSE event subscription.
- `/global/health`: Health check.
- `/session/status`: Find a Busy session.
- `/session`: Retrieve the most recent session.
- `/session/:id`: Retrieve a session summary for Diff fallback.
- `/session/:id/diff?messageID?`: Retrieve Diffs.
- `/tui/append-prompt`: Append a command to the TUI.
