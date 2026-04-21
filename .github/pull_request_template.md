# Pull Request

## Jira Ticket

- [BH-201](https://tranngochuyen.atlassian.net/browse/BH-201): Feature 13: Message search
- [BH-202](https://tranngochuyen.atlassian.net/browse/BH-202): Setup Elasticsearch for Message Search
- [BH-203](https://tranngochuyen.atlassian.net/browse/BH-203): Implement Message Index Sync
- [BH-204](https://tranngochuyen.atlassian.net/browse/BH-204): Develop Message Search API
- [BH-205](https://tranngochuyen.atlassian.net/browse/BH-205): Implement Visibility & Security Rules
- [BH-206](https://tranngochuyen.atlassian.net/browse/BH-206): Implement jump-to-message functionality from search results

---

## Description

Implementation of the comprehensive Message Search and Jump-to-Message navigation feature. This PR introduces Elasticsearch integration for the `search-service`, coordinate calculation logic in `message-service`, and a complex frontend workflow to support seamless navigation from search results to actual chat content.

---

## Type of change

- [x] New feature (adds new functionality)
- [x] Performance Task (Elasticsearch indexing)
- [x] Refactor (UI/UX Improvement)
- [ ] Config / Infrastructure change

---

## Technical Details & Architecture

### 1. Event-Driven Indexing Model
Ensuring search data is always synchronized with the `message-service`:
- **Producer**: `message-service` publishes events when new messages (MESSAGES/FILES) are created or updated.
- **Consumer**: `search-service` consumes events and updates the **Elasticsearch Index (`MessageIndex`)**.
- **Accent Normalization**: During indexing, text is normalized to `NFD` form to support flexible Vietnamese searching.

### 2. Message Search Logic (`search-service`)
Leveraging **Elasticsearch Native Query** for complex retrieval:
- **Query Type**: `matchPhrasePrefix` combined with `bool` filters (security & visibility context).
- **Highlighting**: 
  - Utilizes ES `HighlightQuery` to retrieve snippets containing keywords.
  - Applies **Accent-insensitive Post-processing** to ensure accurate highlighting for Vietnamese characters (e.g., searching "om" matches "Òm").

### 3. Jump to Message API (`message-service`)
Added target message coordinate endpoint: `GET /conversations/{convId}/messages/{msgId}/context`

- **Calculation Logic**:
  - Uses `MongoTemplate` to count `newerCount` (number of messages in the same conversation with `createdAt > target.createdAt`).
  - Applies identical visibility rules as the main message retrieval function (JoinedAt, DeletedBy, System filters, etc.).
- **Pagination Strategy**:
  - `pageIndex = (newerCount / pageSize)` (0-based indexing).
  - Returns: `page`, `size`, `totalElements`.

### 4. Frontend Navigation Workflow
Implemented complex navigation logic in `ChatWindow.tsx`:
1. **Trigger**: User clicks a search result card.
2. **Phase 1 (Heuristic Search)**: Check if the message is already rendered in the DOM. If yes -> Immediate scroll.
3. **Phase 2 (Remote Coordinate)**: If not found, call API to retrieve the `pageIndex`.
4. **Phase 3 (Infinite Fetching)**: Iteratively trigger `fetchNextPage()` until the dataset reaches the target page.
5. **Phase 4 (DOM Targeting)**: Once the target message is mapped to the UI, the `useEffect` hook detects the element and performs:
   - `scrollIntoView({ behavior: 'smooth', block: 'center' })`.
   - Injects `highlight-message` CSS class (background flash effect).
   - Propagates `highlightKeyword` to `MessageBubble` to yellow-highlight the keyword in text.

### 5. UI/UX Improvements
- **Smart Highlighting**: The logic in `MessageBubble.tsx` is designed to match keywords regardless of accents (Vietnamese NFD support).
- **Persistence**: Highlight is maintained for 4 seconds to give the user enough time to identify the content after scrolling.
- **Visual Stability**: Uses `suppressFetchRef` to prevent scroll jumping while React Query is buffering new data.

---

## Business Rules & Validation

| Rule                                    | Implementation                                                  |
| --------------------------------------- | --------------------------------------------------------------- |
| Search restricted to post-join messages | Filter `createdAt >= joinedAt`                                  |
| Exclude deleted messages                | Filter `deletedBy != currentUserId`                             |
| Consistent Highlighting                 | Global use of `<mark>` tag with Zalo yellow theme (`#ffd700`)   |
| Jump Limits                             | PageIndex calculated based on dynamic `pageSize` (default 20)    |

---

## How Has This Been Tested? (Manual)

| Test Case                               | Expected Result                                                 |
| --------------------------------------- | --------------------------------------------------------------- |
| Search unaccented keyword "alo"         | Returns results containing "Alo", "à lô", "hà lội" (highlighted)|
| Click result at the top (old page)      | FE auto-fetches continuously, scrolls to message, flashes yellow|
| Click result already in view            | Smooth scroll to target message immediately                     |
| Toggle search sidebar                   | Highlight state and Jump coordinates are reset properly         |

---

## Risk Level

- [x] Medium – impacts message loading mechanism and Elasticsearch performance.

**Rationale:** Sequential data fetching logic might cause delays if jumping to a very distant page (e.g., page 100). Optimized by increasing fetch speed via `hasNextPage` checks.
