---
name: graphify
description: "Navigates and queries the PocketCraft 6,700+ node knowledge graph at graphify-out/graph.json to save up to 85% of LLM context tokens. Explains classes, traces paths between components, and updates the graph after edits."
---

# /graphify - Knowledge Graph Query & Token-Saving Skill

PocketCraft has a comprehensive, pre-extracted knowledge graph with **6,700+ nodes** located at `graphify-out/graph.json`. This skill teaches Claude how to use `graphify` to navigate the codebase efficiently while consuming minimal context tokens.

---

## 1. Golden Rule: Graphify Before Grep / Read

> [!IMPORTANT]
> **Never open massive source files like `ServerStateHolder.kt` (5,600+ lines) or `ServerHostService.kt` (3,000+ lines) to search for logic.**
> Instead, ask `graphify` to return a scoped subgraph of only the relevant nodes, method calls, and imports. This takes ~1,500–2,000 tokens instead of 25,000+ tokens.

---

## 2. Querying Workflows

### A. General Architecture & Logic Questions
When asked how a feature works (e.g. "How does PocketCraft handle Bedrock ping?" or "Where is world importing handled?"):
```bash
graphify query "How does Bedrock ping work?"
```
*Output*: Scoped subgraph showing callers, callees, and file locations with line numbers.

### B. Finding Relationships Between Two Classes
When investigating how two components interact (e.g., `ServerStateHolder` and `ServerHostService`):
```bash
graphify path "ServerStateHolder" "ServerHostService" --undirected
```
*Output*: Shortest hops through intermediate managers, models, or IPC receivers.

### C. Explaining a Specific Class, Function, or God Node
To inspect all incoming and outgoing connections for a specific symbol:
```bash
graphify explain "RelayManager"
graphify explain "ServerHostService"
```
*Output*: Degree, source file, line number, incoming imports, and outbound calls grouped by file.

### D. Finding Central Architectural Hubs
To discover which components have the highest coupling in the codebase:
```bash
graphify god-nodes --top 15
```

---

## 3. Keeping the Graph Updated

After modifying or adding Kotlin, Java, C, TypeScript, or Markdown files in the project:
```bash
graphify update .
```
- **AST-only**: Runs in seconds using local tree-sitter / AST parsing.
- **Zero API cost**: Consumes 0 LLM tokens.
- Keeps `graph.json` perfectly synchronized with your code changes.

---

## 4. Token Reduction Benchmark
- Traditional full-file review: ~35,000 tokens across 5 files.
- Graphify query traversal: ~1,800 tokens for the exact 20-40 node sub-network.
- Net savings: **~94% context window headroom preserved**.
