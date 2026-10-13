import type { ReactNode } from "react";
import { Link } from "react-router-dom";

// Blog posts are written in a small subset of Markdown: "##" and "###" headings,
// "---" rules, bullet and numbered lists, **bold**, `code` and [links](url).

const INLINE_TOKEN = /(\*\*.+?\*\*|`[^`]+`|\[[^\]]+\]\([^)\s]+\))/g;
const ORDERED_ITEM = /^\d+\.\s+/;

function renderInline(text: string, keyPrefix = "i"): ReactNode[] {
  return text.split(INLINE_TOKEN).map((part, index) => {
    const key = `${keyPrefix}-${index}`;

    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return (
        <strong key={key} className="font-bold">
          {renderInline(part.slice(2, -2), key)}
        </strong>
      );
    }

    if (part.startsWith("`") && part.endsWith("`") && part.length > 2) {
      return (
        <code key={key} className="font-mono text-[0.9em] px-1 py-0.5 rounded bg-[#7FE620]/10">
          {part.slice(1, -1)}
        </code>
      );
    }

    const link = part.match(/^\[([^\]]+)\]\(([^)\s]+)\)$/);
    if (link) {
      const [, label, href] = link;
      const className = "text-[#7FE620] font-bold underline underline-offset-2 hover:opacity-80";
      return href.startsWith("/") ? (
        <Link key={key} to={href} className={className}>
          {label}
        </Link>
      ) : (
        <a key={key} href={href} target="_blank" rel="noopener noreferrer" className={className}>
          {label}
        </a>
      );
    }

    return part;
  });
}

type Block =
  | { kind: "h2" | "h3" | "p"; text: string }
  | { kind: "ul" | "ol"; items: string[] }
  | { kind: "hr" };

function parseBlocks(markdown: string): Block[] {
  const blocks: Block[] = [];
  let paragraph: string[] = [];

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push({ kind: "p", text: paragraph.join(" ") });
      paragraph = [];
    }
  };

  for (const rawLine of markdown.split("\n")) {
    const line = rawLine.trim();
    const last = blocks[blocks.length - 1];

    if (!line) {
      flushParagraph();
    } else if (line === "---") {
      flushParagraph();
      blocks.push({ kind: "hr" });
    } else if (line.startsWith("### ")) {
      flushParagraph();
      blocks.push({ kind: "h3", text: line.slice(4) });
    } else if (line.startsWith("## ")) {
      flushParagraph();
      blocks.push({ kind: "h2", text: line.slice(3) });
    } else if (line.startsWith("- ")) {
      const hadParagraph = paragraph.length > 0;
      flushParagraph();
      if (!hadParagraph && last?.kind === "ul") {
        last.items.push(line.slice(2));
      } else {
        blocks.push({ kind: "ul", items: [line.slice(2)] });
      }
    } else if (ORDERED_ITEM.test(line)) {
      const hadParagraph = paragraph.length > 0;
      flushParagraph();
      const item = line.replace(ORDERED_ITEM, "");
      if (!hadParagraph && last?.kind === "ol") {
        last.items.push(item);
      } else {
        blocks.push({ kind: "ol", items: [item] });
      }
    } else {
      paragraph.push(line);
    }
  }
  flushParagraph();

  return blocks;
}

export function renderMarkdown(markdown: string, theme: "light" | "dark"): ReactNode[] {
  const heading = theme === "dark" ? "text-white" : "text-black";
  const body = theme === "dark" ? "text-white/65 font-medium" : "text-black/70 font-medium";

  return parseBlocks(markdown).map((block, index) => {
    switch (block.kind) {
      case "h2":
        return (
          <h2 key={index} className={`text-3xl font-bold mt-8 mb-4 ${heading}`}>
            {renderInline(block.text)}
          </h2>
        );
      case "h3":
        return (
          <h3 key={index} className={`text-xl font-bold mt-6 mb-2 ${heading}`}>
            {renderInline(block.text)}
          </h3>
        );
      case "hr":
        return <hr key={index} className={theme === "dark" ? "border-white/10" : "border-black/10"} />;
      case "ul":
        return (
          <ul key={index} className="space-y-2 ml-6">
            {block.items.map((item, i) => (
              <li key={i} className={`list-disc ${body}`}>
                {renderInline(item)}
              </li>
            ))}
          </ul>
        );
      case "ol":
        return (
          <ol key={index} className="space-y-2 ml-6 list-decimal">
            {block.items.map((item, i) => (
              <li key={i} className={body}>
                {renderInline(item)}
              </li>
            ))}
          </ol>
        );
      default:
        return (
          <p key={index} className={`${body} leading-relaxed`}>
            {renderInline(block.text)}
          </p>
        );
    }
  });
}
